package com.olehshklyar.lodestar.producer;

import com.olehshklyar.lodestar.config.KafkaTopicConfig;
import com.olehshklyar.lodestar.dto.AlertEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AlertEventProducerTest {

    static {
        System.setProperty("api.version", "1.44");
        System.setProperty("docker.client.api.version", "1.44");
    }

    @Container
    static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @DynamicPropertySource
    static void setKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.rabbitmq.host", () -> "localhost");
    }

    @Autowired
    private AlertEventProducer alertEventProducer;

    @Test
    @DisplayName("Should publish AlertEvent to Kafka topic and consume it successfully")
    void shouldPublishAlertEventToKafka() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        AlertEvent event = new AlertEvent(
                eventId,
                "KYIV_REGION",
                "AIR_RAID",
                "ACTIVE",
                "CRITICAL",
                Instant.now()
        );

        // Act
        alertEventProducer.sendAlertEvent(event).join();

        // Assert - consume directly from Kafka to verify
        KafkaConsumer<String, AlertEvent> consumer = createTestConsumer();
        consumer.subscribe(Collections.singletonList(KafkaTopicConfig.RAW_ALERTS_TOPIC));

        ConsumerRecords<String, AlertEvent> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, AlertEvent> record = records.iterator().next();

        assertThat(record.key()).isEqualTo("KYIV_REGION");
        assertThat(record.value().eventId()).isEqualTo(eventId);
        assertThat(record.value().eventType()).isEqualTo("AIR_RAID");

        consumer.close();
    }

    private KafkaConsumer<String, AlertEvent> createTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new KafkaConsumer<>(props);
    }
}
