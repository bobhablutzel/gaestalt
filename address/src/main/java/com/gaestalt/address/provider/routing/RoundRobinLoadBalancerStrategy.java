/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.routing;

import java.util.List;

import com.gaestalt.address.provider.VerificationProvider;


public final class RoundRobinLoadBalancerStrategy extends ListSelectingLoadBalancerStrategy {

    public RoundRobinLoadBalancerStrategy( final List<VerificationProvider> providers ) {
        super( providers );
    }

    public List<VerificationProvider> getProviderList() {
        return providerLists.get( index.getAndUpdate( i -> (i + 1) % providerLists.size()));
    }
}
