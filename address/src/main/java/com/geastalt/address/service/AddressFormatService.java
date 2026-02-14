/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.service;

import com.geastalt.address.CountryCode;
import com.geastalt.address.format.CountryFormatRegistry;
import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The AddressFormatService verifies that a supplied address
 * appears to be properly formatted for the specific country.
 * The format verification <i>does not</i> validate that the
 * address is actually a real address, just that the format
 * appears to be correct. This is useful for quick format checks
 * (e.g. for user interfaces) or when receiving an address
 * from either a trusted source, or where the business process
 * allows the validation & normalization to be deferred.
 *
 * The bulk of the logic is deferred to the {@link FormatVerifier} class,
 * which is stored in the {@link CountryFormatRegistry}.
 *
 * @author Bob Hablutzel
 * @since 1.0.0
 * @see FormatVerifier
 * @see CountryFormatRegistry
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class AddressFormatService {

    /**
     * Registry used to find formatters via country code.
     */
    private final CountryFormatRegistry formatRegistry;

    /**
     * Validate that the format of an address is valid, given
     * the countryCode (to determine the correct format validator
     * to use) and the individual address data elements
     * 
     * @param countryCode The country code, in <a href="https://en.wikipedia.org/wiki/ISO_3166-1_numeric">ISO 3166-1 Numeric</a> format
     * @param addressLines The variable lines of the address. Depending on the country, 2-5 lines may be supported.
     * @param locality The locality
     * @param administrativeArea
     * @param postalCode
     * @param subLocality
     * @return
     */
    public Optional<FormatVerificationResult> verify(final int countryCode,
                                                       final List<String> addressLines,
                                                       final String locality,
                                                       final String administrativeArea,
                                                       final String postalCode,
                                                       final String subLocality) {
        log.info("Verifying address format for country {}", CountryCode.displayName(countryCode));
        return formatRegistry.getVerifier(countryCode)
                .map(verifier -> verifier.verify(countryCode, addressLines, locality,
                        administrativeArea, postalCode, subLocality));
    }

    public boolean isSupported(final int countryCode) {
        return formatRegistry.isSupported(countryCode);
    }
}
