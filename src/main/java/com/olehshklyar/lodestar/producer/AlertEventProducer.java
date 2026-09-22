package com.olehshklyar.lodestar.producer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dto.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventProducer {

    private final KafkaTemplate<String, AlertEvent> kafkaTemplate;

    public CompletableFuture<SendResult<String, AlertEvent>> sendAlertEvent(AlertEvent event) {
        log.info("Publishing AlertEvent to Kafka topic [{}]: regionId={}, type={}, severity={}",
                KafkaTopicConfig.RAW_ALERTS_TOPIC, event.regionId(), event.eventType(), event.severity());

        CompletableFuture<SendResult<String, AlertEvent>> future = 
                kafkaTemplate.send(KafkaTopicConfig.RAW_ALERTS_TOPIC, event.regionId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published AlertEvent [id={}] to partition [{}] with offset [{}]",
                        event.eventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish AlertEvent [id={}] to Kafka", event.eventId(), ex);
            }
        });

        return future;
    }
}
