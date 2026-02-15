/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VerificationRequest {

    private int countryCode;
    private List<String> addressLines;
    private String locality;
    private String administrativeArea;
    private String postalCode;
    private String subLocality;
    private String organization;
    private String recipient;
}
