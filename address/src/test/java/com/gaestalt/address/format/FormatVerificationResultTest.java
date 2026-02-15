/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.format;

import com.gaestalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.gaestalt.address.format.FormatVerificationResult.Severity;
import com.gaestalt.address.format.FormatVerificationResult.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormatVerificationResultTest {

    @Test
    void constructorSetsAllFields() {
        final var lines = List.of("123 Main St");
        final var result = new FormatVerificationResult(840, lines, "Springfield", "IL", "62704", "Downtown");

        assertEquals(840, result.getCountryCode());
        assertEquals(lines, result.getAddressLines());
        assertEquals("Springfield", result.getLocality());
        assertEquals("IL", result.getAdministrativeArea());
        assertEquals("62704", result.getPostalCode());
        assertEquals("Downtown", result.getSubLocality());
    }

    @Test
    void issuesListStartsEmpty() {
        final var result = new FormatVerificationResult(840, List.of(), null, null, null, null);
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void addIssueAppendsToList() {
        final var result = new FormatVerificationResult(840, List.of(), null, null, null, null);
        final var issue = FormatIssueItem.builder()
                .field("locality")
                .severity(Severity.ERROR)
                .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                .originalValue("")
                .correctedValue("")
                .build();

        result.addIssue(issue);

        assertEquals(1, result.getIssues().size());
        assertEquals("locality", result.getIssues().get(0).getField());
    }

    @Test
    void getStatusReturnsValidWhenNoIssues() {
        final var result = new FormatVerificationResult(840, List.of("123 Main St"), "City", "IL", "62704", null);
        assertEquals(Status.VALID, result.getStatus());
    }

    @Test
    void getStatusReturnsCorrectedWhenOnlyInfoIssues() {
        final var result = new FormatVerificationResult(840, List.of("123 Main St"), "City", "IL", "62704", null);
        result.addIssue(FormatIssueItem.builder()
                .field("administrative_area")
                .severity(Severity.INFO)
                .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                .originalValue("il")
                .correctedValue("IL")
                .build());

        assertEquals(Status.CORRECTED, result.getStatus());
    }

    @Test
    void getStatusReturnsCorrectedWhenOnlyWarningIssues() {
        final var result = new FormatVerificationResult(840, List.of("123 Main St"), "City", "IL", "62704", null);
        result.addIssue(FormatIssueItem.builder()
                .field("postal_code")
                .severity(Severity.WARNING)
                .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                .originalValue("627041234")
                .correctedValue("62704-1234")
                .build());

        assertEquals(Status.CORRECTED, result.getStatus());
    }

    @Test
    void getStatusReturnsInvalidWhenAnyErrorPresent() {
        final var result = new FormatVerificationResult(840, List.of("123 Main St"), "City", "IL", "62704", null);
        result.addIssue(FormatIssueItem.builder()
                .field("administrative_area")
                .severity(Severity.INFO)
                .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                .originalValue("il")
                .correctedValue("IL")
                .build());
        result.addIssue(FormatIssueItem.builder()
                .field("locality")
                .severity(Severity.ERROR)
                .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                .originalValue("")
                .correctedValue("")
                .build());

        assertEquals(Status.INVALID, result.getStatus());
    }

    @Test
    void settersMutateFields() {
        final var result = new FormatVerificationResult(840, List.of(), "Old City", "XX", "00000", null);
        result.setLocality("New City");
        result.setAdministrativeArea("IL");
        result.setPostalCode("62704");
        result.setSubLocality("Downtown");

        assertEquals("New City", result.getLocality());
        assertEquals("IL", result.getAdministrativeArea());
        assertEquals("62704", result.getPostalCode());
        assertEquals("Downtown", result.getSubLocality());
    }

    @Test
    void formatIssueItemCodeConstants() {
        assertEquals(1, FormatIssueItem.CODE_FIELD_REQUIRED);
        assertEquals(2, FormatIssueItem.CODE_FIELD_INVALID);
        assertEquals(3, FormatIssueItem.CODE_FIELD_CORRECTED);
    }
}
