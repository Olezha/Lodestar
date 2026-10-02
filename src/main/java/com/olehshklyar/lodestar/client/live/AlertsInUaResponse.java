package com.olehshklyar.lodestar.client.live;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertsInUaResponse(
        @JsonProperty("alerts") List<AlertsInUaAlert> alerts,
        @JsonProperty("disclaimer") String disclaimer
) {
    public AlertsInUaResponse {
        if (alerts == null) {
            alerts = Collections.emptyList();
        }
    }
}
