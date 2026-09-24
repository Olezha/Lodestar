package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.viber.ViberClient;
import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ViberNotificationConsumerIntegrationTest {

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

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void setTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb_viber_consumer;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.rabbitmq.host", rabbitContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitContainer::getAdminPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
        registry.add("lodestar.viber.dry-run", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SpyBean
    private ViberClient viberClient;

    @Test
    @DisplayName("Should consume task from RabbitMQ, deliver via ViberClient, and deduplicate duplicate message via Redis")
    void shouldConsumeTaskAndDeduplicateViaRedis() {
        // Arrange
        String taskId = UUID.randomUUID().toString();
        String message = "Critical alert for Kyiv";
        String recipient = "viber-user-123";

        NotificationTask task = new NotificationTask(
                taskId,
                "event-999",
                "user-101",
                NotificationChannel.VIBER,
                recipient,
                "KYIV_REGION",
                message,
                "CRITICAL",
                Instant.now()
        );

        // Act 1: Send first message to RabbitMQ exchange
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.VIBER_ROUTING_KEY,
                task
        );

        // Assert 1: Consumer processes task and calls viberClient
        verify(viberClient, timeout(15000).times(1)).sendMessage(eq(recipient), eq(message));

        // Verify Redis contains the idempotency record
        String redisKey = "dedup:notification:" + taskId;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();

        // Act 2: Send duplicate message with the same taskId
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.VIBER_ROUTING_KEY,
                task
        );

        // Assert 2: Wait a moment and verify viberClient was NOT called a second time
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {}

        verify(viberClient, times(1)).sendMessage(eq(recipient), eq(message));
    }
}
