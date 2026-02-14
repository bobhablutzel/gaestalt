/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format.ca;

import com.geastalt.address.CountryCode;
import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.geastalt.address.format.FormatVerificationResult.Severity;
import com.geastalt.address.format.FormatVerificationResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaFormatVerifierTest {

    private CaFormatVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new CaFormatVerifier();
    }

    @Test
    void getCountryCodeReturns124() {
        assertEquals(CountryCode.CA, verifier.getCountryCode());
    }

    // --- Address Lines ---

    @Test
    void nullAddressLinesProducesError() {
        final var result = verifier.verify(124, null, "Ottawa", "ON", "K1A 0A6", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void emptyAddressLinesProducesError() {
        final var result = verifier.verify(124, Collections.emptyList(), "Ottawa", "ON", "K1A 0A6", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void allBlankAddressLinesProducesError() {
        final var result = verifier.verify(124, List.of("", "  "), "Ottawa", "ON", "K1A 0A6", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    // --- Locality ---

    @Test
    void nullLocalityProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), null, "ON", "K1A 0A6", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankLocalityProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "  ", "ON", "K1A 0A6", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    // --- Province (Administrative Area) ---

    @Test
    void nullProvinceProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", null, "K1A 0A6", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankProvinceProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "", "K1A 0A6", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void invalidProvinceCodeProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "XX", "K1A 0A6", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT"})
    void validProvinceCodeProducesNoIssue(final String province) {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", province, "K1A 0A6", null);
        assertNoIssueForField(result, "administrative_area");
    }

    @Test
    void lowercaseProvinceCorrectedToUppercase() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "on", "K1A 0A6", null);

        assertEquals("ON", result.getAdministrativeArea());
        assertHasIssue(result, "administrative_area", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);

        final var issue = findIssue(result, "administrative_area");
        assertEquals("on", issue.getOriginalValue());
        assertEquals("ON", issue.getCorrectedValue());
    }

    // --- Postal Code ---

    @Test
    void nullPostalCodeProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", null, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankPostalCodeProducesError() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", "", null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void validPostalCodeWithSpaceProducesNoIssue() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", "K1A 0A6", null);
        assertNoIssueForField(result, "postal_code");
    }

    @Test
    void postalCodeWithoutSpaceCorrected() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", "K1A0A6", null);

        assertEquals("K1A 0A6", result.getPostalCode());
        assertHasIssue(result, "postal_code", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);

        final var issue = findIssue(result, "postal_code");
        assertEquals("K1A0A6", issue.getOriginalValue());
        assertEquals("K1A 0A6", issue.getCorrectedValue());
    }

    @Test
    void lowercasePostalCodeCorrectedToUppercase() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", "k1a 0a6", null);

        assertEquals("K1A 0A6", result.getPostalCode());
        assertHasIssue(result, "postal_code", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "ABCDEF", "K1A-0A6", "K1A 0A"})
    void invalidPostalCodePatternProducesError(final String postalCode) {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", postalCode, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_INVALID);
    }

    // --- Combined ---

    @Test
    void allValidFieldsProduceValidStatus() {
        final var result = verifier.verify(124, List.of("80 Wellington St"), "Ottawa", "ON", "K1A 0A6", null);
        assertEquals(Status.VALID, result.getStatus());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void allMissingFieldsProduceInvalidStatus() {
        final var result = verifier.verify(124, null, null, null, null, null);
        assertEquals(Status.INVALID, result.getStatus());
        assertEquals(4, result.getIssues().size());
    }

    // --- Helpers ---

    private void assertHasIssue(final FormatVerificationResult result, final String field,
                                final Severity severity, final int code) {
        final var found = result.getIssues().stream()
                .anyMatch(i -> field.equals(i.getField()) && i.getSeverity() == severity && i.getCode() == code);
        assertTrue(found, "Expected issue on field '%s' with severity %s and code %d".formatted(field, severity, code));
    }

    private void assertNoIssueForField(final FormatVerificationResult result, final String field) {
        final var found = result.getIssues().stream().anyMatch(i -> field.equals(i.getField()));
        assertFalse(found, "Expected no issue on field '%s' but found one".formatted(field));
    }

    private FormatIssueItem findIssue(final FormatVerificationResult result, final String field) {
        return result.getIssues().stream()
                .filter(i -> field.equals(i.getField()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No issue found for field: " + field));
    }
}
