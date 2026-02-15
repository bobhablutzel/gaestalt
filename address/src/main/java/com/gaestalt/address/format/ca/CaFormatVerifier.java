/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.format.ca;

import com.gaestalt.address.CountryCode;
import com.gaestalt.address.format.FormatVerificationResult;
import com.gaestalt.address.format.FormatVerificationResult.FormatIssueItem;
import com.gaestalt.address.format.FormatVerificationResult.Severity;
import com.gaestalt.address.format.FormatVerifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * Defines a FormatVerifier specialized for Canada (ISO 3166-1 country code 126).
 * This validates and cleans up the postal code and validates the presence or
 * absence of key line items
 */
@Component
public class CaFormatVerifier implements FormatVerifier {

    /**
     * Defines the postal code pattern. There is an optional space that
     * will be introduced if missing.
     */
    private static final Pattern POSTAL_CODE = Pattern.compile("^([A-Z][0-9][A-Z]) ?([0-9][A-Z][0-9])$");

    /**
     * List of valid province codes
     */
    private static final Set<String> VALID_PROVINCE_CODES = Set.of(
            "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT"
    );

    /**
     * Providers the country code for the formatter.
     */
    @Override
    public int getCountryCode() {
        return CountryCode.CA;
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

        // Validate the required fields are present. Check here
        // for at least one non-blank, non-null line
        if (addressLines == null ||
            addressLines.isEmpty() ||
            addressLines.stream().allMatch(l -> l == null || l.isBlank())) {

            // No non-blank, non-null lines, so add format issue that
            // records the problem.
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

        // Check the administrative area - Province in Canadian terms
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
            if (!VALID_PROVINCE_CODES.contains(upper)) {
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

        // Validate the postal code. 
        if (postalCode == null || postalCode.isBlank()) {
            result.addIssue(FormatIssueItem.builder()
                    .field("postal_code")
                    .severity(Severity.ERROR)
                    .code(FormatIssueItem.CODE_FIELD_REQUIRED)
                    .originalValue("")
                    .correctedValue("")
                    .build());
        } else {
            final var matcher = POSTAL_CODE.matcher(postalCode.toUpperCase().trim());
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
