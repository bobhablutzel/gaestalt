/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.provider.routing.ProviderRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderRegistryTest {

    private VerificationProvider createProvider(final String id, final List<Integer> countries, final boolean enabled) {
        final var provider = mock(VerificationProvider.class);
        when(provider.getProviderId()).thenReturn(id);
        when(provider.getSupportedCountries()).thenReturn(countries);
        when(provider.isEnabled()).thenReturn(enabled);
        return provider;
    }

    // --- getProviderByOverride ---

    @Test
    void overrideReturnsMatchingProvider() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        final var result = registry.getProviderByOverride(CountryCode.US, "usps");

        assertTrue(result.isPresent());
        assertSame(usps, result.get());
    }

    @Test
    void overrideWrongCountryReturnsEmpty() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        final var result = registry.getProviderByOverride(CountryCode.CA, "usps");

        assertTrue(result.isEmpty());
    }

    @Test
    void overrideDisabledProviderReturnsEmpty() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), false);
        final var registry = new ProviderRegistry(List.of(usps), router);

        final var result = registry.getProviderByOverride(CountryCode.US, "usps");

        assertTrue(result.isEmpty());
    }

    @Test
    void overrideUnknownProviderIdReturnsEmpty() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        final var result = registry.getProviderByOverride(CountryCode.US, "unknown");

        assertTrue(result.isEmpty());
    }

    // --- getRoutedProviders ---

    @Test
    void routedProvidersReturnsProvidersFromRouter() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var smarty = createProvider("smarty", List.of(CountryCode.US, CountryCode.CA), true);
        final var registry = new ProviderRegistry(List.of(usps, smarty), router);

        when(router.selectProviders(CountryCode.US)).thenReturn(List.of(usps, smarty));

        final var result = registry.getRoutedProviders(CountryCode.US);

        assertEquals(2, result.size());
        assertSame(usps, result.get(0));
        assertSame(smarty, result.get(1));
    }

    @Test
    void routedProvidersFiltersDisabled() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), false);
        final var smarty = createProvider("smarty", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps, smarty), router);

        when(router.selectProviders(CountryCode.US)).thenReturn(List.of(usps, smarty));

        final var result = registry.getRoutedProviders(CountryCode.US);

        assertEquals(1, result.size());
        assertSame(smarty, result.get(0));
    }

    @Test
    void routedProvidersFiltersUnsupportedCountry() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        when(router.selectProviders(CountryCode.CA)).thenReturn(List.of(usps));

        final var result = registry.getRoutedProviders(CountryCode.CA);

        assertTrue(result.isEmpty());
    }

    @Test
    void routedProvidersReturnsEmptyWhenRouterReturnsEmpty() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        when(router.selectProviders(999)).thenReturn(List.of());

        final var result = registry.getRoutedProviders(999);

        assertTrue(result.isEmpty());
    }

    // --- getAllProviders ---

    @Test
    void getAllProvidersReturnsAllRegistered() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var smarty = createProvider("smarty", List.of(CountryCode.CA), true);
        final var registry = new ProviderRegistry(List.of(usps, smarty), router);

        final var all = registry.getAllProviders();

        assertEquals(2, all.size());
    }

    @Test
    void getAllProvidersReturnsUnmodifiableList() {
        final var router = mock(ProviderRouter.class);
        final var usps = createProvider("usps", List.of(CountryCode.US), true);
        final var registry = new ProviderRegistry(List.of(usps), router);

        assertThrows(UnsupportedOperationException.class, () -> registry.getAllProviders().add(usps));
    }
}
