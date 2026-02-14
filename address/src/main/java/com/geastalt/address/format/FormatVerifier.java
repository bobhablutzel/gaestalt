/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format;

public interface FormatVerifier {

    int getCountryCode();

    /**
     * Verify the input values against country-specific standards.
     * The validations generally follow the pattern of
     * <ul>
     * <li>Validate that the item is present (not blank / null)</li>
     * <li>Check on the format if specific format rules exist</li>
     * </ul>
     *
     * This method <i>does not</i> validate that the address
     * is an actual address in the real world; this just does
     * simple format verification.
     */
    FormatVerificationResult verify(final int countryCode,
                                    final java.util.List<String> addressLines,
                                    final String locality,
                                    final String administrativeArea,
                                    final String postalCode,
                                    final String subLocality);
}
