package com.olehshklyar.lodestar.client.live;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertsInUaAlert(
        @JsonProperty("id") Long id,
        @JsonProperty("location_title") String locationTitle,
        @JsonProperty("location_type") String locationType,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("finished_at") String finishedAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("alert_type") String alertType,
        @JsonProperty("location_uid") String locationUid
) {}
