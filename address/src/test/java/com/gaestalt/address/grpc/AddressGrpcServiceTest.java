/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.grpc;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.format.FormatVerificationResult;
import com.gaestalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.gaestalt.address.format.FormatVerificationResult.Severity;
import com.gaestalt.address.grpc.generated.*;
import com.gaestalt.address.provider.ProviderRegistry;
import com.gaestalt.address.provider.VerificationProvider;
import com.gaestalt.address.provider.VerificationResult;
import com.gaestalt.address.service.AddressFormatService;
import com.gaestalt.address.service.AddressVerificationService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AddressGrpcServiceTest {

    private AddressVerificationService verificationService;
    private AddressFormatService formatService;
    private ProviderRegistry providerRegistry;
    private Tracer tracer;
    private AddressGrpcService grpcService;

    @BeforeEach
    void setUp() {
        verificationService = mock(AddressVerificationService.class);
        formatService = mock(AddressFormatService.class);
        providerRegistry = mock(ProviderRegistry.class);
        tracer = OpenTelemetry.noop().getTracer("test");
        grpcService = new AddressGrpcService(verificationService, formatService, providerRegistry, tracer, true);
    }

    // --- verifyAddress: override path ---

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressOverrideProviderUnavailableReturnsStatus() {
        when(providerRegistry.getProviderByOverride(anyInt(), anyString())).thenReturn(Optional.empty());

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .setProviderId("unknown")
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals(VerificationStatus.PROVIDER_UNAVAILABLE, captor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressOverrideSuccessfulReturnsResponse() {
        final var provider = mock(VerificationProvider.class);
        when(provider.getProviderId()).thenReturn("usps");
        when(providerRegistry.getProviderByOverride(CountryCode.US, "usps")).thenReturn(Optional.of(provider));

        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704")
                .metadata(Map.of("dpvConfirmation", "Y"))
                .build();

        when(verificationService.verify(any(), any()))
                .thenReturn(new AddressVerificationService.Result(verificationResult, "usps"));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .setProviderId("usps")
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        final var response = captor.getValue();
        assertEquals(VerificationStatus.VERIFIED, response.getStatus());
        assertEquals("usps", response.getProviderId());
        assertEquals("Y", response.getMetadataOrDefault("dpvConfirmation", ""));
    }

    // --- verifyAddress: routing path ---

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressRoutingProviderUnavailableReturnsStatus() {
        when(verificationService.verifyWithRouting(any())).thenReturn(Optional.empty());

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals(VerificationStatus.PROVIDER_UNAVAILABLE, captor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressRoutingSuccessfulReturnsCorrectResponse() {
        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704")
                .metadata(Map.of("dpvConfirmation", "Y"))
                .build();

        when(verificationService.verifyWithRouting(any()))
                .thenReturn(Optional.of(new AddressVerificationService.Result(verificationResult, "usps")));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        final var response = captor.getValue();
        assertEquals(VerificationStatus.VERIFIED, response.getStatus());
        assertEquals("usps", response.getProviderId());
        assertEquals("Y", response.getMetadataOrDefault("dpvConfirmation", ""));
        assertEquals("123 MAIN ST", response.getStandardizedAddress().getAddressLines(0));
        assertEquals("SPRINGFIELD", response.getStandardizedAddress().getLocality());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressRoutingIncludesLatLngWhenPresent() {
        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704")
                .latitude(39.78)
                .longitude(-89.65)
                .metadata(Map.of())
                .build();

        when(verificationService.verifyWithRouting(any()))
                .thenReturn(Optional.of(new AddressVerificationService.Result(verificationResult, "smarty")));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        final var response = captor.getValue();
        assertTrue(response.hasLatitude());
        assertTrue(response.hasLongitude());
        assertEquals(39.78, response.getLatitude(), 0.001);
        assertEquals(-89.65, response.getLongitude(), 0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressRoutingOmitsLatLngWhenAbsent() {
        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704")
                .metadata(Map.of())
                .build();

        when(verificationService.verifyWithRouting(any()))
                .thenReturn(Optional.of(new AddressVerificationService.Result(verificationResult, "usps")));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        final var response = captor.getValue();
        assertFalse(response.hasLatitude());
        assertFalse(response.hasLongitude());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressRoutingWithCorrectionsMapStatus() {
        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED_WITH_CORRECTIONS)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704-1234")
                .metadata(Map.of())
                .build();

        when(verificationService.verifyWithRouting(any()))
                .thenReturn(Optional.of(new AddressVerificationService.Result(verificationResult, "usps")));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 main st", "springfield", "il", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        assertEquals(VerificationStatus.VERIFIED_WITH_CORRECTIONS, captor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressExceptionReturnsOnError() {
        when(verificationService.verifyWithRouting(any())).thenThrow(new RuntimeException("API failure"));

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "City", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(Throwable.class);

        grpcService.verifyAddress(request, observer);

        verify(observer).onError(captor.capture());
        verify(observer, never()).onCompleted();
        assertInstanceOf(StatusRuntimeException.class, captor.getValue());
    }

    // --- verifyAddressFormat ---

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressFormatUnsupportedCountryReturnsStatus() {
        when(formatService.verify(anyInt(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        final var request = VerifyAddressFormatRequest.newBuilder()
                .setAddress(buildPostalAddress(999, "123 Str", "City", "XX", "12345"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressFormatResponse.class);

        grpcService.verifyAddressFormat(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals(FormatStatus.FORMAT_UNSUPPORTED_COUNTRY, captor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressFormatValidReturnFormatValid() {
        final var formatResult = new FormatVerificationResult(840, List.of("123 Main St"),
                "Springfield", "IL", "62704", null);

        when(formatService.verify(anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(formatResult));

        final var request = VerifyAddressFormatRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressFormatResponse.class);

        grpcService.verifyAddressFormat(request, observer);

        verify(observer).onNext(captor.capture());
        assertEquals(FormatStatus.FORMAT_VALID, captor.getValue().getStatus());
        assertTrue(captor.getValue().getIssuesList().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressFormatCorrectedReturnFormatCorrected() {
        final var formatResult = new FormatVerificationResult(840, List.of("123 Main St"),
                "Springfield", "il", "62704", null);
        formatResult.setAdministrativeArea("IL");
        formatResult.addIssue(FormatIssueItem.builder()
                .field("administrative_area")
                .severity(Severity.INFO)
                .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                .originalValue("il")
                .correctedValue("IL")
                .build());

        when(formatService.verify(anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(formatResult));

        final var request = VerifyAddressFormatRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "Springfield", "il", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressFormatResponse.class);

        grpcService.verifyAddressFormat(request, observer);

        verify(observer).onNext(captor.capture());

        final var response = captor.getValue();
        assertEquals(FormatStatus.FORMAT_CORRECTED, response.getStatus());
        assertEquals(1, response.getIssuesList().size());
        assertEquals("administrative_area", response.getIssues(0).getField());
        assertEquals(FormatIssueSeverity.INFO, response.getIssues(0).getSeverity());
        assertEquals(FormatIssueCode.FIELD_CORRECTED, response.getIssues(0).getCode());
        assertEquals("IL", response.getCorrectedAddress().getAdministrativeArea());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressFormatInvalidReturnsFormatInvalid() {
        final var formatResult = new FormatVerificationResult(840, null, null, null, null, null);
        formatResult.addIssue(FormatIssueItem.builder()
                .field("address_lines")
                .severity(Severity.ERROR)
                .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                .originalValue("")
                .correctedValue("")
                .build());

        when(formatService.verify(anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(formatResult));

        final var request = VerifyAddressFormatRequest.newBuilder()
                .setAddress(PostalAddress.newBuilder().setCountryCode(840).build())
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressFormatResponse.class);

        grpcService.verifyAddressFormat(request, observer);

        verify(observer).onNext(captor.capture());
        assertEquals(FormatStatus.FORMAT_INVALID, captor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAddressFormatExceptionReturnsOnError() {
        when(formatService.verify(anyInt(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        final var request = VerifyAddressFormatRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "City", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);

        grpcService.verifyAddressFormat(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(observer, never()).onCompleted();
    }

    // --- getProviders ---

    @Test
    @SuppressWarnings("unchecked")
    void getProvidersReturnsMappedProviderInfo() {
        final var provider = mock(VerificationProvider.class);
        when(provider.getProviderId()).thenReturn("usps");
        when(provider.getDisplayName()).thenReturn("USPS Address Verification API");
        when(provider.getSupportedCountries()).thenReturn(List.of(CountryCode.US));
        when(provider.isEnabled()).thenReturn(true);

        when(providerRegistry.getAllProviders()).thenReturn(List.of(provider));

        final var request = GetProvidersRequest.newBuilder().build();
        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(GetProvidersResponse.class);

        grpcService.getProviders(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        final var response = captor.getValue();
        assertEquals(1, response.getProvidersList().size());
        assertEquals("usps", response.getProviders(0).getProviderId());
        assertEquals("USPS Address Verification API", response.getProviders(0).getDisplayName());
        assertEquals(List.of(CountryCode.US), response.getProviders(0).getSupportedCountriesList());
        assertTrue(response.getProviders(0).getEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProvidersEmptyListReturnsEmptyResponse() {
        when(providerRegistry.getAllProviders()).thenReturn(Collections.emptyList());

        final var request = GetProvidersRequest.newBuilder().build();
        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(GetProvidersResponse.class);

        grpcService.getProviders(request, observer);

        verify(observer).onNext(captor.capture());
        assertTrue(captor.getValue().getProvidersList().isEmpty());
    }

    // --- Tracing disabled ---

    @Test
    @SuppressWarnings("unchecked")
    void tracingDisabledStillSucceeds() {
        final var grpcServiceNoTracing = new AddressGrpcService(
                verificationService, formatService, providerRegistry, tracer, false);

        when(verificationService.verifyWithRouting(any())).thenReturn(Optional.empty());

        final var request = VerifyAddressRequest.newBuilder()
                .setAddress(buildPostalAddress(840, "123 Main St", "City", "IL", "62704"))
                .build();

        final var observer = mock(StreamObserver.class);
        final var captor = ArgumentCaptor.forClass(VerifyAddressResponse.class);

        grpcServiceNoTracing.verifyAddress(request, observer);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals(VerificationStatus.PROVIDER_UNAVAILABLE, captor.getValue().getStatus());
    }

    // --- Helpers ---

    private PostalAddress buildPostalAddress(final int countryCode, final String street,
                                             final String locality, final String adminArea,
                                             final String postalCode) {
        return PostalAddress.newBuilder()
                .setCountryCode(countryCode)
                .addAddressLines(street)
                .setLocality(locality)
                .setAdministrativeArea(adminArea)
                .setPostalCode(postalCode)
                .build();
    }
}
