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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PercentBasedLoadBalancerStrategy extends CountedListSelectingLoadBalancerStrategy {

    public PercentBasedLoadBalancerStrategy( final List<Config> configurations ) {
        super(configurations);
        if (values.getLast() > 100) {
            log.warn( "Percentages add up to {} for percent based routing strategy", values.getLast());
        } else {

            // The last might be below 100, if the count wasn't specified. Make it 100.
            values.removeLast();
            values.addLast(100);
        }
    }

    @Override
    public List<VerificationProvider> getProviderList() {
        final var percent = index.getAndUpdate( i -> (i + 1) % 100);
        final int bucket = (int) values.stream().filter( i -> i <= percent ).count();
        return providerLists.get(bucket);
    }


}
