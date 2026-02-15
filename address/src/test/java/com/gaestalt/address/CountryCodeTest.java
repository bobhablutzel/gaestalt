/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountryCodeTest {

    @Test
    void usConstantIs840() {
        assertEquals(840, CountryCode.US);
    }

    @Test
    void caConstantIs124() {
        assertEquals(124, CountryCode.CA);
    }

    @Test
    void gbConstantIs826() {
        assertEquals(826, CountryCode.GB);
    }

    @Test
    void displayNameForUsReturnsCodeWithAlpha() {
        assertEquals("840 (US)", CountryCode.displayName(CountryCode.US));
    }

    @Test
    void displayNameForCaReturnsCodeWithAlpha() {
        assertEquals("124 (CA)", CountryCode.displayName(CountryCode.CA));
    }

    @Test
    void displayNameForGbReturnsCodeWithAlpha() {
        assertEquals("826 (GB)", CountryCode.displayName(CountryCode.GB));
    }

    @Test
    void displayNameForUnknownCodeReturnsJustNumber() {
        assertEquals("999", CountryCode.displayName(999));
    }
}
