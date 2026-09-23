package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dispatcher.NotificationDispatcher;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.entity.AlertEventHistory;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.repository.AlertEventHistoryRepository;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumer that listens to raw alert events from Kafka, persists them to the immutable history log,
 * matches events with active subscriptions, and dispatches delivery tasks to RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final AlertSubscriptionRepository subscriptionRepository;
    private final AlertEventHistoryRepository historyRepository;
    private final NotificationDispatcher notificationDispatcher;

    @KafkaListener(
            topics = KafkaTopicConfig.RAW_ALERTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:lodestar-group}"
    )
    public void consumeAlert(
            @Payload AlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received AlertEvent [id={}] from topic [{}] (partition={}, offset={}): regionId={}, type={}, severity={}",
                event.eventId(), topic, partition, offset, event.regionId(), event.eventType(), event.severity());

        // 1. Persist to immutable append-only event log
        AlertEventHistory historyRecord = AlertEventHistory.builder()
                .eventId(event.eventId())
                .regionId(event.regionId())
                .eventType(event.eventType())
                .status(event.status())
                .severity(event.severity())
                .eventTimestamp(event.timestamp())
                .kafkaTopic(topic)
                .kafkaPartition(partition)
                .kafkaOffset(offset)
                .build();
        historyRepository.save(historyRecord);
        log.info("Archived AlertEvent [id={}] to append-only history log", event.eventId());

        // 2. Query active subscribers for matching region
        List<AlertSubscription> matchedSubscriptions = subscriptionRepository
                .findByRegionIdAndActiveTrue(event.regionId());

        log.info("Matched {} active subscription(s) for region [{}]", matchedSubscriptions.size(), event.regionId());

        // 3. Filter by severity and dispatch notification tasks to RabbitMQ
        for (AlertSubscription sub : matchedSubscriptions) {
            if (!isSeveritySufficient(event.severity(), sub.getMinSeverity())) {
                log.debug("Skipping subscription [id={}] for user [{}]: event severity [{}] below minSeverity [{}]",
                        sub.getId(), sub.getUserId(), event.severity(), sub.getMinSeverity());
                continue;
            }

            log.info("Dispatching notification task for user [{}] via channel [{}]",
                    sub.getUserId(), sub.getChannel());

            NotificationTask task = new NotificationTask(
                    UUID.randomUUID().toString(),
                    event.eventId(),
                    sub.getUserId(),
                    sub.getChannel(),
                    sub.getRecipientAddress(),
                    event.regionId(),
                    String.format("Alert in %s: %s [%s]", event.regionId(), event.eventType(), event.severity()),
                    event.severity(),
                    Instant.now()
            );

            notificationDispatcher.dispatch(task);
        }
    }

    private boolean isSeveritySufficient(String eventSeverity, String minSeverity) {
        if (minSeverity == null || minSeverity.isBlank()) {
            return true;
        }
        return getSeverityWeight(eventSeverity) >= getSeverityWeight(minSeverity);
    }

    private int getSeverityWeight(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "WARNING" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }
}
