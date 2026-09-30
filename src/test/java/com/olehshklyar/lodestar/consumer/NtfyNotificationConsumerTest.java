package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.client.ntfy.NtfyClient;
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
class NtfyNotificationConsumerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NtfyClient ntfyClient;

    @InjectMocks
    private NtfyNotificationConsumer ntfyNotificationConsumer;

    @Test
    @DisplayName("Should deliver notification to ntfy with urgent priority for CRITICAL severity")
    void shouldDeliverCriticalNotificationWithUrgentPriority() {
        // Arrange
        String taskId = UUID.randomUUID().toString();
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-301",
                NotificationChannel.NTFY,
                "lodestar-alerts",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(true);

        // Act
        ntfyNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(ntfyClient).sendMessage("lodestar-alerts", "Air raid in Kyiv", "Alert: KYIV_REGION", "urgent");
    }

    @Test
    @DisplayName("Should map WARNING severity to high priority")
    void shouldMapWarningSeverityToHighPriority() {
        // Arrange
        String taskId = UUID.randomUUID().toString();
        NotificationTask task = new NotificationTask(
                taskId,
                "event-456",
                "user-302",
                NotificationChannel.NTFY,
                "lodestar-warnings",
                "LVIV_REGION",
                "Artillery shelling threat",
                "WARNING",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(true);

        // Act
        ntfyNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(ntfyClient).sendMessage("lodestar-warnings", "Artillery shelling threat", "Alert: LVIV_REGION", "high");
    }

    @Test
    @DisplayName("Should skip delivery when task is detected as duplicate by IdempotencyService")
    void shouldSkipDeliveryWhenTaskIsDuplicate() {
        // Arrange
        String taskId = "duplicate-ntfy-task";
        NotificationTask task = new NotificationTask(
                taskId,
                "event-123",
                "user-301",
                NotificationChannel.NTFY,
                "lodestar-alerts",
                "KYIV_REGION",
                "Air raid in Kyiv",
                "CRITICAL",
                Instant.now()
        );

        when(idempotencyService.acquireLock(eq(taskId), any(Duration.class))).thenReturn(false);

        // Act
        ntfyNotificationConsumer.consumeNotificationTask(task);

        // Assert
        verify(idempotencyService).acquireLock(eq(taskId), any(Duration.class));
        verify(ntfyClient, never()).sendMessage(any(), any(), any(), any());
    }
}
