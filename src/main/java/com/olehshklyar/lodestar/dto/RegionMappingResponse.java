package com.olehshklyar.lodestar.dto;

import java.time.Instant;

public record RegionMappingResponse(
        Long id,
        String rawTitle,
        String regionId,
        Instant createdAt,
        Instant updatedAt
) {}
