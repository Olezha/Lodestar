package com.olehshklyar.lodestar.producer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dto.AnomalyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Producer that publishes detected anomaly events to Kafka topic events.anomalies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> sendAnomalyEvent(AnomalyEvent event) {
        log.info("Publishing AnomalyEvent [id={}] to Kafka topic [{}]: regionId={}, type={}, count={}",
                event.anomalyId(), KafkaTopicConfig.ANOMALY_ALERTS_TOPIC, event.regionId(), event.anomalyType(), event.eventCount());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopicConfig.ANOMALY_ALERTS_TOPIC, event.regionId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published AnomalyEvent [id={}] to partition [{}] with offset [{}]",
                        event.anomalyId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish AnomalyEvent [id={}] to Kafka topic [{}]",
                        event.anomalyId(), KafkaTopicConfig.ANOMALY_ALERTS_TOPIC, ex);
            }
        });

        return future;
    }
}
