package com.olehshklyar.lodestar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCanonicalRegionRequest(
        @NotBlank(message = "regionId must not be blank")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "regionId must consist of uppercase alphanumeric characters and underscores")
        @Schema(description = "Standard uppercase region identifier", example = "CHERKASY_REGION")
        String regionId,

        @NotBlank(message = "displayName must not be blank")
        @Schema(description = "Human-readable official region display name", example = "Черкаська область")
        String displayName
) {}
