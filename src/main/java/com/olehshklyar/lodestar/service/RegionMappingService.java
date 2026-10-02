package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.dto.AssignLocationRequest;
import com.olehshklyar.lodestar.dto.CanonicalRegionResponse;
import com.olehshklyar.lodestar.dto.CreateCanonicalRegionRequest;
import com.olehshklyar.lodestar.dto.CreateRegionMappingRequest;
import com.olehshklyar.lodestar.dto.RegionMappingResponse;
import com.olehshklyar.lodestar.dto.UnrecognizedLocationResponse;
import com.olehshklyar.lodestar.entity.CanonicalRegion;
import com.olehshklyar.lodestar.entity.RegionMapping;
import com.olehshklyar.lodestar.entity.UnrecognizedLocation;
import com.olehshklyar.lodestar.exception.RegionConflictException;
import com.olehshklyar.lodestar.exception.RegionNotFoundException;
import com.olehshklyar.lodestar.repository.CanonicalRegionRepository;
import com.olehshklyar.lodestar.repository.RegionMappingRepository;
import com.olehshklyar.lodestar.repository.UnrecognizedLocationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing canonical regions, raw title mappings, and unrecognized location tracking.
 * Maintains an in-memory thread-safe cache for sub-millisecond normalization during alert ingestion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionMappingService {

    private final CanonicalRegionRepository canonicalRegionRepository;
    private final RegionMappingRepository regionMappingRepository;
    private final UnrecognizedLocationRepository unrecognizedLocationRepository;

    private final Map<String, String> lookupCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCache() {
        refreshCache();
    }

    public synchronized void refreshCache() {
        lookupCache.clear();
        List<RegionMapping> mappings = regionMappingRepository.findAll();
        for (RegionMapping mapping : mappings) {
            lookupCache.put(mapping.getRawTitle().trim().toLowerCase(), mapping.getRegionId());
        }
        log.info("Loaded {} region mappings into normalization cache", lookupCache.size());
    }

    /**
     * Normalizes a raw location title to a canonical region identifier.
     * If unmapped, records the encounter in unrecognized_locations and produces a fallback slug.
     */
    public String normalize(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return "UNKNOWN_REGION";
        }

        String cleaned = rawLocation.trim().toLowerCase();
        String matched = lookupCache.get(cleaned);
        if (matched != null) {
            return matched;
        }

        // Encountered unknown location: record for administrative review
        recordUnrecognizedLocation(rawLocation.trim());

        // Slug fallback so ingestion continues uninterrupted
        String slug = rawLocation.trim().toUpperCase()
                .replaceAll("[^A-Z0-9А-ЯІЇЄҐ]+", "_")
                .replaceAll("^_+|_+$", "");

        return slug.endsWith("_REGION") ? slug : slug + "_REGION";
    }

    private void recordUnrecognizedLocation(String rawTitle) {
        try {
            unrecognizedLocationRepository.findByRawTitle(rawTitle).ifPresentOrElse(
                    existing -> {
                        existing.setOccurrencesCount(existing.getOccurrencesCount() + 1);
                        existing.setLastSeenAt(Instant.now());
                        unrecognizedLocationRepository.save(existing);
                    },
                    () -> {
                        UnrecognizedLocation fresh = UnrecognizedLocation.builder()
                                .rawTitle(rawTitle)
                                .occurrencesCount(1L)
                                .firstSeenAt(Instant.now())
                                .lastSeenAt(Instant.now())
                                .build();
                        unrecognizedLocationRepository.save(fresh);
                    }
            );
        } catch (Exception ex) {
            log.debug("Concurrent insert/update for unrecognized location '{}': {}", rawTitle, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<CanonicalRegionResponse> getAllCanonicalRegions() {
        return canonicalRegionRepository.findAll(Sort.by(Sort.Direction.ASC, "regionId"))
                .stream()
                .map(r -> new CanonicalRegionResponse(r.getRegionId(), r.getDisplayName(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CanonicalRegionResponse createCanonicalRegion(CreateCanonicalRegionRequest request) {
        if (canonicalRegionRepository.existsById(request.regionId())) {
            throw new RegionConflictException("Canonical region with ID '" + request.regionId() + "' already exists");
        }

        CanonicalRegion region = CanonicalRegion.builder()
                .regionId(request.regionId().trim().toUpperCase())
                .displayName(request.displayName().trim())
                .build();

        CanonicalRegion saved = canonicalRegionRepository.save(region);
        log.info("Created canonical region: {}", saved.getRegionId());
        return new CanonicalRegionResponse(saved.getRegionId(), saved.getDisplayName(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<RegionMappingResponse> getAllMappings() {
        return regionMappingRepository.findAll(Sort.by(Sort.Direction.ASC, "rawTitle"))
                .stream()
                .map(m -> new RegionMappingResponse(m.getId(), m.getRawTitle(), m.getRegionId(), m.getCreatedAt(), m.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public RegionMappingResponse addMapping(CreateRegionMappingRequest request) {
        return registerMapping(request.rawTitle(), request.regionId());
    }

    private RegionMappingResponse registerMapping(String rawTitle, String targetRegionId) {
        String normalizedRegionId = targetRegionId.trim().toUpperCase();
        if (!canonicalRegionRepository.existsById(normalizedRegionId)) {
            throw new RegionNotFoundException("Canonical region '" + normalizedRegionId + "' not found");
        }

        String normalizedTitle = rawTitle.trim().toLowerCase();
        if (regionMappingRepository.existsByRawTitle(normalizedTitle)) {
            throw new RegionConflictException("Mapping for title '" + rawTitle + "' already exists");
        }

        RegionMapping mapping = RegionMapping.builder()
                .rawTitle(normalizedTitle)
                .regionId(normalizedRegionId)
                .build();

        RegionMapping saved = regionMappingRepository.save(mapping);
        lookupCache.put(normalizedTitle, normalizedRegionId);

        // Remove from unrecognized locations if it was previously recorded
        unrecognizedLocationRepository.findByRawTitle(rawTitle.trim())
                .ifPresent(unrecognizedLocationRepository::delete);

        log.info("Registered region mapping: '{}' -> '{}'", normalizedTitle, normalizedRegionId);
        return new RegionMappingResponse(saved.getId(), saved.getRawTitle(), saved.getRegionId(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public void deleteMapping(Long id) {
        RegionMapping mapping = regionMappingRepository.findById(id)
                .orElseThrow(() -> new RegionNotFoundException("Mapping with ID " + id + " not found"));

        regionMappingRepository.delete(mapping);
        lookupCache.remove(mapping.getRawTitle().toLowerCase());
        log.info("Deleted region mapping ID {}: '{}'", id, mapping.getRawTitle());
    }

    @Transactional(readOnly = true)
    public List<UnrecognizedLocationResponse> getAllUnrecognizedLocations() {
        return unrecognizedLocationRepository.findAll(Sort.by(Sort.Direction.DESC, "occurrencesCount"))
                .stream()
                .map(u -> new UnrecognizedLocationResponse(
                        u.getId(),
                        u.getRawTitle(),
                        u.getOccurrencesCount(),
                        u.getFirstSeenAt(),
                        u.getLastSeenAt()
                ))
                .toList();
    }

    @Transactional
    public RegionMappingResponse assignUnrecognized(Long unrecognizedId, AssignLocationRequest request) {
        UnrecognizedLocation unrecognized = unrecognizedLocationRepository.findById(unrecognizedId)
                .orElseThrow(() -> new RegionNotFoundException("Unrecognized location with ID " + unrecognizedId + " not found"));

        String rawTitle = unrecognized.getRawTitle();
        RegionMappingResponse mapping = registerMapping(rawTitle, request.regionId());
        unrecognizedLocationRepository.delete(unrecognized);
        log.info("Assigned unrecognized location ID {} ('{}') to '{}'", unrecognizedId, rawTitle, request.regionId());
        return mapping;
    }
}
