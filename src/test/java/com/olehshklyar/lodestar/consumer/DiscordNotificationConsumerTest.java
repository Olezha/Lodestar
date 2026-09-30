package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.discord.DiscordClient;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordNotificationConsumerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private DiscordClient discordClient;

    @InjectMocks
    private DiscordNotificationConsumer discordNotificationConsumer;

    @Test
    @DisplayName("Should deliver notification to Discord when task is unique")
    void shouldDeliverNotificationWhenTaskIsUnique() {
        // Arrange
        String taskId = UUID.randomUUID().toString();
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-201",
                NotificationChannel.DISCORD,
                "https://discord.com/api/webhooks/123/xyz",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(true);

        // Act
        discordNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(discordClient).sendMessage("https://discord.com/api/webhooks/123/xyz", "Air raid in Kyiv");
    }

    @Test
    @DisplayName("Should skip delivery when task is detected as duplicate by IdempotencyService")
    void shouldSkipDeliveryWhenTaskIsDuplicate() {
        // Arrange
        String taskId = "duplicate-discord-task";
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-201",
                NotificationChannel.DISCORD,
                "https://discord.com/api/webhooks/123/xyz",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(false);

        // Act
        discordNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(discordClient, never()).sendMessage(any(), any());
    }
}
