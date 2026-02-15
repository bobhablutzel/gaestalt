/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.smarty;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "smarty")
public class SmartyConfig {

    private String authId;
    private String authToken;
    private String usStreetBaseUrl = "https://us-street.api.smarty.com";
    private String internationalBaseUrl = "https://international-street.api.smarty.com";
}
