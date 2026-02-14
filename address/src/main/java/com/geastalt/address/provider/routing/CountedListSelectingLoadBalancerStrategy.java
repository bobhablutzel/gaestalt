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

import com.geastalt.address.provider.VerificationProvider;

public sealed abstract class CountedListSelectingLoadBalancerStrategy extends ListSelectingLoadBalancerStrategy
        permits CountBasedLoadBalancerStrategy,
        PercentBasedLoadBalancerStrategy {

    protected final List<Integer> values = new ArrayList<>();

    public record Config(VerificationProvider provider, int value) {
    }

    // Constructor - builds the queue of lists to attempt
    public CountedListSelectingLoadBalancerStrategy(final List<Config> configurations) {
        super(configurations.stream().map(Config::provider).toList());

        int sum = 0;
        final var size = configurations.size();
        for (int i = 0; i < size; ++i) {
            sum += configurations.get(i).value;
            values.add(sum);
        }
    }

    public void reset() {
        index.set(0);
    }
}
