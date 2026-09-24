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
    @DisplayName("Should publish NotificationTask to RabbitMQ with VIBER routing key")
    void shouldDispatchViberNotificationTask() {
        // Arrange
        NotificationTask task = new NotificationTask(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "user-101",
                NotificationChannel.VIBER,
                "viber-chat-id-101",
                "KYIV_REGION",
                "Alert message",
                "WARNING",
                Instant.now()
        );

        // Act
        notificationDispatcher.dispatch(task);

        // Assert
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.VIBER_ROUTING_KEY,
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
                "viber-chat-id-101",
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
