package com.olehshklyar.lodestar.dto;

import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.entity.NotificationChannel;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        String userId,
        NotificationChannel channel,
        String recipientAddress,
        String regionId,
        String minSeverity,
        boolean active,
        Instant createdAt
) {
    public static SubscriptionResponse fromEntity(AlertSubscription entity) {
        return new SubscriptionResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getChannel(),
                entity.getRecipientAddress(),
                entity.getRegionId(),
                entity.getMinSeverity(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
