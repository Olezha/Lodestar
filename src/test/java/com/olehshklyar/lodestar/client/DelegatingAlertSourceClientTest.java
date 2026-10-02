package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.client.live.LiveAlertSourceClient;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelegatingAlertSourceClientTest {

    @Mock
    private SyntheticAlertSourceClient syntheticClient;

    @Mock
    private LiveAlertSourceClient liveClient;

    private static final AlertEvent SAMPLE_EVENT = new AlertEvent(
            "sample-id", "KYIV_REGION", "AIR_RAID", "ACTIVE", "CRITICAL", Instant.now()
    );

    @Test
    @DisplayName("Should delegate to SyntheticAlertSourceClient when type is synthetic")
    void shouldDelegateToSyntheticWhenTypeIsSynthetic() {
        // Arrange
        AlertSourceProperties properties = new AlertSourceProperties("synthetic", "https://api.alerts.in.ua/v1", "", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofHours(1));
        DelegatingAlertSourceClient delegator = new DelegatingAlertSourceClient(properties, syntheticClient, liveClient);

        when(syntheticClient.fetchLatestEvents()).thenReturn(List.of(SAMPLE_EVENT));

        // Act
        List<AlertEvent> events = delegator.fetchLatestEvents();

        // Assert
        assertThat(events).hasSize(1);
        verify(syntheticClient).fetchLatestEvents();
        verify(liveClient, never()).fetchLatestEvents();
    }

    @Test
    @DisplayName("Should delegate to LiveAlertSourceClient when type is live and token is present")
    void shouldDelegateToLiveWhenTypeIsLiveAndTokenSet() {
        // Arrange
        AlertSourceProperties properties = new AlertSourceProperties("live", "https://api.alerts.in.ua/v1", "my-token", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofHours(1));
        DelegatingAlertSourceClient delegator = new DelegatingAlertSourceClient(properties, syntheticClient, liveClient);

        when(liveClient.fetchLatestEvents()).thenReturn(List.of(SAMPLE_EVENT));

        // Act
        List<AlertEvent> events = delegator.fetchLatestEvents();

        // Assert
        assertThat(events).hasSize(1);
        verify(liveClient).fetchLatestEvents();
        verify(syntheticClient, never()).fetchLatestEvents();
    }

    @Test
    @DisplayName("Should fall back to SyntheticAlertSourceClient when type is live but token is empty")
    void shouldFallbackToSyntheticWhenTokenMissing() {
        // Arrange
        AlertSourceProperties properties = new AlertSourceProperties("live", "https://api.alerts.in.ua/v1", "", Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofHours(1));
        DelegatingAlertSourceClient delegator = new DelegatingAlertSourceClient(properties, syntheticClient, liveClient);

        when(syntheticClient.fetchLatestEvents()).thenReturn(List.of(SAMPLE_EVENT));

        // Act
        List<AlertEvent> events = delegator.fetchLatestEvents();

        // Assert
        assertThat(events).hasSize(1);
        verify(syntheticClient).fetchLatestEvents();
        verify(liveClient, never()).fetchLatestEvents();
    }
}
