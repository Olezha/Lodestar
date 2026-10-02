package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.dto.AssignLocationRequest;
import com.olehshklyar.lodestar.dto.CanonicalRegionResponse;
import com.olehshklyar.lodestar.dto.CreateCanonicalRegionRequest;
import com.olehshklyar.lodestar.dto.CreateRegionMappingRequest;
import com.olehshklyar.lodestar.dto.RegionMappingResponse;
import com.olehshklyar.lodestar.entity.CanonicalRegion;
import com.olehshklyar.lodestar.entity.RegionMapping;
import com.olehshklyar.lodestar.entity.UnrecognizedLocation;
import com.olehshklyar.lodestar.exception.RegionConflictException;
import com.olehshklyar.lodestar.exception.RegionNotFoundException;
import com.olehshklyar.lodestar.repository.CanonicalRegionRepository;
import com.olehshklyar.lodestar.repository.RegionMappingRepository;
import com.olehshklyar.lodestar.repository.UnrecognizedLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionMappingServiceTest {

    @Mock
    private CanonicalRegionRepository canonicalRegionRepository;

    @Mock
    private RegionMappingRepository regionMappingRepository;

    @Mock
    private UnrecognizedLocationRepository unrecognizedLocationRepository;

    private RegionMappingService service;

    @BeforeEach
    void setUp() {
        service = new RegionMappingService(canonicalRegionRepository, regionMappingRepository, unrecognizedLocationRepository);

        RegionMapping m1 = RegionMapping.builder()
                .id(1L)
                .rawTitle("м. київ")
                .regionId("KYIV_REGION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        RegionMapping m2 = RegionMapping.builder()
                .id(2L)
                .rawTitle("львівська область")
                .regionId("LVIV_REGION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(regionMappingRepository.findAll()).thenReturn(List.of(m1, m2));
        service.loadCache();
    }

    @Test
    @DisplayName("Should resolve known raw titles from in-memory cache")
    void shouldResolveFromCache() {
        assertThat(service.normalize("м. Київ")).isEqualTo("KYIV_REGION");
        assertThat(service.normalize("  ЛЬВІВСЬКА ОБЛАСТЬ  ")).isEqualTo("LVIV_REGION");
    }

    @Test
    @DisplayName("Should handle null and empty titles gracefully")
    void shouldHandleNullAndEmpty() {
        assertThat(service.normalize(null)).isEqualTo("UNKNOWN_REGION");
        assertThat(service.normalize("   ")).isEqualTo("UNKNOWN_REGION");
    }

    @Test
    @DisplayName("Should record unrecognized title and return slugified fallback when not in cache")
    void shouldRecordUnrecognizedAndFallback() {
        when(unrecognizedLocationRepository.findByRawTitle("Полтава ТГ")).thenReturn(Optional.empty());

        String result = service.normalize("Полтава ТГ");

        assertThat(result).isEqualTo("ПОЛТАВА_ТГ_REGION");
        verify(unrecognizedLocationRepository).save(any(UnrecognizedLocation.class));
    }

    @Test
    @DisplayName("Should increment occurrences count if unrecognized title already exists")
    void shouldIncrementCountWhenAlreadyEncountered() {
        UnrecognizedLocation existing = UnrecognizedLocation.builder()
                .id(10L)
                .rawTitle("Unknown Hromada")
                .occurrencesCount(2L)
                .firstSeenAt(Instant.now().minusSeconds(3600))
                .lastSeenAt(Instant.now().minusSeconds(1800))
                .build();

        when(unrecognizedLocationRepository.findByRawTitle("Unknown Hromada")).thenReturn(Optional.of(existing));

        String result = service.normalize("Unknown Hromada");

        assertThat(result).isEqualTo("UNKNOWN_HROMADA_REGION");
        assertThat(existing.getOccurrencesCount()).isEqualTo(3L);
        verify(unrecognizedLocationRepository).save(existing);
    }

    @Test
    @DisplayName("Should create canonical region successfully")
    void shouldCreateCanonicalRegion() {
        CreateCanonicalRegionRequest req = new CreateCanonicalRegionRequest("CHERKASY_REGION", "Черкаська область");
        when(canonicalRegionRepository.existsById("CHERKASY_REGION")).thenReturn(false);

        CanonicalRegion saved = CanonicalRegion.builder()
                .regionId("CHERKASY_REGION")
                .displayName("Черкаська область")
                .createdAt(Instant.now())
                .build();
        when(canonicalRegionRepository.save(any(CanonicalRegion.class))).thenReturn(saved);

        CanonicalRegionResponse resp = service.createCanonicalRegion(req);

        assertThat(resp.regionId()).isEqualTo("CHERKASY_REGION");
        assertThat(resp.displayName()).isEqualTo("Черкаська область");
    }

    @Test
    @DisplayName("Should throw conflict exception when creating existing canonical region")
    void shouldThrowConflictWhenCanonicalRegionExists() {
        CreateCanonicalRegionRequest req = new CreateCanonicalRegionRequest("KYIV_REGION", "Київська");
        when(canonicalRegionRepository.existsById("KYIV_REGION")).thenReturn(true);

        assertThatThrownBy(() -> service.createCanonicalRegion(req))
                .isInstanceOf(RegionConflictException.class)
                .hasMessageContaining("KYIV_REGION");

        verify(canonicalRegionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should add new mapping rule, update cache, and clean up unrecognized record")
    void shouldAddMappingAndUpdateCache() {
        CreateRegionMappingRequest req = new CreateRegionMappingRequest("м. Бровари", "KYIV_REGION");
        when(canonicalRegionRepository.existsById("KYIV_REGION")).thenReturn(true);
        when(regionMappingRepository.existsByRawTitle("м. бровари")).thenReturn(false);

        RegionMapping saved = RegionMapping.builder()
                .id(5L)
                .rawTitle("м. бровари")
                .regionId("KYIV_REGION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(regionMappingRepository.save(any(RegionMapping.class))).thenReturn(saved);

        UnrecognizedLocation unrec = UnrecognizedLocation.builder()
                .id(99L)
                .rawTitle("м. Бровари")
                .occurrencesCount(5L)
                .build();
        when(unrecognizedLocationRepository.findByRawTitle("м. Бровари")).thenReturn(Optional.of(unrec));

        RegionMappingResponse resp = service.addMapping(req);

        assertThat(resp.regionId()).isEqualTo("KYIV_REGION");
        assertThat(resp.rawTitle()).isEqualTo("м. бровари");
        verify(unrecognizedLocationRepository).delete(unrec);

        // Verify cache is updated and resolves immediately without repository call
        assertThat(service.normalize("м. Бровари")).isEqualTo("KYIV_REGION");
    }

    @Test
    @DisplayName("Should throw NotFoundException when adding mapping for non-existent canonical region")
    void shouldThrowWhenCanonicalRegionNotFound() {
        CreateRegionMappingRequest req = new CreateRegionMappingRequest("м. Бровари", "NON_EXISTENT_REGION");
        when(canonicalRegionRepository.existsById("NON_EXISTENT_REGION")).thenReturn(false);

        assertThatThrownBy(() -> service.addMapping(req))
                .isInstanceOf(RegionNotFoundException.class)
                .hasMessageContaining("NON_EXISTENT_REGION");
    }

    @Test
    @DisplayName("Should assign unrecognized location and purge it")
    void shouldAssignUnrecognized() {
        UnrecognizedLocation unrec = UnrecognizedLocation.builder()
                .id(42L)
                .rawTitle("Дрогобицький район")
                .occurrencesCount(3L)
                .build();

        when(unrecognizedLocationRepository.findById(42L)).thenReturn(Optional.of(unrec));
        when(canonicalRegionRepository.existsById("LVIV_REGION")).thenReturn(true);
        when(regionMappingRepository.existsByRawTitle("дрогобицький район")).thenReturn(false);

        RegionMapping saved = RegionMapping.builder()
                .id(10L)
                .rawTitle("дрогобицький район")
                .regionId("LVIV_REGION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(regionMappingRepository.save(any(RegionMapping.class))).thenReturn(saved);

        RegionMappingResponse resp = service.assignUnrecognized(42L, new AssignLocationRequest("LVIV_REGION"));

        assertThat(resp.regionId()).isEqualTo("LVIV_REGION");
        verify(unrecognizedLocationRepository).delete(unrec);
        assertThat(service.normalize("Дрогобицький район")).isEqualTo("LVIV_REGION");
    }

    @Test
    @DisplayName("Should delete mapping and remove from cache")
    void shouldDeleteMapping() {
        RegionMapping m = RegionMapping.builder()
                .id(1L)
                .rawTitle("м. київ")
                .regionId("KYIV_REGION")
                .build();
        when(regionMappingRepository.findById(1L)).thenReturn(Optional.of(m));

        service.deleteMapping(1L);

        verify(regionMappingRepository).delete(m);
        // After deletion, cache shouldn't have it
        when(unrecognizedLocationRepository.findByRawTitle("м. київ")).thenReturn(Optional.empty());
        String fallback = service.normalize("м. київ");
        assertThat(fallback).isEqualTo("М_КИЇВ_REGION");
    }
}
