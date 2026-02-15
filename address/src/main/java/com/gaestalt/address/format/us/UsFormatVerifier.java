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
import com.gaestalt.address.format.FormatVerifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UsFormatVerifier implements FormatVerifier {

    // US ZIP code: 5 digits, optionally followed by dash and 4 more digits
    private static final Pattern ZIP_CODE = Pattern.compile("^(\\d{5})(?:-?(\\d{4}))?$");

    private static final Set<String> VALID_STATE_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "PR", "GU", "VI", "AS", "MP", "UM"
    );

    @Override
    public int getCountryCode() {
        return CountryCode.US;
    }

    /** {@inheritDoc} */
    @Override
    public FormatVerificationResult verify(final int countryCode,
                                           final List<String> addressLines,
                                           final String locality,
                                           final String administrativeArea,
                                           final String postalCode,
                                           final String subLocality) {
        final var result = new FormatVerificationResult(countryCode, addressLines, locality,
                administrativeArea, postalCode, subLocality);

        // Required fields
        if (addressLines == null || addressLines.isEmpty()
                || addressLines.stream().allMatch(l -> l == null || l.isBlank())) {
            result.addIssue(FormatIssueItem.builder()
                    .field("address_lines")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        }

        if (locality == null || locality.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("locality")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        }

        if (administrativeArea == null || administrativeArea.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("administrative_area")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        } else {
            final var upper = administrativeArea.toUpperCase().trim();
            if (!VALID_STATE_CODES.contains(upper)) {
                result.addIssue(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.ERROR)
                        .code(FormatIssueItem.CODE_FIELD_INVALID)
                        .originalValue(administrativeArea)
                        .correctedValue("")
                        .build());
            } else if (!upper.equals(administrativeArea)) {
                result.setAdministrativeArea(upper);
                result.addIssue(FormatIssueItem.builder()
                        .field("administrative_area")
                        .severity(Severity.INFO)
                        .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                        .originalValue(administrativeArea)
                        .correctedValue(upper)
                        .build());
            }
        }

        if (postalCode == null || postalCode.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("postal_code")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        } else {
            final var matcher = ZIP_CODE.matcher(postalCode.trim());
            if (matcher.matches()) {
                final var rebuilt = matcher.group(2) != null
                        ? matcher.group(1) + "-" + matcher.group(2)
                        : matcher.group(1);
                if (!rebuilt.equals(postalCode)) {
                    result.setPostalCode(rebuilt);
                    result.addIssue(FormatIssueItem.builder()
                            .field("postal_code")
                            .severity(Severity.INFO)
                            .code(FormatIssueItem.CODE_FIELD_CORRECTED)
                            .originalValue(postalCode)
                            .correctedValue(rebuilt)
                            .build());
                }
            } else {
                result.addIssue(FormatIssueItem.builder()
                        .field("postal_code")
                        .severity(Severity.ERROR)
                        .code(FormatIssueItem.CODE_FIELD_INVALID)
                        .originalValue(postalCode)
                        .correctedValue("")
                        .build());
            }
        }

        return result;
    }
}
