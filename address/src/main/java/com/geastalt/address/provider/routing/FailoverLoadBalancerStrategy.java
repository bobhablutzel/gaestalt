/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.routing;

import java.util.List;

import com.geastalt.address.provider.VerificationProvider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;



@RequiredArgsConstructor
public final class FailoverLoadBalancerStrategy implements ProviderLoadBalancerStrategy {

    @Getter
    final List<VerificationProvider> providerList;
}
