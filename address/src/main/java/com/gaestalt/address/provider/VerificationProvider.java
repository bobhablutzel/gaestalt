/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider;

import java.util.List;

public interface VerificationProvider {

    String getProviderId();

    String getDisplayName();

    List<Integer> getSupportedCountries();

    boolean isEnabled();

    VerificationResult verify(final VerificationRequest request);
}
