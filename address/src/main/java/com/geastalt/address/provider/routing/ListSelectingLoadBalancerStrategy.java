/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.geastalt.address.provider.VerificationProvider;

public sealed abstract class ListSelectingLoadBalancerStrategy implements ProviderLoadBalancerStrategy
        permits CountedListSelectingLoadBalancerStrategy,
        RoundRobinLoadBalancerStrategy {

    protected final List<List<VerificationProvider>> providerLists = new ArrayList<>();
    protected final AtomicInteger index = new AtomicInteger();

    // Constructor - builds the queue of lists to attempt
    public ListSelectingLoadBalancerStrategy(final List<VerificationProvider> providerList) {

        final var size = providerList.size();
        for (int i = 0; i < size; ++i) {

            final var newList = new ArrayList<>(providerList.subList(i, size));
            newList.addAll(providerList.subList(0, i));
            providerLists.add(newList);
        }
    }

}
