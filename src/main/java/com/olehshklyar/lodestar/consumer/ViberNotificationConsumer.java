package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.viber.ViberClient;
import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RabbitMQ consumer that listens to the notifications.viber queue,
 * ensures idempotency using Redis, and delivers notifications via ViberClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViberNotificationConsumer {

    private static final Duration DEDUP_TTL = Duration.ofMinutes(15);

    private final IdempotencyService idempotencyService;
    private final ViberClient viberClient;

    @RabbitListener(queues = RabbitMQConfig.VIBER_QUEUE)
    public void consumeNotificationTask(NotificationTask task) {
        log.info("Received NotificationTask [id={}] for user [{}] via channel [{}]",
                task.taskId(), task.userId(), task.channel());

        // 1. Idempotency check via Redis
        boolean isUnique = idempotencyService.acquireLock(task.taskId(), DEDUP_TTL);
        if (!isUnique) {
            log.warn("Duplicate NotificationTask [id={}] detected in Redis, skipping Viber delivery", task.taskId());
            return;
        }

        // 2. Dispatch to Viber API
        viberClient.sendMessage(task.recipientAddress(), task.message());
        log.info("Successfully processed NotificationTask [id={}] for user [{}]", task.taskId(), task.userId());
    }
}
