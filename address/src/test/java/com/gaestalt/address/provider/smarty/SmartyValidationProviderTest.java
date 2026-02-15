/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.smarty;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.provider.VerificationRequest;
import com.gaestalt.address.provider.VerificationResult;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SmartyValidationProviderTest {

    private SmartyConfig smartyConfig;
    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private Tracer tracer;
    private SmartyValidationProvider provider;

    private RestClient.RequestHeadersUriSpec<?> getSpec;
    private RestClient.RequestHeadersSpec<?> uriGetSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        smartyConfig = new SmartyConfig();
        smartyConfig.setAuthId("test-auth-id");
        smartyConfig.setAuthToken("test-auth-token");
        smartyConfig.setUsStreetBaseUrl("https://us-street.api.smarty.com");
        smartyConfig.setInternationalBaseUrl("https://international-street.api.smarty.com");

        tracer = OpenTelemetry.noop().getTracer("test");

        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        uriGetSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.build()).thenReturn(restClient);
        doReturn(getSpec).when(restClient).get();
        doReturn(uriGetSpec).when(getSpec).uri(anyString());
        when(uriGetSpec.retrieve()).thenReturn(responseSpec);

        provider = new SmartyValidationProvider(smartyConfig, restClientBuilder, tracer);
    }

    // --- Identity ---

    @Test
    void providerIdIsSmarty() {
        assertEquals("smarty", provider.getProviderId());
    }

    @Test
    void displayNameIsNonEmpty() {
        assertFalse(provider.getDisplayName().isEmpty());
    }

    @Test
    void supportedCountriesContainsUsCanadaAndGb() {
        assertEquals(List.of(CountryCode.US, CountryCode.CA, CountryCode.GB),
                provider.getSupportedCountries());
    }

    @Test
    void isEnabledWhenAuthIdPresent() {
        assertTrue(provider.isEnabled());
    }

    @Test
    void isDisabledWhenAuthIdNull() {
        smartyConfig.setAuthId(null);
        assertFalse(provider.isEnabled());
    }

    @Test
    void isDisabledWhenAuthIdEmpty() {
        smartyConfig.setAuthId("");
        assertFalse(provider.isEnabled());
    }

    // --- US Street path ---

    @Test
    void usIdenticalResponseReturnsValidated() {
        final var request = buildUsRequest("123 Main St", "Springfield", "IL", "62704");

        final var response = new SmartyUsStreetResponse();
        response.setDeliveryLine1("123 Main St");
        final var components = new SmartyUsStreetResponse.Components();
        components.setCityName("Springfield");
        components.setStateAbbreviation("IL");
        components.setZipcode("62704");
        response.setComponents(components);

        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(new SmartyUsStreetResponse[]{response});

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertEquals(List.of("123 Main St"), result.getAddressLines());
        assertEquals("Springfield", result.getLocality());
        assertEquals("IL", result.getAdministrativeArea());
        assertEquals("62704", result.getPostalCode());
    }

    @Test
    void usResponseWithCorrectionsReturnsValidatedWithCorrections() {
        final var request = buildUsRequest("123 main st", "springfield", "il", "62704");

        final var response = new SmartyUsStreetResponse();
        response.setDeliveryLine1("123 MAIN ST");
        final var components = new SmartyUsStreetResponse.Components();
        components.setCityName("SPRINGFIELD");
        components.setStateAbbreviation("IL");
        components.setZipcode("62704");
        components.setPlus4Code("1234");
        response.setComponents(components);

        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(new SmartyUsStreetResponse[]{response});

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED_WITH_CORRECTIONS, result.getStatus());
        assertEquals("62704-1234", result.getPostalCode());
    }

    @Test
    void usNullResponseReturnsInvalid() {
        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(null);

        final var result = provider.verify(buildUsRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    void usEmptyResponseReturnsInvalid() {
        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(new SmartyUsStreetResponse[0]);

        final var result = provider.verify(buildUsRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    void usExceptionReturnsProviderError() {
        when(uriGetSpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

        final var result = provider.verify(buildUsRequest("123 Main St", "City", "IL", "62704"));

        assertEquals(VerificationResult.Status.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    @Test
    void usMetadataPopulatedFromResponse() {
        final var request = buildUsRequest("123 Main St", "Springfield", "IL", "62704");

        final var response = new SmartyUsStreetResponse();
        response.setDeliveryLine1("123 Main St");
        final var components = new SmartyUsStreetResponse.Components();
        components.setCityName("Springfield");
        components.setStateAbbreviation("IL");
        components.setZipcode("62704");
        response.setComponents(components);

        final var metadata = new SmartyUsStreetResponse.Metadata();
        metadata.setCountyName("Sangamon");
        metadata.setLatitude("39.78");
        metadata.setLongitude("-89.65");
        metadata.setRecordType("S");
        metadata.setRdi("Commercial");
        response.setMetadata(metadata);

        final var analysis = new SmartyUsStreetResponse.Analysis();
        analysis.setDpvMatchCode("Y");
        analysis.setDpvFootnotes("AABB");
        analysis.setActive("Y");
        response.setAnalysis(analysis);

        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(new SmartyUsStreetResponse[]{response});

        final var result = provider.verify(request);

        assertEquals("Sangamon", result.getMetadata().get("countyName"));
        assertEquals(39.78, result.getLatitude());
        assertEquals(-89.65, result.getLongitude());
        assertNull(result.getMetadata().get("latitude"));
        assertNull(result.getMetadata().get("longitude"));
        assertEquals("S", result.getMetadata().get("recordType"));
        assertEquals("Commercial", result.getMetadata().get("rdi"));
        assertEquals("Y", result.getMetadata().get("dpvMatchCode"));
        assertEquals("AABB", result.getMetadata().get("dpvFootnotes"));
        assertEquals("Y", result.getMetadata().get("active"));
    }

    @Test
    void usZipPlus4Joining() {
        final var request = buildUsRequest("123 Main St", "Springfield", "IL", "62704");

        final var response = new SmartyUsStreetResponse();
        response.setDeliveryLine1("123 Main St");
        final var components = new SmartyUsStreetResponse.Components();
        components.setCityName("Springfield");
        components.setStateAbbreviation("IL");
        components.setZipcode("62704");
        components.setPlus4Code("5678");
        response.setComponents(components);

        when(responseSpec.body(SmartyUsStreetResponse[].class)).thenReturn(new SmartyUsStreetResponse[]{response});

        final var result = provider.verify(request);

        assertEquals("62704-5678", result.getPostalCode());
    }

    // --- International path ---

    @Test
    void caValidatedResponse() {
        final var request = buildInternationalRequest(CountryCode.CA, "123 Main St", "Ottawa", "ON", "K1A 0B1");

        final var response = new SmartyInternationalResponse();
        response.setAddress1("123 Main St");
        final var components = new SmartyInternationalResponse.Components();
        components.setLocality("Ottawa");
        components.setAdministrativeArea("ON");
        components.setPostalCode("K1A 0B1");
        response.setComponents(components);

        when(responseSpec.body(SmartyInternationalResponse[].class)).thenReturn(new SmartyInternationalResponse[]{response});

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertEquals(CountryCode.CA, result.getCountryCode());
        assertEquals(List.of("123 Main St"), result.getAddressLines());
        assertEquals("Ottawa", result.getLocality());
        assertEquals("ON", result.getAdministrativeArea());
        assertEquals("K1A 0B1", result.getPostalCode());
    }

    @Test
    void gbValidatedResponse() {
        final var request = buildInternationalRequest(CountryCode.GB, "10 Downing St", "London", "England", "SW1A 2AA");

        final var response = new SmartyInternationalResponse();
        response.setAddress1("10 Downing St");
        final var components = new SmartyInternationalResponse.Components();
        components.setLocality("London");
        components.setAdministrativeArea("England");
        components.setPostalCode("SW1A 2AA");
        response.setComponents(components);

        when(responseSpec.body(SmartyInternationalResponse[].class)).thenReturn(new SmartyInternationalResponse[]{response});

        final var result = provider.verify(request);

        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertEquals(CountryCode.GB, result.getCountryCode());
        assertEquals(List.of("10 Downing St"), result.getAddressLines());
    }

    @Test
    void internationalLatLngPopulatedFromMetadata() {
        final var request = buildInternationalRequest(CountryCode.CA, "123 Main St", "Ottawa", "ON", "K1A 0B1");

        final var response = new SmartyInternationalResponse();
        response.setAddress1("123 Main St");
        final var components = new SmartyInternationalResponse.Components();
        components.setLocality("Ottawa");
        components.setAdministrativeArea("ON");
        components.setPostalCode("K1A 0B1");
        response.setComponents(components);

        final var metadata = new SmartyInternationalResponse.Metadata();
        metadata.setLatitude("45.4215");
        metadata.setLongitude("-75.6972");
        response.setMetadata(metadata);

        when(responseSpec.body(SmartyInternationalResponse[].class)).thenReturn(new SmartyInternationalResponse[]{response});

        final var result = provider.verify(request);

        assertEquals(45.4215, result.getLatitude());
        assertEquals(-75.6972, result.getLongitude());
    }

    @Test
    void internationalNoMetadataLeavesLatLngNull() {
        final var request = buildInternationalRequest(CountryCode.CA, "123 Main St", "Ottawa", "ON", "K1A 0B1");

        final var response = new SmartyInternationalResponse();
        response.setAddress1("123 Main St");
        final var components = new SmartyInternationalResponse.Components();
        components.setLocality("Ottawa");
        components.setAdministrativeArea("ON");
        components.setPostalCode("K1A 0B1");
        response.setComponents(components);

        when(responseSpec.body(SmartyInternationalResponse[].class)).thenReturn(new SmartyInternationalResponse[]{response});

        final var result = provider.verify(request);

        assertNull(result.getLatitude());
        assertNull(result.getLongitude());
    }

    @Test
    void internationalNullResponseReturnsInvalid() {
        when(responseSpec.body(SmartyInternationalResponse[].class)).thenReturn(null);

        final var result = provider.verify(buildInternationalRequest(CountryCode.CA, "123 St", "City", "ON", "K1A"));

        assertEquals(VerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    void internationalExceptionReturnsProviderError() {
        when(uriGetSpec.retrieve()).thenThrow(new RuntimeException("Timeout"));

        final var result = provider.verify(buildInternationalRequest(CountryCode.CA, "123 St", "City", "ON", "K1A"));

        assertEquals(VerificationResult.Status.PROVIDER_ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("Timeout"));
    }

    // --- Helpers ---

    private VerificationRequest buildUsRequest(final String street, final String city,
                                              final String state, final String zip) {
        return VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of(street))
                .locality(city)
                .administrativeArea(state)
                .postalCode(zip)
                .build();
    }

    private VerificationRequest buildInternationalRequest(final int countryCode, final String street,
                                                         final String locality, final String adminArea,
                                                         final String postalCode) {
        return VerificationRequest.builder()
                .countryCode(countryCode)
                .addressLines(List.of(street))
                .locality(locality)
                .administrativeArea(adminArea)
                .postalCode(postalCode)
                .build();
    }
}
