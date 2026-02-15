/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.format;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CountryFormatRegistry {

    private final Map<Integer, FormatVerifier> verifiersByCountry;

    public CountryFormatRegistry(final List<FormatVerifier> verifiers) {
        this.verifiersByCountry = verifiers.stream()
                .collect(Collectors.toMap(FormatVerifier::getCountryCode, Function.identity()));
        log.info("Registered format verifiers for countries: {}", verifiersByCountry.keySet());
    }

    public Optional<FormatVerifier> getVerifier(final int countryCode) {
        return Optional.ofNullable(verifiersByCountry.get(countryCode));
    }

    public boolean isSupported(final int countryCode) {
        return verifiersByCountry.containsKey(countryCode);
    }
}
