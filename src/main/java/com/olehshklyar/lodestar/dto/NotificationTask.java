package com.olehshklyar.lodestar.dto;

import java.time.Instant;

/**
 * Task payload dispatched to RabbitMQ queues for delivery by channel gateways (e.g., Viber).
 */
public record NotificationTask(
    String taskId,
    String eventId,
    String userId,
    String channel,
    String recipientAddress,
    String regionId,
    String message,
    String severity,
    Instant createdAt
) {}
