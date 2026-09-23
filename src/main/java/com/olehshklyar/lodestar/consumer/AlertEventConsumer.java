package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dto.AlertEvent;
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

import java.util.List;

/**
 * Consumer that listens to raw alert events from Kafka, persists them to the immutable history log,
 * and matches events with active subscriptions for downstream dispatch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final AlertSubscriptionRepository subscriptionRepository;
    private final AlertEventHistoryRepository historyRepository;

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

        for (AlertSubscription sub : matchedSubscriptions) {
            log.debug("Found recipient [{}] for channel [{}] in region [{}]",
                    sub.getRecipientAddress(), sub.getChannel(), sub.getRegionId());

            // TODO: Step 3.2 - Filter matched subscriptions by minSeverity threshold (e.g. WARNING vs CRITICAL)
            // TODO: Step 3.3 - Convert to NotificationTask DTO and dispatch to RabbitMQ (notifications.viber queue)
        }
    }
}
