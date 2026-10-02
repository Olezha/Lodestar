package com.olehshklyar.lodestar.worker;

import com.olehshklyar.lodestar.client.AlertSourceClient;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import com.olehshklyar.lodestar.service.AlertDebounceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AlertIngestWorker implements SchedulingConfigurer {

    private final AlertSourceClient alertSourceClient;
    private final AlertEventProducer alertEventProducer;
    private final AlertDebounceService alertDebounceService;
    private final AlertSourceProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        log.info("Registering alert ingestion worker with fixed rate: {}", properties.pollInterval());
        taskRegistrar.addFixedRateTask(this::pollAndPublishAlerts, properties.pollInterval());
    }

    public void pollAndPublishAlerts() {
        log.debug("Polling external alert source...");
        List<AlertEvent> events = alertSourceClient.fetchLatestEvents();

        for (AlertEvent event : events) {
            if (alertDebounceService.shouldPublish(event)) {
                log.info("Ingesting new alert event for region [{}] (type: {}, severity: {})",
                        event.regionId(), event.eventType(), event.severity());
                alertEventProducer.sendAlertEvent(event);
            } else {
                log.debug("Debouncing duplicate active alert for region [{}] (type: {})",
                        event.regionId(), event.eventType());
            }
        }
    }
}
