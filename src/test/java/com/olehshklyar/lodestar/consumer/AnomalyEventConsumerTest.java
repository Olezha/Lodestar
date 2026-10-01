package com.olehshklyar.lodestar.consumer;

import com.olehshklyar.lodestar.dispatcher.NotificationDispatcher;
import com.olehshklyar.lodestar.dto.AnomalyEvent;
import com.olehshklyar.lodestar.dto.NotificationTask;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.service.SubscriptionCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyEventConsumerTest {

    @Mock
    private SubscriptionCacheService subscriptionCacheService;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private AnomalyEventConsumer anomalyEventConsumer;

    private static final String REGION_ID = "KYIV_REGION";

    @Test
    @DisplayName("Should dispatch notification task to active subscribers upon anomaly detection")
    void shouldDispatchNotificationToSubscribers() {
        // Arrange
        String anomalyId = UUID.randomUUID().toString();
        AnomalyEvent anomaly = new AnomalyEvent(
                anomalyId,
                REGION_ID,
                "RAPID_FIRE_SPIKE",
                "Spike detected in KYIV_REGION: 5 events within 300s",
                5,
                300L,
                "CRITICAL",
                Instant.now(),
                List.of("event-1", "event-2")
        );

        SubscriptionResponse sub1 = new SubscriptionResponse(
                1L, "user-101", NotificationChannel.DISCORD, "https://discord.com/api/webhooks/101",
                REGION_ID, "WARNING", true, Instant.now()
        );
        SubscriptionResponse sub2 = new SubscriptionResponse(
                2L, "user-102", NotificationChannel.NTFY, "kyiv-alerts-topic",
                REGION_ID, "INFO", true, Instant.now()
        );

        when(subscriptionCacheService.getActiveSubscriptions(REGION_ID)).thenReturn(List.of(sub1, sub2));

        // Act
        anomalyEventConsumer.consumeAnomalyEvent(anomaly);

        // Assert
        ArgumentCaptor<NotificationTask> taskCaptor = ArgumentCaptor.forClass(NotificationTask.class);
        verify(notificationDispatcher, org.mockito.Mockito.times(2)).dispatch(taskCaptor.capture());

        List<NotificationTask> dispatched = taskCaptor.getAllValues();
        assertThat(dispatched).hasSize(2);

        NotificationTask discordTask = dispatched.stream()
                .filter(t -> t.channel() == NotificationChannel.DISCORD)
                .findFirst().orElseThrow();
        assertThat(discordTask.userId()).isEqualTo("user-101");
        assertThat(discordTask.eventId()).isEqualTo(anomalyId);
        assertThat(discordTask.message()).contains("CRITICAL SPIKE ALERT in KYIV_REGION");

        NotificationTask ntfyTask = dispatched.stream()
                .filter(t -> t.channel() == NotificationChannel.NTFY)
                .findFirst().orElseThrow();
        assertThat(ntfyTask.userId()).isEqualTo("user-102");
        assertThat(ntfyTask.recipientAddress()).isEqualTo("kyiv-alerts-topic");
    }

    @Test
    @DisplayName("Should do nothing when there are no active subscribers for the anomaly region")
    void shouldDoNothingWhenNoSubscribers() {
        // Arrange
        AnomalyEvent anomaly = new AnomalyEvent(
                UUID.randomUUID().toString(),
                "ODESA_REGION",
                "RAPID_FIRE_SPIKE",
                "Spike detected in ODESA_REGION",
                3,
                300L,
                "CRITICAL",
                Instant.now(),
                Collections.emptyList()
        );

        when(subscriptionCacheService.getActiveSubscriptions("ODESA_REGION")).thenReturn(Collections.emptyList());

        // Act
        anomalyEventConsumer.consumeAnomalyEvent(anomaly);

        // Assert
        verify(notificationDispatcher, never()).dispatch(any());
    }
}
