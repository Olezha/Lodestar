package com.olehshklyar.lodestar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateRegionMappingRequest(
        @NotBlank(message = "rawTitle must not be blank")
        @Schema(description = "Raw external location title encountered in alert feed", example = "Білоцерківський район")
        String rawTitle,

        @NotBlank(message = "regionId must not be blank")
        @Schema(description = "Target canonical region identifier", example = "KYIV_REGION")
        String regionId
) {}
