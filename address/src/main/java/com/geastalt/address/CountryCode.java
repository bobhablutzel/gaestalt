/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address;

import java.util.Map;

/**
 * ISO 3166-1 numeric country code constants for the currently supported countries.
 */
public final class CountryCode {

    public static final int US = 840;
    public static final int CA = 124;
    public static final int GB = 826;

    private static final Map<Integer, String> ALPHA2 = Map.of(
            US, "US",
            CA, "CA",
            GB, "GB"
    );

    private CountryCode() {}

    /**
     * Returns a human-readable display name, e.g. {@code "840 (US)"}.
     */
    public static String displayName(final int code) {
        final var alpha = ALPHA2.get(code);
        return alpha != null ? code + " (" + alpha + ")" : String.valueOf(code);
    }
}
