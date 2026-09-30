package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.discord.DiscordClient;
import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RabbitMQ consumer that listens to notifications.discord queue,
 * ensures idempotency using Redis, and delivers notifications via DiscordClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordNotificationConsumer {

    private static final Duration DEDUP_TTL = Duration.ofMinutes(15);

    private final IdempotencyService idempotencyService;
    private final DiscordClient discordClient;

    @RabbitListener(queues = RabbitMQConfig.DISCORD_QUEUE)
    public void consumeNotificationTask(NotificationTask task) {
        log.info("Received NotificationTask [id={}] for user [{}] via channel [{}]",
                task.taskId(), task.userId(), task.channel());

        // 1. Idempotency check via Redis
        boolean isUnique = idempotencyService.acquireLock(task.taskId(), DEDUP_TTL);
        if (!isUnique) {
            log.warn("Duplicate NotificationTask [id={}] detected in Redis, skipping Discord delivery", task.taskId());
            return;
        }

        // 2. Dispatch to Discord Webhook
        discordClient.sendMessage(task.recipientAddress(), task.message());
        log.info("Successfully processed NotificationTask [id={}] for user [{}] via Discord", task.taskId(), task.userId());
    }
}
