package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.viber.ViberClient;
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
class ViberNotificationConsumerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ViberClient viberClient;

    @InjectMocks
    private ViberNotificationConsumer viberNotificationConsumer;

    @Test
    @DisplayName("Should deliver notification to Viber when task is unique")
    void shouldDeliverNotificationWhenTaskIsUnique() {
        // Arrange
        String taskId = UUID.randomUUID().toString();
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-101",
                NotificationChannel.VIBER,
                "viber-chat-id-101",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(true);

        // Act
        viberNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(viberClient).sendMessage("viber-chat-id-101", "Air raid in Kyiv");
    }

    @Test
    @DisplayName("Should skip delivery when task is detected as duplicate by IdempotencyService")
    void shouldSkipDeliveryWhenTaskIsDuplicate() {
        // Arrange
        String taskId = "duplicate-task-id";
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-101",
                NotificationChannel.VIBER,
                "viber-chat-id-101",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(false);

        // Act
        viberNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(viberClient, never()).sendMessage(any(), any());
    }
}
