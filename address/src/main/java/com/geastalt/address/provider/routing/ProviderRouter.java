/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.provider.routing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.geastalt.address.provider.VerificationProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderRouter {

    private final RoutingConfig routingConfig;

    public List<VerificationProvider> selectProviders(final int countryCode) {
        return routingConfig.getStrategyFor(countryCode).getProviderList();
    }
}
