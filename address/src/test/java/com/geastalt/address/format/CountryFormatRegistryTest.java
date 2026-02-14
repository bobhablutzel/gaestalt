/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format;

import com.geastalt.address.CountryCode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CountryFormatRegistryTest {

    @Test
    void getVerifierReturnsPresentForRegisteredCountry() {
        final var usVerifier = mock(FormatVerifier.class);
        when(usVerifier.getCountryCode()).thenReturn(CountryCode.US);

        final var registry = new CountryFormatRegistry(List.of(usVerifier));

        assertTrue(registry.getVerifier(CountryCode.US).isPresent());
        assertSame(usVerifier, registry.getVerifier(CountryCode.US).get());
    }

    @Test
    void getVerifierReturnsEmptyForUnregisteredCountry() {
        final var usVerifier = mock(FormatVerifier.class);
        when(usVerifier.getCountryCode()).thenReturn(CountryCode.US);

        final var registry = new CountryFormatRegistry(List.of(usVerifier));

        assertTrue(registry.getVerifier(999).isEmpty());
    }

    @Test
    void isSupportedReturnsTrueForRegistered() {
        final var caVerifier = mock(FormatVerifier.class);
        when(caVerifier.getCountryCode()).thenReturn(CountryCode.CA);

        final var registry = new CountryFormatRegistry(List.of(caVerifier));

        assertTrue(registry.isSupported(CountryCode.CA));
    }

    @Test
    void isSupportedReturnsFalseForUnregistered() {
        final var registry = new CountryFormatRegistry(Collections.emptyList());

        assertFalse(registry.isSupported(CountryCode.US));
    }

    @Test
    void emptyVerifierListCreatesEmptyRegistry() {
        final var registry = new CountryFormatRegistry(Collections.emptyList());

        assertTrue(registry.getVerifier(CountryCode.US).isEmpty());
        assertTrue(registry.getVerifier(CountryCode.CA).isEmpty());
        assertTrue(registry.getVerifier(CountryCode.GB).isEmpty());
    }
}
