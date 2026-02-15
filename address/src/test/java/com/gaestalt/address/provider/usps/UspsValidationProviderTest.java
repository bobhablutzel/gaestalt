/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.usps;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.provider.VerificationRequest;
import com.gaestalt.address.provider.VerificationResult;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UspsValidationProviderTest {

    private UspsConfig uspsConfig;
    private UspsOAuthService oAuthService;
    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private Tracer tracer;
    private UspsValidationProvider provider;

    // Mock chain objects for GET
    private RestClient.RequestHeadersUriSpec<?> getSpec;
    private RestClient.RequestHeadersSpec<?> uriGetSpec;
    private RestClient.RequestHeadersSpec<?> headerSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uspsConfig = new UspsConfig();
        uspsConfig.setClientId("test-client-id");
        uspsConfig.setClientSecret("test-client-secret");
        uspsConfig.setBaseUrl("https://apis.usps.com");

        oAuthService = mock(UspsOAuthService.class);
        when(oAuthService.getAccessToken()).thenReturn("test-token");

        tracer = OpenTelemetry.noop().getTracer("test");

        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        uriGetSpec = mock(RestClient.RequestHeadersSpec.class);
        headerSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        doReturn(getSpec).when(restClient).get();
        doReturn(uriGetSpec).when(getSpec).uri(anyString());
        doReturn(headerSpec).when(uriGetSpec).header(anyString(), anyString());
        when(headerSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        provider = new UspsValidationProvider(uspsConfig, oAuthService, restClientBuilder, tracer);
    }

    // --- Identity ---

    @Test
    void providerIdIsUsps() {
        assertEquals("usps", provider.getProviderId());
    }

    @Test
    void displayNameIsNonEmpty() {
        assertFalse(provider.getDisplayName().isEmpty());
    }

    @Test
    void supportedCountriesContainsOnlyUs() {
        assertEquals(List.of(CountryCode.US), provider.getSupportedCountries());
    }

    @Test
    void isEnabledWhenClientIdPresent() {
        assertTrue(provider.isEnabled());
    }

    @Test
    void isDisabledWhenClientIdNull() {
        uspsConfig.setClientId(null);
        assertFalse(provider.isEnabled());
    }

    @Test
    void isDisabledWhenClientIdEmpty() {
        uspsConfig.setClientId("");
        assertFalse(provider.isEnabled());
    }

    // --- Response mapping ---

    @Test
    void identicalResponseReturnsValidated() {
        final var request = buildRequest("123 Main St", "Springfield", "IL", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 Main St");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setZipCode("62704");
        uspsResponse.setAddress(addr);

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertEquals(List.of("123 Main St"), result.getAddressLines());
        assertEquals("Springfield", result.getLocality());
        assertEquals("IL", result.getAdministrativeArea());
        assertEquals("62704", result.getPostalCode());
    }

    @Test
    void differingResponseReturnsValidatedWithCorrections() {
        final var request = buildRequest("123 main st", "springfield", "il", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 MAIN ST");
        addr.setCity("SPRINGFIELD");
        addr.setState("IL");
        addr.setZipCode("62704");
        addr.setZipPlus4("1234");
        uspsResponse.setAddress(addr);

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED_WITH_CORRECTIONS, result.getStatus());
        assertEquals("62704-1234", result.getPostalCode());
    }

    @Test
    void nullResponseReturnsInvalid() {
        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(null);

        final var result = provider.verify(buildRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    void nullAddressInResponseReturnsInvalid() {
        final var uspsResponse = new UspsAddressResponse();
        uspsResponse.setAddress(null);

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(buildRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    void exceptionReturnsProviderError() {
        when(headerSpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

        final var result = provider.verify(buildRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    // --- Metadata ---

    @Test
    void metadataPopulatedFromResponseFields() {
        final var request = buildRequest("123 Main St", "Springfield", "IL", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 Main St");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setZipCode("62704");
        uspsResponse.setAddress(addr);
        uspsResponse.setDeliveryPoint("99");
        uspsResponse.setCarrierRoute("C001");
        uspsResponse.setDPVConfirmation("Y");
        uspsResponse.setDPVCMRA("N");
        uspsResponse.setBusiness("N");
        uspsResponse.setVacant("N");

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertEquals("99", result.getMetadata().get("deliveryPoint"));
        assertEquals("C001", result.getMetadata().get("carrierRoute"));
        assertEquals("Y", result.getMetadata().get("dpvConfirmation"));
        assertEquals("N", result.getMetadata().get("dpvCmra"));
        assertEquals("N", result.getMetadata().get("business"));
        assertEquals("N", result.getMetadata().get("vacant"));
    }

    @Test
    void latitudeLongitudeNotProvided() {
        final var request = buildRequest("123 Main St", "Springfield", "IL", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 Main St");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setZipCode("62704");
        uspsResponse.setAddress(addr);

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertNull(result.getLatitude());
        assertNull(result.getLongitude());
    }

    @Test
    void nullMetadataFieldsOmittedFromMap() {
        final var request = buildRequest("123 Main St", "Springfield", "IL", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 Main St");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setZipCode("62704");
        uspsResponse.setAddress(addr);
        // All metadata fields left null

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void secondaryAddressIncludedInStandardizedLines() {
        final var request = buildRequest("123 Main St", "Springfield", "IL", "62704");

        final var uspsResponse = new UspsAddressResponse();
        final var addr = new UspsAddressResponse.Address();
        addr.setStreetAddress("123 MAIN ST");
        addr.setSecondaryAddress("APT 4");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setZipCode("62704");
        uspsResponse.setAddress(addr);

        when(responseSpec.body(UspsAddressResponse.class)).thenReturn(uspsResponse);

        final var result = provider.verify(request);

        assertEquals(List.of("123 MAIN ST", "APT 4"), result.getAddressLines());
    }

    // --- Helper ---

    private VerificationRequest buildRequest(final String street, final String city,
                                           final String state, final String zip) {
        return VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of(street))
                .locality(city)
                .administrativeArea(state)
                .postalCode(zip)
                .build();
    }
}
