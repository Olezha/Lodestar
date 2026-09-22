package com.olehshklyar.lodestar.worker;

import com.olehshklyar.lodestar.client.AlertSourceClient;
import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AlertIngestWorker {

    private final AlertSourceClient alertSourceClient;
    private final AlertEventProducer alertEventProducer;

    @Scheduled(fixedRate = 15000)
    public void pollAndPublishAlerts() {
        log.debug("Polling external alert source...");
        List<AlertEvent> events = alertSourceClient.fetchLatestEvents();

        for (AlertEvent event : events) {
            alertEventProducer.sendAlertEvent(event);
        }
    }
}
