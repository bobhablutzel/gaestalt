/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider;

import com.geastalt.address.provider.routing.ProviderRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProviderRegistry {

    private final Map<String, VerificationProvider> providersById;
    private final ProviderRouter router;

    public ProviderRegistry(final List<VerificationProvider> providers,
                            final ProviderRouter router) {
        this.providersById = providers.stream()
                .collect(Collectors.toMap(VerificationProvider::getProviderId, Function.identity()));
        this.router = router;
        log.info("Registered {} verification providers: {}", providers.size(),
                providers.stream().map(VerificationProvider::getProviderId).toList());
    }

    public Optional<VerificationProvider> getProviderByOverride(final int countryCode, final String providerId) {
        final var provider = providersById.get(providerId);
        if (provider != null && provider.isEnabled()
                && provider.getSupportedCountries().contains(countryCode)) {
            return Optional.of(provider);
        }
        return Optional.empty();
    }

    public List<VerificationProvider> getRoutedProviders(final int countryCode) {
        return router.selectProviders(countryCode).stream()
                .filter(p -> p.isEnabled()
                        && p.getSupportedCountries().contains(countryCode))
                .toList();
    }

    public List<VerificationProvider> getAllProviders() {
        return List.copyOf(providersById.values());
    }
}
