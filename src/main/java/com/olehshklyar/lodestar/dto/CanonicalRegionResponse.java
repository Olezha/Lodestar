package com.olehshklyar.lodestar.dto;

import java.time.Instant;

public record CanonicalRegionResponse(
        String regionId,
        String displayName,
        Instant createdAt
) {}
