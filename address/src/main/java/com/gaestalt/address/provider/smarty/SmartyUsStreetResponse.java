/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.smarty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartyUsStreetResponse {

    @JsonProperty("delivery_line_1")
    private String deliveryLine1;

    @JsonProperty("delivery_line_2")
    private String deliveryLine2;

    @JsonProperty("last_line")
    private String lastLine;

    private Components components;
    private Metadata metadata;
    private Analysis analysis;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Components {

        @JsonProperty("city_name")
        private String cityName;

        @JsonProperty("state_abbreviation")
        private String stateAbbreviation;

        private String zipcode;

        @JsonProperty("plus4_code")
        private String plus4Code;

        @JsonProperty("primary_number")
        private String primaryNumber;

        @JsonProperty("street_name")
        private String streetName;

        @JsonProperty("street_suffix")
        private String streetSuffix;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {

        @JsonProperty("county_name")
        private String countyName;

        private String latitude;
        private String longitude;

        @JsonProperty("record_type")
        private String recordType;

        private String rdi;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Analysis {

        @JsonProperty("dpv_match_code")
        private String dpvMatchCode;

        @JsonProperty("dpv_footnotes")
        private String dpvFootnotes;

        private String active;
    }
}
