package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.service.RegionMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Normalizes external location titles (e.g. from alerts.in.ua) into standardized Lodestar region identifiers.
 * Delegates to RegionMappingService for database-backed normalization with fast in-memory cache.
 */
@Component
@RequiredArgsConstructor
public class RegionNormalizer {

    private final RegionMappingService regionMappingService;

    /**
     * Resolves an external location string to a canonical region identifier.
     */
    public String normalize(String rawLocation) {
        return regionMappingService.normalize(rawLocation);
    }
}
