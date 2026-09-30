package com.olehshklyar.lodestar.dto;

import com.olehshklyar.lodestar.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionRequest(
        @NotBlank(message = "userId must not be blank")
        String userId,

        @NotNull(message = "channel must not be null")
        NotificationChannel channel,

        @NotBlank(message = "recipientAddress must not be blank")
        String recipientAddress,

        @NotBlank(message = "regionId must not be blank")
        String regionId,

        String minSeverity
) {
    public CreateSubscriptionRequest {
        if (minSeverity == null || minSeverity.isBlank()) {
            minSeverity = "INFO";
        }
    }
}
