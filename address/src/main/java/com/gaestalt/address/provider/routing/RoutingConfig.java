/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.routing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gaestalt.address.provider.VerificationProvider;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "address.providers")
public class RoutingConfig {

    /**
     * List of the verification providers - injected by Spring
     */
    private final List<VerificationProvider> verificationProviders;

    /**
     * The routing configuration represents the unconverted configuration
     * defined by the application configuration. This is
     * converted in the post construct initializer to the
     * set of routing strategies
     */
    @Getter
    @Setter
    private Map<Integer, Map<String, String>> routingConfiguration;

    /**
     * The set of routing strategies is populated during the post-construct
     * initializer and represents the set of routing strategies for each
     * country code (keyed by the ISO 3166-1 numeric country code)
     */
    @Getter
    private Map<Integer, ProviderLoadBalancerStrategy> routingTable;

    public RoutingConfig(final List<VerificationProvider> verificationProviders) {
        this.verificationProviders = verificationProviders;
    }

    @PostConstruct
    public void init() {

        final var configurationProcessor = new ConfigurationProcessor(verificationProviders);

        // Parse the configurations
        routingTable = configurationProcessor.parse(routingConfiguration);
    }


    public ProviderLoadBalancerStrategy getStrategyFor( final int countryCode ) {
        return routingTable.getOrDefault(countryCode, new NullLoadBalancerStrategy());
    }

    /**
     * Internal class for parsing the configuration processors
     */
    @Slf4j
    private final static class ConfigurationProcessor {

        // The map of verification providers by id
        private final Map<String, VerificationProvider> providerMap;

        /**
         * Constructor
         *
         * @param providers The list of verification providers
         */
        public ConfigurationProcessor(final List<VerificationProvider> providers) {

            // Build the provider map
            providerMap = providers.stream().collect(Collectors.toMap(VerificationProvider::getProviderId, v -> v));
        }

        public Map<Integer, ProviderLoadBalancerStrategy> parse(final Map<Integer, Map<String, String>> configuration) {
            return configuration
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            this::parseConfiguration));
        }

        private ProviderLoadBalancerStrategy parseConfiguration(final Map.Entry<Integer, Map<String, String>> entry) {

            // Retrieve the configuration object
            final var configuration = entry.getValue();

            // Start by seeing if this is a trivial provider configuration
            if (configuration.containsKey("provider")) {
                final var providerName = configuration.get("provider");
                return trivialLoadBalancer(providerName);
            } else if (configuration.containsKey("strategy") && configuration.containsKey("specification")) {

                final var strategy = configuration.get("strategy");
                final var specification = configuration.get("specification");
                final var specList = Arrays.asList(specification.split(","));

                // Specification has to have multiple values
                if (specList.size() == 1) {

                    log.warn("Specification did not provide list of value for {}", entry.getKey());
                    return new NullLoadBalancerStrategy();
                }

                return strategyBasedLoadBalancer(strategy, specList);

            } else {
                log.warn("Configuration must supply provider or strategy");
                return new NullLoadBalancerStrategy();
            }
        }

        private ProviderLoadBalancerStrategy strategyBasedLoadBalancer(final String strategy,
                final List<String> patterns) {
            return switch (strategy) {
                case "failover" -> providerListBuilder(patterns, FailoverLoadBalancerStrategy::new);
                case "round_robin" -> providerListBuilder(patterns, RoundRobinLoadBalancerStrategy::new );
                case "counter" -> counter(patterns);
                case "percentage" -> percentage(patterns);
                default -> invalidStrategy(strategy);
            };
        }

        private ProviderLoadBalancerStrategy providerListBuilder( final List<String> patterns, 
                                    final Function<List<VerificationProvider>, ProviderLoadBalancerStrategy> builder ) {

            // Each pattern should be a provider name, so make sure they are all provider
            // names
            if (patterns.stream().allMatch(providerMap::containsKey)) {
                final var providers = patterns.stream().map(providerMap::get).toList();
                return builder.apply(providers);
            } else {

                // Get the ones that don't match
                patterns.stream().filter(k -> !providerMap.containsKey(k))
                        .forEach(p -> log.warn("Provider not found: {}", p));
                return new NullLoadBalancerStrategy();

            }
        }

        private ProviderLoadBalancerStrategy counter(final List<String> patterns) {
            final var configurations = toConfig(patterns);
            return !configurations.isEmpty()
                    ? new CountBasedLoadBalancerStrategy(configurations)
                    : new NullLoadBalancerStrategy();
        }

        private ProviderLoadBalancerStrategy percentage(final List<String> patterns) {
            final var configurations = toConfig(patterns);
            return !configurations.isEmpty()
                    ? new PercentBasedLoadBalancerStrategy(configurations)
                    : new NullLoadBalancerStrategy();
        }

        private List<CountedListSelectingLoadBalancerStrategy.Config> toConfig(final List<String> patterns) {

            // Each pattern should be in the pattern provider:count, except the last
            // which should just be a provider. Parse those here and make sure they all
            // match the pattern.
            final var pattern = Pattern.compile("^(\\w+)(:(\\d+))?$");
            final var matches = patterns.stream().map(pattern::matcher).toList();

            if (!matches.stream().allMatch(Matcher::matches)) {
                log.warn("Syntax error in strategy specification");
                return List.of();
            }

            // If any entry lacks an explicit count, it must be the last one
            final var uncounted = matches.stream().filter(m -> m.group(3) == null).toList();
            if (uncounted.size() > 1) {
                log.warn("Strategy specification has multiple entries without counts");
                return List.of();
            }
            if (uncounted.size() == 1 && !uncounted.getFirst().equals(matches.getLast())) {
                log.warn("Entry without count must be the last entry");
                return List.of();
            }

            // The patterns match and the only one that doesn't specify a value is the last
            // one.
            // Now make sure that the provider names all match known providers.
            if (!matches.stream().allMatch(m -> providerMap.containsKey(m.group(1)))) {
                log.warn("Strategy specifies invalid provider name");
                return List.of();
            }

            // All good - make the configs!
            return matches.stream()
                    .map(m -> new CountedListSelectingLoadBalancerStrategy.Config(
                            providerMap.get(m.group(1)),
                            m.group(3) != null ? Integer.parseInt(m.group(3)) : 1))
                    .toList();
        }

        private ProviderLoadBalancerStrategy trivialLoadBalancer(final String providerName) {
            return providerMap.containsKey(providerName)
                    ? new TrivialLoadBalancerStrategy(providerMap.get(providerName))
                    : missingProvider(providerName);
        }

        private ProviderLoadBalancerStrategy missingProvider(final String provider) {
            log.warn("Provider {} was not found", provider);
            return new NullLoadBalancerStrategy();
        }

        private ProviderLoadBalancerStrategy invalidStrategy(final String strategy) {
            log.warn("Invalid strategy {}", strategy);
            return new NullLoadBalancerStrategy();
        }

    }

}
