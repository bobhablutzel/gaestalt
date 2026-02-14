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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressVerificationService {

    private final ProviderRegistry providerRegistry;

    public record Result(VerificationResult verificationResult, String providerId) {}

    public Result verify(final VerificationRequest request, final VerificationProvider provider) {
        log.info("Verifying address with provider '{}' for country {}",
                provider.getProviderId(), CountryCode.displayName(request.getCountryCode()));
        final var result = provider.verify(request);
        return new Result(result, provider.getProviderId());
    }

    public Optional<Result> verifyWithRouting(final VerificationRequest request) {
        final var providers = providerRegistry.getRoutedProviders(request.getCountryCode());
        final var lastError = new AtomicReference<Result>();
        return providers.stream()
                .map(provider -> verify(request, provider))
                .peek(result -> {
                    if (result.verificationResult().getStatus() == VerificationResult.Status.PROVIDER_ERROR) {
                        log.warn("Provider '{}' returned PROVIDER_ERROR for country {}, trying next fallback",
                                result.providerId(), CountryCode.displayName(request.getCountryCode()));
                        lastError.set(result);
                    }
                })
                .filter(result -> result.verificationResult().getStatus() != VerificationResult.Status.PROVIDER_ERROR)
                .findFirst()
                .or(() -> Optional.ofNullable(lastError.get()));
    }
}
