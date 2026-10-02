package com.olehshklyar.lodestar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AssignLocationRequest(
        @NotBlank(message = "regionId must not be blank")
        @Schema(description = "Target canonical region identifier to assign", example = "KYIV_REGION")
        String regionId
) {}
