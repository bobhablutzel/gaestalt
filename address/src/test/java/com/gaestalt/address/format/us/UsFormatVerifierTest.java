/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.format.us;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.format.FormatVerificationResult;
import com.gaestalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.gaestalt.address.format.FormatVerificationResult.Severity;
import com.gaestalt.address.format.FormatVerificationResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsFormatVerifierTest {

    private UsFormatVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new UsFormatVerifier();
    }

    @Test
    void getCountryCodeReturns840() {
        assertEquals(CountryCode.US, verifier.getCountryCode());
    }

    // --- Address Lines ---

    @Test
    void nullAddressLinesProducesError() {
        final var result = verifier.verify(840, null, "Springfield", "IL", "62704", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void emptyAddressLinesProducesError() {
        final var result = verifier.verify(840, Collections.emptyList(), "Springfield", "IL", "62704", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void allBlankAddressLinesProducesError() {
        final var result = verifier.verify(840, List.of("", "  "), "Springfield", "IL", "62704", null);
        assertHasIssue(result, "address_lines", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void validAddressLinesProducesNoAddressLineIssue() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "62704", null);
        assertNoIssueForField(result, "address_lines");
    }

    // --- Locality ---

    @Test
    void nullLocalityProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), null, "IL", "62704", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankLocalityProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "  ", "IL", "62704", null);
        assertHasIssue(result, "locality", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    // --- State (Administrative Area) ---

    @Test
    void nullStateProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", null, "62704", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankStateProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "", "62704", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void invalidStateCodeProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "XX", "62704", null);
        assertHasIssue(result, "administrative_area", Severity.ERROR, FormatIssueItem.CODE_FIELD_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IL", "CA", "NY", "TX", "DC", "PR", "GU", "VI", "AS", "MP"})
    void validStateCodeProducesNoIssue(final String state) {
        final var result = verifier.verify(840, List.of("123 Main St"), "City", state, "62704", null);
        assertNoIssueForField(result, "administrative_area");
    }

    @Test
    void lowercaseStateCorrectedToUppercase() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "il", "62704", null);

        assertEquals("IL", result.getAdministrativeArea());
        assertHasIssue(result, "administrative_area", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);

        final var issue = findIssue(result, "administrative_area");
        assertEquals("il", issue.getOriginalValue());
        assertEquals("IL", issue.getCorrectedValue());
    }

    // --- ZIP Code ---

    @Test
    void nullPostalCodeProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", null, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void blankPostalCodeProducesError() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "", null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_REQUIRED);
    }

    @Test
    void validFiveDigitZipProducesNoIssue() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "62704", null);
        assertNoIssueForField(result, "postal_code");
    }

    @Test
    void validZipPlusFourWithDashProducesNoIssue() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "62704-1234", null);
        assertNoIssueForField(result, "postal_code");
    }

    @Test
    void nineDigitZipWithoutDashCorrected() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "627041234", null);

        assertEquals("62704-1234", result.getPostalCode());
        assertHasIssue(result, "postal_code", Severity.INFO, FormatIssueItem.CODE_FIELD_CORRECTED);

        final var issue = findIssue(result, "postal_code");
        assertEquals("627041234", issue.getOriginalValue());
        assertEquals("62704-1234", issue.getCorrectedValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCDE", "1234", "123456", "62704-12", "62704-12345"})
    void invalidZipPatternProducesError(final String zip) {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", zip, null);
        assertHasIssue(result, "postal_code", Severity.ERROR, FormatIssueItem.CODE_FIELD_INVALID);
    }

    // --- Combined scenarios ---

    @Test
    void allValidFieldsProduceValidStatus() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "IL", "62704", null);
        assertEquals(Status.VALID, result.getStatus());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void allMissingFieldsProduceInvalidStatusWithFourErrors() {
        final var result = verifier.verify(840, null, null, null, null, null);
        assertEquals(Status.INVALID, result.getStatus());
        assertEquals(4, result.getIssues().size());
    }

    @Test
    void correctionsOnlyProduceCorrectedStatus() {
        final var result = verifier.verify(840, List.of("123 Main St"), "Springfield", "il", "627041234", null);
        assertEquals(Status.CORRECTED, result.getStatus());
        assertEquals(2, result.getIssues().size());
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
