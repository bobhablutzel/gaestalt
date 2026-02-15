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

public final class CountBasedLoadBalancerStrategy extends CountedListSelectingLoadBalancerStrategy {

    public CountBasedLoadBalancerStrategy( final List<Config> configurations ) {
        super(configurations);
    }

    @Override
    public List<VerificationProvider> getProviderList() {
        final var counter = index.getAndUpdate( i -> (i + 1) % values.getLast());
        final int bucket = (int) values.stream().filter( i -> i <= counter ).count();
        return providerLists.get(bucket);
    }
}
