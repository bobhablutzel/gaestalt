/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.format.gb;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.format.FormatVerificationResult;
import com.gaestalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.gaestalt.address.format.FormatVerificationResult.Severity;
import com.gaestalt.address.format.FormatVerifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;


/**
 * Defines a FormatVerifier specialized for Canada (ISO 3166-1 country code 126).
 * This validates and cleans up the postal code and validates the presence or
 * absence of key line items
 */
@Component
public class GbFormatVerifier implements FormatVerifier {

    // UK postcode: outward code (2-4 chars) + space + inward code (3 chars)
    // Patterns: A9 9AA, A99 9AA, A9A 9AA, AA9 9AA, AA99 9AA, AA9A 9AA
    private static final Pattern UK_POSTCODE = Pattern.compile(
            "^([A-Z]{1,2}\\d[A-Z\\d]?) ?(\\d[A-Z]{2})$"
    );

    @Override
    public int getCountryCode() {
        return CountryCode.GB;
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

        // Required: at least one address line
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

        // Required: locality (post town)
        if (locality == null || locality.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("locality")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        }

        // UK doesn't require administrative_area for postal addresses

        // Postal code validation
        if (postalCode == null || postalCode.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("postal_code")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        } else {
            final var matcher = UK_POSTCODE.matcher(postalCode.toUpperCase().trim());
            if (matcher.matches()) {
                final var rebuilt = matcher.group(1) + " " + matcher.group(2);
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
