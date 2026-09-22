package com.olehshklyar.lodestar.dto;

import java.time.Instant;

/**
 * Immutable DTO representing a raw alert event in the system.
 */
public record AlertEvent(
    String eventId,
    String regionId,
    String eventType,
    String status,
    String severity,
    Instant timestamp
) {}
