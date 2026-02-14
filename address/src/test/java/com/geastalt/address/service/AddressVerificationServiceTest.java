/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.service;

import com.geastalt.address.CountryCode;
import com.geastalt.address.provider.ProviderRegistry;
import com.geastalt.address.provider.VerificationProvider;
import com.geastalt.address.provider.VerificationRequest;
import com.geastalt.address.provider.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressVerificationServiceTest {

    private ProviderRegistry providerRegistry;
    private AddressVerificationService service;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(ProviderRegistry.class);
        service = new AddressVerificationService(providerRegistry);
    }

    @Test
    void verifyCallsProviderAndWrapsResult() {
        final var provider = mock(VerificationProvider.class);
        when(provider.getProviderId()).thenReturn("usps");

        final var request = VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 Main St"))
                .locality("Springfield")
                .administrativeArea("IL")
                .postalCode("62704")
                .build();

        final var verificationResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 MAIN ST"))
                .locality("SPRINGFIELD")
                .administrativeArea("IL")
                .postalCode("62704")
                .metadata(Map.of())
                .build();

        when(provider.verify(request)).thenReturn(verificationResult);

        final var result = service.verify(request, provider);

        assertEquals("usps", result.providerId());
        assertSame(verificationResult, result.verificationResult());
        verify(provider).verify(request);
    }

    @Test
    void verifyWithRoutingReturnsFirstSuccess() {
        final var provider1 = mock(VerificationProvider.class);
        when(provider1.getProviderId()).thenReturn("usps");
        final var provider2 = mock(VerificationProvider.class);
        when(provider2.getProviderId()).thenReturn("smarty");

        final var request = VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 Main St"))
                .locality("Springfield")
                .administrativeArea("IL")
                .postalCode("62704")
                .build();

        final var successResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .metadata(Map.of())
                .build();

        when(providerRegistry.getRoutedProviders(CountryCode.US))
                .thenReturn(List.of(provider1, provider2));
        when(provider1.verify(request)).thenReturn(successResult);

        final var result = service.verifyWithRouting(request);

        assertTrue(result.isPresent());
        assertEquals("usps", result.get().providerId());
        verify(provider2, never()).verify(any());
    }

    @Test
    void verifyWithRoutingFallsBackOnProviderError() {
        final var provider1 = mock(VerificationProvider.class);
        when(provider1.getProviderId()).thenReturn("usps");
        final var provider2 = mock(VerificationProvider.class);
        when(provider2.getProviderId()).thenReturn("smarty");

        final var request = VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 Main St"))
                .locality("Springfield")
                .administrativeArea("IL")
                .postalCode("62704")
                .build();

        final var errorResult = VerificationResult.builder()
                .status(VerificationResult.Status.PROVIDER_ERROR)
                .message("API error")
                .metadata(Map.of())
                .build();
        final var successResult = VerificationResult.builder()
                .status(VerificationResult.Status.VERIFIED)
                .countryCode(CountryCode.US)
                .metadata(Map.of())
                .build();

        when(providerRegistry.getRoutedProviders(CountryCode.US))
                .thenReturn(List.of(provider1, provider2));
        when(provider1.verify(request)).thenReturn(errorResult);
        when(provider2.verify(request)).thenReturn(successResult);

        final var result = service.verifyWithRouting(request);

        assertTrue(result.isPresent());
        assertEquals("smarty", result.get().providerId());
        assertSame(successResult, result.get().verificationResult());
    }

    @Test
    void verifyWithRoutingReturnsEmptyWhenNoProviders() {
        final var request = VerificationRequest.builder()
                .countryCode(999)
                .addressLines(List.of("123 St"))
                .build();

        when(providerRegistry.getRoutedProviders(999)).thenReturn(List.of());

        final var result = service.verifyWithRouting(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void verifyWithRoutingReturnsLastErrorWhenAllFail() {
        final var provider1 = mock(VerificationProvider.class);
        when(provider1.getProviderId()).thenReturn("usps");
        final var provider2 = mock(VerificationProvider.class);
        when(provider2.getProviderId()).thenReturn("smarty");

        final var request = VerificationRequest.builder()
                .countryCode(CountryCode.US)
                .addressLines(List.of("123 Main St"))
                .locality("Springfield")
                .administrativeArea("IL")
                .postalCode("62704")
                .build();

        final var error1 = VerificationResult.builder()
                .status(VerificationResult.Status.PROVIDER_ERROR)
                .message("USPS error")
                .metadata(Map.of())
                .build();
        final var error2 = VerificationResult.builder()
                .status(VerificationResult.Status.PROVIDER_ERROR)
                .message("Smarty error")
                .metadata(Map.of())
                .build();

        when(providerRegistry.getRoutedProviders(CountryCode.US))
                .thenReturn(List.of(provider1, provider2));
        when(provider1.verify(request)).thenReturn(error1);
        when(provider2.verify(request)).thenReturn(error2);

        final var result = service.verifyWithRouting(request);

        assertTrue(result.isPresent());
        assertEquals(VerificationResult.Status.PROVIDER_ERROR, result.get().verificationResult().getStatus());
        assertEquals("smarty", result.get().providerId());
    }
}
