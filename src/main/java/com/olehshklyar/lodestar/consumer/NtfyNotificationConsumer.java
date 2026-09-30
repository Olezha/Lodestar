package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.ntfy.NtfyClient;
import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RabbitMQ consumer that listens to notifications.ntfy queue,
 * ensures idempotency using Redis, and delivers push notifications via NtfyClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NtfyNotificationConsumer {

    private static final Duration DEDUP_TTL = Duration.ofMinutes(15);

    private final IdempotencyService idempotencyService;
    private final NtfyClient ntfyClient;

    @RabbitListener(queues = RabbitMQConfig.NTFY_QUEUE)
    public void consumeNotificationTask(NotificationTask task) {
        log.info("Received NotificationTask [id={}] for user [{}] via channel [{}]",
                task.taskId(), task.userId(), task.channel());

        // 1. Idempotency check via Redis
        boolean isUnique = idempotencyService.acquireLock(task.taskId(), DEDUP_TTL);
        if (!isUnique) {
            log.warn("Duplicate NotificationTask [id={}] detected in Redis, skipping ntfy delivery", task.taskId());
            return;
        }

        // 2. Map severity to ntfy priority
        String priority = mapSeverityToPriority(task.severity());
        String title = String.format("Alert: %s", task.regionId() != null ? task.regionId() : "National");

        // 3. Dispatch to ntfy.sh
        ntfyClient.sendMessage(task.recipientAddress(), task.message(), title, priority);
        log.info("Successfully processed NotificationTask [id={}] for user [{}] via ntfy", task.taskId(), task.userId());
    }

    private String mapSeverityToPriority(String severity) {
        if (severity == null) {
            return "default";
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "urgent";
            case "WARNING", "HIGH" -> "high";
            case "LOW" -> "low";
            default -> "default";
        };
    }
}
