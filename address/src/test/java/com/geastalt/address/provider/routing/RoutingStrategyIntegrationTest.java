/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.routing;

import com.geastalt.address.CountryCode;
import com.geastalt.address.provider.VerificationRequest;
import com.geastalt.address.provider.VerificationResult;
import com.geastalt.address.service.AddressVerificationService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"server.port=0", "grpc.server.port=0"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "USPS_CLIENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SMARTY_AUTH_ID", matches = ".+")
class RoutingStrategyIntegrationTest {

    private static final VerificationRequest US_ADDRESS = VerificationRequest.builder()
            .countryCode(CountryCode.US)
            .addressLines(List.of("1600 Pennsylvania Ave NW"))
            .locality("Washington")
            .administrativeArea("DC")
            .postalCode("20500")
            .build();

    @Autowired
    private RoutingConfig routingConfig;

    @Autowired
    private AddressVerificationService verificationService;

    // --- Trivial strategy ---

    @Test
    @Order(1)
    void trivialUsps() {
        reconfigure(Map.of(CountryCode.US, Map.of("provider", "usps")));

        final var result = verify();
        assertEquals("usps", result.providerId());
    }

    @Test
    @Order(2)
    void trivialSmarty() {
        reconfigure(Map.of(CountryCode.US, Map.of("provider", "smarty")));

        final var result = verify();
        assertEquals("smarty", result.providerId());
    }

    // --- Round-robin strategy ---

    @Test
    @Order(3)
    void roundRobin() {
        reconfigure(Map.of(CountryCode.US, Map.of(
                "strategy", "round_robin",
                "specification", "usps,smarty")));

        assertEquals("usps", verify().providerId());
        assertEquals("smarty", verify().providerId());
        assertEquals("usps", verify().providerId());
        assertEquals("smarty", verify().providerId());
    }

    // --- Counter-based strategy ---

    @Test
    @Order(4)
    void counterBased() {
        reconfigure(Map.of(CountryCode.US, Map.of(
                "strategy", "counter",
                "specification", "usps:2,smarty")));

        assertEquals("usps", verify().providerId());
        assertEquals("usps", verify().providerId());
        assertEquals("smarty", verify().providerId());
    }

    // --- Percentage-based strategy ---

    @Test
    @Order(5)
    void percentageBased() {
        reconfigure(Map.of(CountryCode.US, Map.of(
                "strategy", "percentage",
                "specification", "usps:1,smarty")));

        // Bucket 0 < cumulative 1 → usps; bucket 1 >= cumulative 1 → smarty
        assertEquals("usps", verify().providerId());
        assertEquals("smarty", verify().providerId());
    }

    // --- Helpers ---

    private void reconfigure(final Map<Integer, Map<String, String>> config) {
        routingConfig.setRoutingConfiguration(config);
        routingConfig.init();
    }

    private AddressVerificationService.Result verify() {
        final var result = verificationService.verifyWithRouting(US_ADDRESS);
        assertTrue(result.isPresent(), "Expected a verification result");
        assertNotEquals(VerificationResult.Status.PROVIDER_ERROR, result.get().verificationResult().getStatus(),
                "Provider " + result.get().providerId() + " returned PROVIDER_ERROR");
        return result.get();
    }
}
