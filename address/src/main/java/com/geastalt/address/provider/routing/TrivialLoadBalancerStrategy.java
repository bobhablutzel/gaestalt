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


public final class TrivialLoadBalancerStrategy implements ProviderLoadBalancerStrategy {

    @Getter
    private final List<VerificationProvider> providerList;

    public TrivialLoadBalancerStrategy( final VerificationProvider provider ) {
        providerList = List.of(provider );
    }
}
