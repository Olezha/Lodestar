package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.dto.AlertEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class SyntheticAlertSourceClient implements AlertSourceClient {

    private final Random random = new Random();
    private final String[] regions = {"KYIV_REGION", "KHARKIV_REGION", "ODESA_REGION", "LVIV_REGION", "DNIPRO_REGION"};
    private final String[] eventTypes = {"AIR_RAID", "ARTILLERY", "WEATHER_EXTREME", "RADIATION_RISK"};
    private final String[] severities = {"INFO", "WARNING", "CRITICAL"};

    @Override
    public List<AlertEvent> fetchLatestEvents() {
        String region = regions[random.nextInt(regions.length)];
        String eventType = eventTypes[random.nextInt(eventTypes.length)];
        String severity = severities[random.nextInt(severities.length)];

        AlertEvent event = new AlertEvent(
                UUID.randomUUID().toString(),
                region,
                eventType,
                "ACTIVE",
                severity,
                Instant.now()
        );

        return List.of(event);
    }
}
