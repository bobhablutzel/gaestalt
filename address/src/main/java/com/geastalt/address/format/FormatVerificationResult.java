/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.format;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class FormatVerificationResult {

    public enum Status {
        VALID,
        CORRECTED,
        INVALID
    }

    private final int countryCode;
    private final List<String> addressLines;
    @Setter private String locality;
    @Setter private String administrativeArea;
    @Setter private String postalCode;
    @Setter private String subLocality;
    private final List<FormatIssueItem> issues = new ArrayList<>();

    public FormatVerificationResult(final int countryCode, final List<String> addressLines,
                                    final String locality, final String administrativeArea,
                                    final String postalCode, final String subLocality) {
        this.countryCode = countryCode;
        this.addressLines = addressLines;
        this.locality = locality;
        this.administrativeArea = administrativeArea;
        this.postalCode = postalCode;
        this.subLocality = subLocality;
    }

    public void addIssue(final FormatIssueItem issue) {
        issues.add(issue);
    }

    public Status getStatus() {
        if (issues.stream().anyMatch(i -> i.getSeverity() == Severity.ERROR)) {
            return Status.INVALID;
        }
        if (!issues.isEmpty()) {
            return Status.CORRECTED;
        }
        return Status.VALID;
    }

    @Data
    @Builder
    public static class FormatIssueItem {
        public static final int CODE_FIELD_REQUIRED = 1;
        public static final int CODE_FIELD_INVALID = 2;
        public static final int CODE_FIELD_CORRECTED = 3;

        private String field;
        private Severity severity;
        private int code;
        private String originalValue;
        private String correctedValue;
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}
