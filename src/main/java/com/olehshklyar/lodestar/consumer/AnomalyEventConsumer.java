package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dispatcher.NotificationDispatcher;
import com.olehshklyar.lodestar.dto.AnomalyEvent;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.service.SubscriptionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumer that listens to detected AnomalyEvents from Kafka topic `events.anomalies`
 * and dispatches priority notifications to active region subscribers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyEventConsumer {

    private final SubscriptionCacheService subscriptionCacheService;
    private final NotificationDispatcher notificationDispatcher;

    @KafkaListener(
            topics = KafkaTopicConfig.ANOMALY_ALERTS_TOPIC,
            groupId = "${spring.kafka.consumer.anomaly-group-id:lodestar-anomaly-group}"
    )
    public void consumeAnomalyEvent(@Payload AnomalyEvent anomaly) {
        log.warn("Received AnomalyEvent [id={}] for region [{}]: type={}, count={}",
                anomaly.anomalyId(), anomaly.regionId(), anomaly.anomalyType(), anomaly.eventCount());

        List<SubscriptionResponse> subscribers = subscriptionCacheService
                .getActiveSubscriptions(anomaly.regionId());

        if (subscribers.isEmpty()) {
            log.info("No active subscribers found for anomaly in region [{}]", anomaly.regionId());
            return;
        }

        String formattedMessage = String.format("CRITICAL SPIKE ALERT in %s: %s (Detected: %s)",
                anomaly.regionId(), anomaly.description(), anomaly.detectedAt());

        for (SubscriptionResponse sub : subscribers) {
            NotificationTask task = new NotificationTask(
                    UUID.randomUUID().toString(),
                    anomaly.anomalyId(),
                    sub.userId(),
                    sub.channel(),
                    sub.recipientAddress(),
                    anomaly.regionId(),
                    formattedMessage,
                    anomaly.severity(),
                    Instant.now()
            );

            log.info("Dispatching anomaly notification task to user [{}] via channel [{}]",
                    sub.userId(), sub.channel());
            notificationDispatcher.dispatch(task);
        }
    }
}
