package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.service.StreamAnalysisEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer that listens to raw alert stream using a dedicated consumer group (lodestar-analytics-group)
 * and feeds events into the StreamAnalysisEngine for sliding window anomaly detection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamAnalysisConsumer {

    private final StreamAnalysisEngine streamAnalysisEngine;

    @KafkaListener(
            topics = KafkaTopicConfig.RAW_ALERTS_TOPIC,
            groupId = "${spring.kafka.consumer.analytics-group-id:lodestar-analytics-group}"
    )
    public void consumeRawAlertForAnalysis(@Payload AlertEvent event) {
        log.debug("Analyzing incoming AlertEvent [id={}] in analytics consumer group", event.eventId());
        streamAnalysisEngine.analyzeEvent(event);
    }
}
