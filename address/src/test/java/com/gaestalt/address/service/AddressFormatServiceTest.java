/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.service;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.format.CountryFormatRegistry;
import com.gaestalt.address.format.FormatVerificationResult;
import com.gaestalt.address.format.FormatVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressFormatServiceTest {

    private CountryFormatRegistry formatRegistry;
    private AddressFormatService service;

    @BeforeEach
    void setUp() {
        formatRegistry = mock(CountryFormatRegistry.class);
        service = new AddressFormatService(formatRegistry);
    }

    @Test
    void verifyDelegatesToRegistryVerifier() {
        final var verifier = mock(FormatVerifier.class);
        final var expectedResult = new FormatVerificationResult(840, List.of("123 Main St"), "City", "IL", "62704", null);

        when(formatRegistry.getVerifier(CountryCode.US)).thenReturn(Optional.of(verifier));
        when(verifier.verify(840, List.of("123 Main St"), "City", "IL", "62704", null)).thenReturn(expectedResult);

        final var result = service.verify(840, List.of("123 Main St"), "City", "IL", "62704", null);

        assertTrue(result.isPresent());
        assertSame(expectedResult, result.get());
    }

    @Test
    void verifyReturnsEmptyWhenNoVerifier() {
        when(formatRegistry.getVerifier(999)).thenReturn(Optional.empty());

        final var result = service.verify(999, List.of("123 Main St"), "City", "ST", "12345", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void isSupportedDelegatesToRegistry() {
        when(formatRegistry.isSupported(CountryCode.US)).thenReturn(true);
        when(formatRegistry.isSupported(999)).thenReturn(false);

        assertTrue(service.isSupported(CountryCode.US));
        assertFalse(service.isSupported(999));
    }
}
