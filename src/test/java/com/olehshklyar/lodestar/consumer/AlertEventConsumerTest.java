package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.entity.AlertEventHistory;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import com.olehshklyar.lodestar.repository.AlertEventHistoryRepository;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AlertEventConsumerTest {

    static {
        System.setProperty("api.version", "1.44");
        System.setProperty("docker.client.api.version", "1.44");
    }

    @Container
    static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static final RabbitMQContainer rabbitContainer = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine")
    );

    @DynamicPropertySource
    static void setTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb_consumer;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.rabbitmq.host", rabbitContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitContainer::getAdminPassword);
    }

    @Autowired
    private AlertEventProducer alertEventProducer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @SpyBean
    private AlertSubscriptionRepository subscriptionRepository;

    @SpyBean
    private AlertEventHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        historyRepository.deleteAll();

        // 1. Active subscription for KYIV_REGION
        subscriptionRepository.save(AlertSubscription.builder()
                .userId("user-101")
                .channel("VIBER")
                .recipientAddress("viber-chat-id-101")
                .regionId("KYIV_REGION")
                .minSeverity("WARNING")
                .active(true)
                .build());

        // 2. Inactive subscription for KYIV_REGION
        subscriptionRepository.save(AlertSubscription.builder()
                .userId("user-102")
                .channel("VIBER")
                .recipientAddress("viber-chat-id-102")
                .regionId("KYIV_REGION")
                .minSeverity("WARNING")
                .active(false)
                .build());

        // 3. Active subscription for LVIV_REGION
        subscriptionRepository.save(AlertSubscription.builder()
                .userId("user-103")
                .channel("VIBER")
                .recipientAddress("viber-chat-id-103")
                .regionId("LVIV_REGION")
                .minSeverity("INFO")
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Should consume AlertEvent from Kafka topic, archive history, and match subscriptions")
    void shouldConsumeAlertEventAndMatchSubscriptions() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        AlertEvent event = new AlertEvent(
                eventId,
                "KYIV_REGION",
                "AIR_RAID",
                "ACTIVE",
                "WARNING",
                Instant.now()
        );

        // Act - publish event to Kafka
        alertEventProducer.sendAlertEvent(event);

        // Assert - verify consumer triggered repository query for matching region
        verify(subscriptionRepository, timeout(15000)).findByRegionIdAndActiveTrue(eq("KYIV_REGION"));

        // Assert - verify event was persisted to append-only history log
        verify(historyRepository, timeout(15000)).save(any(AlertEventHistory.class));
        List<AlertEventHistory> history = historyRepository.findByRegionIdOrderByEventTimestampDesc("KYIV_REGION");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getEventId()).isEqualTo(eventId);
        assertThat(history.get(0).getEventType()).isEqualTo("AIR_RAID");
        assertThat(history.get(0).getKafkaTopic()).isEqualTo("events.raw-alerts");

        // Assert - verify notification task was dispatched to RabbitMQ notifications.viber queue
        Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfig.VIBER_QUEUE, 10000);
        assertThat(received).isNotNull();
        assertThat(received).isInstanceOf(NotificationTask.class);
        NotificationTask task = (NotificationTask) received;
        assertThat(task.eventId()).isEqualTo(eventId);
        assertThat(task.userId()).isEqualTo("user-101");
        assertThat(task.channel()).isEqualTo("VIBER");
        assertThat(task.recipientAddress()).isEqualTo("viber-chat-id-101");
        assertThat(task.regionId()).isEqualTo("KYIV_REGION");
        assertThat(task.severity()).isEqualTo("WARNING");

        // Verify only 1 message was sent (user-102 is inactive, user-103 is in LVIV_REGION)
        Object second = rabbitTemplate.receiveAndConvert(RabbitMQConfig.VIBER_QUEUE, 1000);
        assertThat(second).isNull();
    }
}
