package com.olehshklyar.lodestar.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String RAW_ALERTS_TOPIC = "events.raw-alerts";
    public static final String ANOMALY_ALERTS_TOPIC = "events.anomalies";

    @Bean
    public NewTopic rawAlertsTopic() {
        return TopicBuilder.name(RAW_ALERTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyAlertsTopic() {
        return TopicBuilder.name(ANOMALY_ALERTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
