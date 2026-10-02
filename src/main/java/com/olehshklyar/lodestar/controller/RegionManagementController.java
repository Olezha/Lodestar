package com.olehshklyar.lodestar.controller;

import com.olehshklyar.lodestar.dto.AssignLocationRequest;
import com.olehshklyar.lodestar.dto.CanonicalRegionResponse;
import com.olehshklyar.lodestar.dto.CreateCanonicalRegionRequest;
import com.olehshklyar.lodestar.dto.CreateRegionMappingRequest;
import com.olehshklyar.lodestar.dto.RegionMappingResponse;
import com.olehshklyar.lodestar.dto.UnrecognizedLocationResponse;
import com.olehshklyar.lodestar.service.RegionMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
@Tag(name = "Region Management API", description = "Endpoints for canonical regions, normalization mappings, and unrecognized location discovery")
public class RegionManagementController {

    private final RegionMappingService regionMappingService;

    @GetMapping("/canonical")
    @Operation(summary = "List all canonical regions")
    public ResponseEntity<List<CanonicalRegionResponse>> listCanonicalRegions() {
        return ResponseEntity.ok(regionMappingService.getAllCanonicalRegions());
    }

    @PostMapping("/canonical")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new canonical region")
    public ResponseEntity<CanonicalRegionResponse> createCanonicalRegion(
            @Valid @RequestBody CreateCanonicalRegionRequest request
    ) {
        CanonicalRegionResponse created = regionMappingService.createCanonicalRegion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mappings")
    @Operation(summary = "List all active region normalization mappings")
    public ResponseEntity<List<RegionMappingResponse>> listMappings() {
        return ResponseEntity.ok(regionMappingService.getAllMappings());
    }

    @PostMapping("/mappings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new title normalization mapping rule")
    public ResponseEntity<RegionMappingResponse> addMapping(
            @Valid @RequestBody CreateRegionMappingRequest request
    ) {
        RegionMappingResponse created = regionMappingService.addMapping(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an existing normalization mapping rule")
    public ResponseEntity<Void> deleteMapping(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID of the mapping to delete", example = "1")
            @PathVariable Long id
    ) {
        regionMappingService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unrecognized")
    @Operation(summary = "List unrecognized location titles encountered during alert ingestion")
    public ResponseEntity<List<UnrecognizedLocationResponse>> listUnrecognizedLocations() {
        return ResponseEntity.ok(regionMappingService.getAllUnrecognizedLocations());
    }

    @PostMapping("/unrecognized/{id}/assign")
    @Operation(summary = "Assign an unrecognized location to a canonical region and register mapping rule")
    public ResponseEntity<RegionMappingResponse> assignUnrecognized(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID of the unrecognized location to map", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AssignLocationRequest request
    ) {
        RegionMappingResponse mapping = regionMappingService.assignUnrecognized(id, request);
        return ResponseEntity.ok(mapping);
    }

    @PostMapping("/refresh-cache")
    @Operation(summary = "Force reload of the normalization in-memory cache from database")
    public ResponseEntity<Void> refreshCache() {
        regionMappingService.refreshCache();
        return ResponseEntity.ok().build();
    }
}
