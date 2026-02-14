/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.routing;

import com.geastalt.address.CountryCode;
import com.geastalt.address.provider.VerificationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderRouterTest {

    private final VerificationProvider usps = mockProvider("usps");
    private final VerificationProvider smarty = mockProvider("smarty");
    private final VerificationProvider other = mockProvider("other");

    private static VerificationProvider mockProvider(final String id) {
        final var provider = mock(VerificationProvider.class);
        when(provider.getProviderId()).thenReturn(id);
        return provider;
    }

    private ProviderRouter createRouter(final List<VerificationProvider> providers,
                                        final Map<Integer, Map<String, String>> config) {
        final var routingConfig = new RoutingConfig(providers);
        routingConfig.setRoutingConfiguration(config);
        routingConfig.init();
        return new ProviderRouter(routingConfig);
    }

    // --- Trivial (single provider) ---

    @Test
    void singleProviderAlwaysSelected() {
        final var router = createRouter(List.of(usps),
                Map.of(CountryCode.US, Map.of("provider", "usps")));

        for (var i = 0; i < 10; i++) {
            final var result = router.selectProviders(CountryCode.US);
            assertEquals(1, result.size());
            assertSame(usps, result.get(0));
        }
    }

    // --- Count-based routing ---

    @Test
    void countBasedDistributesOverFullCycle() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "counter", "specification", "usps:3,smarty:2")));

        // Cycle length = 5 (3 + 2)
        // Positions 0,1,2 → usps first; 3,4 → smarty first
        var uspsFirst = 0;
        var smartyFirst = 0;
        for (var i = 0; i < 5; i++) {
            final var result = router.selectProviders(CountryCode.US);
            assertEquals(2, result.size());
            if (result.get(0) == usps) {
                uspsFirst++;
                assertSame(smarty, result.get(1));
            } else {
                smartyFirst++;
                assertSame(usps, result.get(1));
            }
        }
        assertEquals(3, uspsFirst);
        assertEquals(2, smartyFirst);
    }

    @Test
    void countBasedLastEntryDefaultsToOne() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "counter", "specification", "usps:4,smarty")));

        // Cycle length = 5 (4 + 1 default)
        var uspsFirst = 0;
        var smartyFirst = 0;
        for (var i = 0; i < 5; i++) {
            final var result = router.selectProviders(CountryCode.US);
            if (result.get(0) == usps) {
                uspsFirst++;
            } else {
                smartyFirst++;
            }
        }
        assertEquals(4, uspsFirst);
        assertEquals(1, smartyFirst);
    }

    // --- Percentage-based routing ---

    @Test
    void percentageBasedDistributesCorrectly() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "percentage", "specification", "usps:70,smarty")));

        var uspsFirst = 0;
        var smartyFirst = 0;
        for (var i = 0; i < 100; i++) {
            final var result = router.selectProviders(CountryCode.US);
            if (result.get(0) == usps) {
                uspsFirst++;
            } else {
                smartyFirst++;
            }
        }
        assertEquals(70, uspsFirst);
        assertEquals(30, smartyFirst);
    }

    @Test
    void percentageBoundaryAtSeventyHitsSecondProvider() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "percentage", "specification", "usps:70,smarty")));

        // Skip first 69 calls (buckets 0-68, all usps)
        for (var i = 0; i < 69; i++) {
            router.selectProviders(CountryCode.US);
        }
        // Bucket 69 → still usps (cumulative 70, 69 < 70)
        assertSame(usps, router.selectProviders(CountryCode.US).get(0));
        // Bucket 70 → smarty (cumulative 70, 70 >= 70)
        assertSame(smarty, router.selectProviders(CountryCode.US).get(0));
    }

    // --- Round-robin ---

    @Test
    void roundRobinRotatesProviders() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "round_robin", "specification", "usps,smarty")));

        // First call → usps first, smarty second
        final var result0 = router.selectProviders(CountryCode.US);
        assertSame(usps, result0.get(0));
        assertSame(smarty, result0.get(1));

        // Second call → smarty first, usps second
        final var result1 = router.selectProviders(CountryCode.US);
        assertSame(smarty, result1.get(0));
        assertSame(usps, result1.get(1));
    }

    // --- Failover ---

    @Test
    void failoverAlwaysReturnsSameOrder() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "failover", "specification", "usps,smarty")));

        for (var i = 0; i < 10; i++) {
            final var result = router.selectProviders(CountryCode.US);
            assertEquals(List.of(usps, smarty), result);
        }
    }

    @Test
    void failoverThreeProvidersPreservesOrder() {
        final var router = createRouter(List.of(usps, smarty, other),
                Map.of(CountryCode.US, Map.of("strategy", "failover", "specification", "usps,smarty,other")));

        for (var i = 0; i < 10; i++) {
            assertEquals(List.of(usps, smarty, other), router.selectProviders(CountryCode.US));
        }
    }

    @Test
    void failoverReversedOrder() {
        final var router = createRouter(List.of(usps, smarty),
                Map.of(CountryCode.US, Map.of("strategy", "failover", "specification", "smarty,usps")));

        final var result = router.selectProviders(CountryCode.US);
        assertEquals(List.of(smarty, usps), result);
    }

    // --- Edge cases ---

    @Test
    void unknownCountryReturnsEmptyList() {
        final var router = createRouter(List.of(usps),
                Map.of(CountryCode.US, Map.of("provider", "usps")));

        assertTrue(router.selectProviders(999).isEmpty());
    }

    @Test
    void emptyConfigReturnsEmptyList() {
        final var router = createRouter(List.of(usps), Map.of());

        assertTrue(router.selectProviders(CountryCode.US).isEmpty());
    }

    // --- Fallback order ---

    @Test
    void fallbackOrderSelectedFirstOthersFollowInConfigOrder() {
        final var router = createRouter(List.of(usps, smarty, other),
                Map.of(CountryCode.US, Map.of("strategy", "counter", "specification", "usps:1,smarty:1,other")));

        // Cycle length = 3. Position 0 → usps selected
        final var result0 = router.selectProviders(CountryCode.US);
        assertEquals(List.of(usps, smarty, other), result0);

        // Position 1 → smarty selected
        final var result1 = router.selectProviders(CountryCode.US);
        assertEquals(List.of(smarty, other, usps), result1);

        // Position 2 → other selected
        final var result2 = router.selectProviders(CountryCode.US);
        assertEquals(List.of(other, usps, smarty), result2);
    }

    // --- Strategy type verification ---

    @Test
    void trivialConfigProducesTrivialStrategy() {
        final var config = new RoutingConfig(List.of(usps));
        config.setRoutingConfiguration(Map.of(CountryCode.US, Map.of("provider", "usps")));
        config.init();

        assertInstanceOf(TrivialLoadBalancerStrategy.class, config.getRoutingTable().get(CountryCode.US));
    }

    @Test
    void counterConfigProducesCountBasedStrategy() {
        final var config = new RoutingConfig(List.of(usps, smarty));
        config.setRoutingConfiguration(Map.of(CountryCode.US, Map.of("strategy", "counter", "specification", "usps:3,smarty")));
        config.init();

        assertInstanceOf(CountBasedLoadBalancerStrategy.class, config.getRoutingTable().get(CountryCode.US));
    }

    @Test
    void percentageConfigProducesPercentBasedStrategy() {
        final var config = new RoutingConfig(List.of(usps, smarty));
        config.setRoutingConfiguration(Map.of(CountryCode.US, Map.of("strategy", "percentage", "specification", "usps:70,smarty")));
        config.init();

        assertInstanceOf(PercentBasedLoadBalancerStrategy.class, config.getRoutingTable().get(CountryCode.US));
    }

    @Test
    void roundRobinConfigProducesRoundRobinStrategy() {
        final var config = new RoutingConfig(List.of(usps, smarty));
        config.setRoutingConfiguration(Map.of(CountryCode.US, Map.of("strategy", "round_robin", "specification", "usps,smarty")));
        config.init();

        assertInstanceOf(RoundRobinLoadBalancerStrategy.class, config.getRoutingTable().get(CountryCode.US));
    }

    @Test
    void failoverConfigProducesFailoverStrategy() {
        final var config = new RoutingConfig(List.of(usps, smarty));
        config.setRoutingConfiguration(Map.of(CountryCode.US, Map.of("strategy", "failover", "specification", "usps,smarty")));
        config.init();

        assertInstanceOf(FailoverLoadBalancerStrategy.class, config.getRoutingTable().get(CountryCode.US));
    }
}
