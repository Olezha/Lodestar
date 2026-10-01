package com.olehshklyar.lodestar.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO representing an anomalous pattern detected in the incoming event stream.
 */
public record AnomalyEvent(
        String anomalyId,
        String regionId,
        String anomalyType,
        String description,
        int eventCount,
        long windowDurationSeconds,
        String severity,
        Instant detectedAt,
        List<String> triggeringEventIds
) {}
