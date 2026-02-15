/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.usps;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.provider.VerificationProvider;
import com.gaestalt.address.provider.VerificationRequest;
import com.gaestalt.address.provider.VerificationResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UspsValidationProvider implements VerificationProvider {

    private static final String ADDRESS_PATH = "/addresses/v3/address";

    private final UspsConfig uspsConfig;
    private final UspsOAuthService oAuthService;
    private final RestClient.Builder restClientBuilder;
    private final Tracer tracer;

    @Override
    public String getProviderId() {
        return "usps";
    }

    @Override
    public String getDisplayName() {
        return "USPS Address Verification API";
    }

    @Override
    public List<Integer> getSupportedCountries() {
        return List.of(CountryCode.US);
    }

    @Override
    public boolean isEnabled() {
        return uspsConfig.getClientId() != null && !uspsConfig.getClientId().isEmpty();
    }

    @Override
    public VerificationResult verify(final VerificationRequest request) {
        final var uspsRequest = mapToUspsRequest(request);

        try {
            final var uspsResponse = callUspsApi(uspsRequest);
            return mapFromUspsResponse(request, uspsResponse);
        } catch (Exception e) {
            log.error("USPS verification failed: {}", e.getMessage(), e);
            return VerificationResult.builder()
                    .status(VerificationResult.Status.PROVIDER_ERROR)
                    .message("USPS API error: " + e.getMessage())
                    .metadata(Map.of())
                    .build();
        }
    }

    private UspsAddressRequest mapToUspsRequest(final VerificationRequest request) {
        final var lines = request.getAddressLines() != null ? request.getAddressLines() : List.<String>of();
        return UspsAddressRequest.builder()
                .streetAddress(!lines.isEmpty() ? lines.get(0) : null)
                .secondaryAddress(lines.size() > 1 ? lines.get(1) : null)
                .city(request.getLocality())
                .state(request.getAdministrativeArea())
                .zipCode(request.getPostalCode())
                .build();
    }

    private VerificationResult mapFromUspsResponse(final VerificationRequest originalRequest,
                                                  final UspsAddressResponse uspsResponse) {
        if (uspsResponse == null || uspsResponse.getAddress() == null) {
            return VerificationResult.builder()
                    .status(VerificationResult.Status.INVALID)
                    .message("USPS returned no address data")
                    .metadata(Map.of())
                    .build();
        }

        final var addr = uspsResponse.getAddress();

        final var standardizedLines = new ArrayList<String>();
        if (addr.getStreetAddress() != null && !addr.getStreetAddress().isEmpty()) {
            standardizedLines.add(addr.getStreetAddress());
        }
        if (addr.getSecondaryAddress() != null && !addr.getSecondaryAddress().isEmpty()) {
            standardizedLines.add(addr.getSecondaryAddress());
        }

        final var postalCode = joinPostalCode(addr.getZipCode(), addr.getZipPlus4());

        final var hasCorrections = !addressLinesMatch(originalRequest.getAddressLines(), standardizedLines)
                || !equalsIgnoreCase(originalRequest.getLocality(), addr.getCity())
                || !equalsIgnoreCase(originalRequest.getAdministrativeArea(), addr.getState())
                || !equalsIgnoreCase(originalRequest.getPostalCode(), postalCode);

        final var metadata = new HashMap<String, String>();
        if (uspsResponse.getDeliveryPoint() != null) {
            metadata.put("deliveryPoint", uspsResponse.getDeliveryPoint());
        }
        if (uspsResponse.getCarrierRoute() != null) {
            metadata.put("carrierRoute", uspsResponse.getCarrierRoute());
        }
        if (uspsResponse.getDPVConfirmation() != null) {
            metadata.put("dpvConfirmation", uspsResponse.getDPVConfirmation());
        }
        if (uspsResponse.getDPVCMRA() != null) {
            metadata.put("dpvCmra", uspsResponse.getDPVCMRA());
        }
        if (uspsResponse.getBusiness() != null) {
            metadata.put("business", uspsResponse.getBusiness());
        }
        if (uspsResponse.getVacant() != null) {
            metadata.put("vacant", uspsResponse.getVacant());
        }

        return VerificationResult.builder()
                .status(hasCorrections ? VerificationResult.Status.VERIFIED_WITH_CORRECTIONS
                        : VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(standardizedLines)
                .locality(addr.getCity())
                .administrativeArea(addr.getState())
                .postalCode(postalCode)
                .metadata(metadata)
                .build();
    }

    private UspsAddressResponse callUspsApi(final UspsAddressRequest request) {
        final var uri = buildAddressUri(request);
        final var span = tracer.spanBuilder("usps.address.verify")
                .setAttribute(AttributeKey.stringKey("http.method"), "GET")
                .setAttribute(AttributeKey.stringKey("http.url"), uri)
                .startSpan();
        try (final var scope = span.makeCurrent()) {
            var token = oAuthService.getAccessToken();

            final var restClient = restClientBuilder
                    .baseUrl(uspsConfig.getBaseUrl())
                    .build();

            try {
                final var response = restClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            if (res.getStatusCode().value() == 401) {
                                log.warn("USPS OAuth token expired, invalidating cache");
                                oAuthService.invalidateToken();
                            }
                            throw new RuntimeException("USPS API error: " + res.getStatusCode());
                        })
                        .body(UspsAddressResponse.class);
                span.setStatus(StatusCode.OK);
                return response;
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    log.info("Retrying with new token");
                    token = oAuthService.getAccessToken();
                    final var response = restClient.get()
                            .uri(uri)
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .body(UspsAddressResponse.class);
                    span.setStatus(StatusCode.OK);
                    return response;
                }
                throw e;
            }
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private String buildAddressUri(final UspsAddressRequest request) {
        final var builder = UriComponentsBuilder.fromPath(ADDRESS_PATH);

        if (request.getStreetAddress() != null) {
            builder.queryParam("streetAddress", request.getStreetAddress());
        }
        if (request.getSecondaryAddress() != null) {
            builder.queryParam("secondaryAddress", request.getSecondaryAddress());
        }
        if (request.getCity() != null) {
            builder.queryParam("city", request.getCity());
        }
        if (request.getState() != null) {
            builder.queryParam("state", request.getState());
        }
        if (request.getZipCode() != null) {
            builder.queryParam("ZIPCode", request.getZipCode());
        }
        if (request.getZipPlus4() != null) {
            builder.queryParam("ZIPPlus4", request.getZipPlus4());
        }

        return builder.build().toUriString();
    }

    private String joinPostalCode(final String zipCode, final String zipPlus4) {
        if (zipCode == null) return null;
        if (zipPlus4 != null && !zipPlus4.isEmpty()) {
            return zipCode + "-" + zipPlus4;
        }
        return zipCode;
    }

    private boolean equalsIgnoreCase(final String a, final String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private boolean addressLinesMatch(final List<String> original, final List<String> standardized) {
        if (original == null && standardized == null) return true;
        if (original == null || standardized == null) return false;
        if (original.size() != standardized.size()) return false;
        for (var i = 0; i < original.size(); i++) {
            if (!equalsIgnoreCase(original.get(i), standardized.get(i))) return false;
        }
        return true;
    }
}
