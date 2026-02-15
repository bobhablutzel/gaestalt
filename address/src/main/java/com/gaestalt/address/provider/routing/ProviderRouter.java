/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.routing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gaestalt.address.provider.VerificationProvider;

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
