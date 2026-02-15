/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.smarty;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.provider.VerificationProvider;
import com.gaestalt.address.provider.VerificationRequest;
import com.gaestalt.address.provider.VerificationResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class SmartyValidationProvider implements VerificationProvider {

    private static final Map<Integer, String> ALPHA3 = Map.of(
            CountryCode.CA, "CAN",
            CountryCode.GB, "GBR"
    );

    private final SmartyConfig smartyConfig;
    private final RestClient.Builder restClientBuilder;
    private final Tracer tracer;

    @Override
    public String getProviderId() {
        return "smarty";
    }

    @Override
    public String getDisplayName() {
        return "Smarty Address Verification API";
    }

    @Override
    public List<Integer> getSupportedCountries() {
        return List.of(CountryCode.US, CountryCode.CA, CountryCode.GB);
    }

    @Override
    public boolean isEnabled() {
        return smartyConfig.getAuthId() != null && !smartyConfig.getAuthId().isEmpty();
    }

    @Override
    public VerificationResult verify(final VerificationRequest request) {
        try {
            if (request.getCountryCode() == CountryCode.US) {
                return verifyUsStreet(request);
            } else {
                return verifyInternational(request);
            }
        } catch (Exception e) {
            log.error("Smarty verification failed: {}", e.getMessage(), e);
            return VerificationResult.builder()
                    .status(VerificationResult.Status.PROVIDER_ERROR)
                    .message("Smarty API error: " + e.getMessage())
                    .metadata(Map.of())
                    .build();
        }
    }

    private VerificationResult verifyUsStreet(final VerificationRequest request) {
        final var lines = request.getAddressLines() != null ? request.getAddressLines() : List.<String>of();
        final var uri = UriComponentsBuilder.fromUriString(smartyConfig.getUsStreetBaseUrl())
                .path("/street-address")
                .queryParam("street", !lines.isEmpty() ? lines.get(0) : "")
                .queryParam("city", request.getLocality())
                .queryParam("state", request.getAdministrativeArea())
                .queryParam("zipcode", request.getPostalCode())
                .queryParam("auth-id", smartyConfig.getAuthId())
                .queryParam("auth-token", smartyConfig.getAuthToken())
                .queryParam("candidates", 1)
                .build()
                .toUriString();

        final var span = tracer.spanBuilder("smarty.us-street.verify")
                .setAttribute(AttributeKey.stringKey("http.method"), "GET")
                .setAttribute(AttributeKey.stringKey("http.url"), smartyConfig.getUsStreetBaseUrl())
                .startSpan();
        try (final var scope = span.makeCurrent()) {
            final var restClient = restClientBuilder.build();
            final var responses = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(SmartyUsStreetResponse[].class);

            span.setStatus(StatusCode.OK);

            if (responses == null || responses.length == 0) {
                return VerificationResult.builder()
                        .status(VerificationResult.Status.INVALID)
                        .message("Smarty returned no address data")
                        .metadata(Map.of())
                        .build();
            }

            return mapUsStreetResponse(request, responses[0]);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private VerificationResult mapUsStreetResponse(final VerificationRequest originalRequest,
                                                  final SmartyUsStreetResponse response) {
        final var standardizedLines = new ArrayList<String>();
        if (response.getDeliveryLine1() != null && !response.getDeliveryLine1().isEmpty()) {
            standardizedLines.add(response.getDeliveryLine1());
        }
        if (response.getDeliveryLine2() != null && !response.getDeliveryLine2().isEmpty()) {
            standardizedLines.add(response.getDeliveryLine2());
        }

        final var components = response.getComponents();
        final var locality = components != null ? components.getCityName() : null;
        final var adminArea = components != null ? components.getStateAbbreviation() : null;
        final var postalCode = joinPostalCode(
                components != null ? components.getZipcode() : null,
                components != null ? components.getPlus4Code() : null);

        final var hasCorrections = !addressLinesMatch(originalRequest.getAddressLines(), standardizedLines)
                || !equalsIgnoreCase(originalRequest.getLocality(), locality)
                || !equalsIgnoreCase(originalRequest.getAdministrativeArea(), adminArea)
                || !equalsIgnoreCase(originalRequest.getPostalCode(), postalCode);

        Double latitude = null;
        Double longitude = null;
        final var metadata = new HashMap<String, String>();
        if (response.getMetadata() != null) {
            if (response.getMetadata().getCountyName() != null) {
                metadata.put("countyName", response.getMetadata().getCountyName());
            }
            if (response.getMetadata().getLatitude() != null) {
                latitude = Double.parseDouble(response.getMetadata().getLatitude());
            }
            if (response.getMetadata().getLongitude() != null) {
                longitude = Double.parseDouble(response.getMetadata().getLongitude());
            }
            if (response.getMetadata().getRecordType() != null) {
                metadata.put("recordType", response.getMetadata().getRecordType());
            }
            if (response.getMetadata().getRdi() != null) {
                metadata.put("rdi", response.getMetadata().getRdi());
            }
        }
        if (response.getAnalysis() != null) {
            if (response.getAnalysis().getDpvMatchCode() != null) {
                metadata.put("dpvMatchCode", response.getAnalysis().getDpvMatchCode());
            }
            if (response.getAnalysis().getDpvFootnotes() != null) {
                metadata.put("dpvFootnotes", response.getAnalysis().getDpvFootnotes());
            }
            if (response.getAnalysis().getActive() != null) {
                metadata.put("active", response.getAnalysis().getActive());
            }
        }

        return VerificationResult.builder()
                .status(hasCorrections ? VerificationResult.Status.VERIFIED_WITH_CORRECTIONS
                        : VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(standardizedLines)
                .locality(locality)
                .administrativeArea(adminArea)
                .postalCode(postalCode)
                .latitude(latitude)
                .longitude(longitude)
                .metadata(metadata)
                .build();
    }

    private VerificationResult verifyInternational(final VerificationRequest request) {
        final var alpha3 = ALPHA3.get(request.getCountryCode());
        final var lines = request.getAddressLines() != null ? request.getAddressLines() : List.<String>of();
        final var uri = UriComponentsBuilder.fromUriString(smartyConfig.getInternationalBaseUrl())
                .path("/verify")
                .queryParam("country", alpha3)
                .queryParam("address1", !lines.isEmpty() ? lines.get(0) : "")
                .queryParam("locality", request.getLocality())
                .queryParam("administrative_area", request.getAdministrativeArea())
                .queryParam("postal_code", request.getPostalCode())
                .queryParam("auth-id", smartyConfig.getAuthId())
                .queryParam("auth-token", smartyConfig.getAuthToken())
                .build()
                .toUriString();

        final var span = tracer.spanBuilder("smarty.international.verify")
                .setAttribute(AttributeKey.stringKey("http.method"), "GET")
                .setAttribute(AttributeKey.stringKey("http.url"), smartyConfig.getInternationalBaseUrl())
                .startSpan();
        try (final var scope = span.makeCurrent()) {
            final var restClient = restClientBuilder.build();
            final var responses = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(SmartyInternationalResponse[].class);

            span.setStatus(StatusCode.OK);

            if (responses == null || responses.length == 0) {
                return VerificationResult.builder()
                        .status(VerificationResult.Status.INVALID)
                        .message("Smarty returned no address data")
                        .metadata(Map.of())
                        .build();
            }

            return mapInternationalResponse(request, responses[0]);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private VerificationResult mapInternationalResponse(final VerificationRequest originalRequest,
                                                       final SmartyInternationalResponse response) {
        final var standardizedLines = new ArrayList<String>();
        if (response.getAddress1() != null && !response.getAddress1().isEmpty()) {
            standardizedLines.add(response.getAddress1());
        }
        if (response.getAddress2() != null && !response.getAddress2().isEmpty()) {
            standardizedLines.add(response.getAddress2());
        }

        final var components = response.getComponents();
        final var locality = components != null ? components.getLocality() : null;
        final var adminArea = components != null ? components.getAdministrativeArea() : null;
        final var postalCode = components != null ? components.getPostalCode() : null;

        final var hasCorrections = !addressLinesMatch(originalRequest.getAddressLines(), standardizedLines)
                || !equalsIgnoreCase(originalRequest.getLocality(), locality)
                || !equalsIgnoreCase(originalRequest.getAdministrativeArea(), adminArea)
                || !equalsIgnoreCase(originalRequest.getPostalCode(), postalCode);

        Double latitude = null;
        Double longitude = null;
        final var responseMetadata = response.getMetadata();
        if (responseMetadata != null) {
            if (responseMetadata.getLatitude() != null) {
                latitude = Double.parseDouble(responseMetadata.getLatitude());
            }
            if (responseMetadata.getLongitude() != null) {
                longitude = Double.parseDouble(responseMetadata.getLongitude());
            }
        }

        return VerificationResult.builder()
                .status(hasCorrections ? VerificationResult.Status.VERIFIED_WITH_CORRECTIONS
                        : VerificationResult.Status.VERIFIED)
                .countryCode(originalRequest.getCountryCode())
                .addressLines(standardizedLines)
                .locality(locality)
                .administrativeArea(adminArea)
                .postalCode(postalCode)
                .latitude(latitude)
                .longitude(longitude)
                .metadata(Map.of())
                .build();
    }

    private String joinPostalCode(final String zipCode, final String plus4Code) {
        if (zipCode == null) return null;
        if (plus4Code != null && !plus4Code.isEmpty()) {
            return zipCode + "-" + plus4Code;
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
