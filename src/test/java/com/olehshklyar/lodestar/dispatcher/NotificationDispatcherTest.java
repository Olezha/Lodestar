package com.olehshklyar.lodestar.dispatcher;

import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationDispatcher notificationDispatcher;

    @Test
    @DisplayName("Should publish NotificationTask to RabbitMQ with DISCORD routing key")
    void shouldDispatchDiscordNotificationTask() {
        // Arrange
        NotificationTask task = new NotificationTask(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "user-102",
                NotificationChannel.DISCORD,
                "https://discord.com/api/webhooks/123/abc",
                "KYIV_REGION",
                "Discord alert",
                "CRITICAL",
                Instant.now()
        );

        // Act
        notificationDispatcher.dispatch(task);

        // Assert
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.DISCORD_ROUTING_KEY,
                task
        );
    }

    @Test
    @DisplayName("Should publish NotificationTask to RabbitMQ with NTFY routing key")
    void shouldDispatchNtfyNotificationTask() {
        // Arrange
        NotificationTask task = new NotificationTask(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "user-103",
                NotificationChannel.NTFY,
                "lodestar-alerts",
                "KYIV_REGION",
                "ntfy alert",
                "INFO",
                Instant.now()
        );

        // Act
        notificationDispatcher.dispatch(task);

        // Assert
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.NTFY_ROUTING_KEY,
                task
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when channel is null")
    void shouldThrowExceptionWhenChannelIsNull() {
        // Arrange
        NotificationTask task = new NotificationTask(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "user-101",
                null,
                "https://discord.com/api/webhooks/101/token",
                "KYIV_REGION",
                "Alert message",
                "WARNING",
                Instant.now()
        );

        // Act & Assert
        assertThatThrownBy(() -> notificationDispatcher.dispatch(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Notification channel must not be null");
    }
}
