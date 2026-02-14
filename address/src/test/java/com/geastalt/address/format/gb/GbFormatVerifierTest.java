/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format.gb;

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

class GbFormatVerifierTest {

    private GbFormatVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GbFormatVerifier();
    }

    @Test
    void getCountryCodeReturns826() {
        assertEquals(CountryCode.GB, verifier.getCountryCode());
    }

    // --- Address Lines ---

    @Test
    void nullAddressLinesProducesError() {
        final var result = verifier.verify(826, null, "London", null, "SW1A 1AA", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void emptyAddressLinesProducesError() {
        final var result = verifier.verify(826, Collections.emptyList(), "London", null, "SW1A 1AA", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void allBlankAddressLinesProducesError() {
        final var result = verifier.verify(826, List.of("", "  "), "London", null, "SW1A 1AA", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    // --- Locality ---

    @Test
    void nullLocalityProducesError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), null, null, "SW1A 2AA", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankLocalityProducesError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "  ", null, "SW1A 2AA", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    // --- Administrative Area (NOT required for UK) ---

    @Test
    void nullAdministrativeAreaProducesNoError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "SW1A 2AA", null);
        assertNoIssueForField(result, "administrative_area");
    }

    @Test
    void blankAdministrativeAreaProducesNoError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", "", "SW1A 2AA", null);
        assertNoIssueForField(result, "administrative_area");
    }

    // --- UK Postcode ---

    @Test
    void nullPostalCodeProducesError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, null, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankPostalCodeProducesError() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "", null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M1 1AA", "B33 8TH", "W1A 0AX", "EC1A 1BB", "CR2 6XH", "DN55 1PT"})
    void validUkPostcodeFormatsProduceNoIssue(final String postcode) {
        final var result = verifier.verify(826, List.of("1 High St"), "City", null, postcode, null);
        assertNoIssueForField(result, "postal_code");
    }

    @Test
    void postcodeWithoutSpaceCorrected() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "SW1A2AA", null);

        assertEquals("SW1A 2AA", result.getPostalCode());
        assertHasIssue(result, "postal_code", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);

        final var issue = findIssue(result, "postal_code");
        assertEquals("SW1A2AA", issue.getOriginalValue());
        assertEquals("SW1A 2AA", issue.getCorrectedValue());
    }

    @Test
    void lowercasePostcodeCorrectedToUppercase() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "sw1a 2aa", null);

        assertEquals("SW1A 2AA", result.getPostalCode());
        assertHasIssue(result, "postal_code", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "ABCDEF", "SW1A-2AA", "1AA SW1"})
    void invalidPostcodePatternProducesError(final String postcode) {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, postcode, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_INVALID);
    }

    // --- Combined ---

    @Test
    void allValidFieldsProduceValidStatus() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "SW1A 2AA", null);
        assertEquals(Status.VALID, result.getStatus());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void allMissingRequiredFieldsProduceInvalidWithThreeErrors() {
        final var result = verifier.verify(826, null, null, null, null, null);
        assertEquals(Status.INVALID, result.getStatus());
        // address_lines, locality, postal_code (NOT administrative_area for UK)
        assertEquals(3, result.getIssues().size());
    }

    @Test
    void correctionOnlyProducesCorrectedStatus() {
        final var result = verifier.verify(826, List.of("10 Downing St"), "London", null, "sw1a2aa", null);
        assertEquals(Status.CORRECTED, result.getStatus());
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
