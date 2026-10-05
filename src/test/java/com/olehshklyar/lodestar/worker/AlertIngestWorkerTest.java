package com.olehshklyar.lodestar.worker;

import com.olehshklyar.lodestar.client.AlertSourceClient;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import com.olehshklyar.lodestar.service.AlertDebounceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertIngestWorkerTest {

    @Mock
    private AlertSourceClient alertSourceClient;

    @Mock
    private AlertEventProducer alertEventProducer;

    @Mock
    private AlertDebounceService alertDebounceService;

    @Mock
    private com.olehshklyar.lodestar.service.LeaderElectionService leaderElectionService;

    private AlertSourceProperties properties;
    private AlertIngestWorker worker;

    @BeforeEach
    void setUp() {
        properties = new AlertSourceProperties(
                "synthetic",
                "",
                "",
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofHours(1)
        );
        worker = new AlertIngestWorker(alertSourceClient, alertEventProducer, alertDebounceService, leaderElectionService, properties);
    }

    @Test
    @DisplayName("Should publish alert event when debouncing allows it and instance is leader")
    void shouldPublishAlertWhenNotDebounced() {
        when(leaderElectionService.isLeader()).thenReturn(true);
        AlertEvent event = new AlertEvent("ev-1", "KYIV_REGION", "AIR_RAID", "ACTIVE", "CRITICAL", Instant.now());
        when(alertSourceClient.fetchLatestEvents()).thenReturn(List.of(event));
        when(alertDebounceService.shouldPublish(event)).thenReturn(true);

        worker.pollAndPublishAlerts();

        verify(alertEventProducer).sendAlertEvent(event);
    }

    @Test
    @DisplayName("Should skip publishing alert event when debounced as duplicate")
    void shouldSkipAlertWhenDebounced() {
        when(leaderElectionService.isLeader()).thenReturn(true);
        AlertEvent event = new AlertEvent("ev-2", "LVIV_REGION", "AIR_RAID", "ACTIVE", "CRITICAL", Instant.now());
        when(alertSourceClient.fetchLatestEvents()).thenReturn(List.of(event));
        when(alertDebounceService.shouldPublish(event)).thenReturn(false);

        worker.pollAndPublishAlerts();

        verify(alertEventProducer, never()).sendAlertEvent(any());
    }

    @Test
    @DisplayName("Should skip polling external API completely when instance is in standby mode")
    void shouldSkipPollingWhenInStandbyMode() {
        when(leaderElectionService.isLeader()).thenReturn(false);

        worker.pollAndPublishAlerts();

        verify(alertSourceClient, never()).fetchLatestEvents();
        verify(alertEventProducer, never()).sendAlertEvent(any());
    }

    @Test
    @DisplayName("Should register fixed rate task with configured pollInterval duration")
    void shouldConfigureFixedRateTaskWithConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        worker.configureTasks(registrar);

        List<IntervalTask> tasks = registrar.getFixedRateTaskList();
        assertThat(tasks).hasSize(1);
        IntervalTask task = tasks.getFirst();
        assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(15));
    }
}
