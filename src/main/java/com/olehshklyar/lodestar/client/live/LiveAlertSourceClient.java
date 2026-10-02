package com.olehshklyar.lodestar.client.live;

import com.olehshklyar.lodestar.client.AlertSourceClient;
import com.olehshklyar.lodestar.client.RegionNormalizer;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live HTTP client that polls real-time alert data from alerts.in.ua open API.
 * Supports HTTP conditional requests (If-Modified-Since / Last-Modified) and debouncing.
 */
@Slf4j
@Component
public class LiveAlertSourceClient implements AlertSourceClient {

    private final RestClient restClient;
    private final RegionNormalizer regionNormalizer;
    private final AlertSourceProperties properties;
    private final AtomicReference<String> lastModifiedHeader = new AtomicReference<>();

    public LiveAlertSourceClient(
            RestClient.Builder restClientBuilder,
            RegionNormalizer regionNormalizer,
            AlertSourceProperties properties
    ) {
        this.regionNormalizer = regionNormalizer;
        this.properties = properties;

        RestClient.Builder builder = restClientBuilder
                .baseUrl(properties.alertsInUaBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (properties.alertsInUaToken() != null && !properties.alertsInUaToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.alertsInUaToken().trim());
        }

        this.restClient = builder.build();
    }

    @Override
    public List<AlertEvent> fetchLatestEvents() {
        if (properties.alertsInUaToken() == null || properties.alertsInUaToken().isBlank()) {
            log.warn("ALERTS_IN_UA_TOKEN is not configured; live polling may receive 401 Unauthorized from {}",
                    properties.alertsInUaBaseUrl());
        }

        try {
            log.debug("Polling live alert API: {}/alerts/active.json", properties.alertsInUaBaseUrl());
            var request = restClient.get()
                    .uri("/alerts/active.json");

            String lastModified = lastModifiedHeader.get();
            if (lastModified != null && !lastModified.isBlank()) {
                request.header(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
            }

            return request.exchange((clientRequest, clientResponse) -> {
                if (clientResponse.getStatusCode().value() == HttpStatus.NOT_MODIFIED.value()) {
                    log.debug("alerts.in.ua returned 304 Not Modified - alert data unchanged");
                    return Collections.emptyList();
                }

                if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                    log.warn("Non-2xx response from live API: {}", clientResponse.getStatusCode());
                    return Collections.emptyList();
                }

                String newLastModified = clientResponse.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
                if (newLastModified != null && !newLastModified.isBlank()) {
                    lastModifiedHeader.set(newLastModified);
                }

                AlertsInUaResponse response = clientResponse.bodyTo(AlertsInUaResponse.class);
                if (response == null || response.alerts() == null || response.alerts().isEmpty()) {
                    log.debug("No active alerts returned from live API");
                    return Collections.emptyList();
                }

                List<AlertEvent> events = new ArrayList<>(response.alerts().size());
                for (AlertsInUaAlert alert : response.alerts()) {
                    mapToAlertEvent(alert).ifPresent(events::add);
                }

                log.info("Fetched {} active alert(s) from live API", events.size());
                return events;
            });
        } catch (Exception ex) {
            log.warn("Failed to fetch live alerts from {}: {}", properties.alertsInUaBaseUrl(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    private Optional<AlertEvent> mapToAlertEvent(AlertsInUaAlert alert) {
        if (alert == null || alert.locationTitle() == null || alert.locationTitle().isBlank()) {
            return Optional.empty();
        }

        String regionId = regionNormalizer.normalize(alert.locationTitle());
        String eventType = resolveEventType(alert.alertType());
        String severity = resolveSeverity(eventType);
        Instant timestamp = parseTimestamp(alert.startedAt());

        String eventId = alert.id() != null
                ? "alerts-in-ua-" + alert.id()
                : UUID.randomUUID().toString();

        return Optional.of(new AlertEvent(
                eventId,
                regionId,
                eventType,
                "ACTIVE",
                severity,
                timestamp
        ));
    }

    private String resolveEventType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "AIR_RAID";
        }
        return switch (rawType.toLowerCase()) {
            case "air_raid" -> "AIR_RAID";
            case "artillery_shelling", "artillery" -> "ARTILLERY";
            case "urban_fights" -> "URBAN_FIGHTS";
            case "chemical" -> "CHEMICAL_RISK";
            case "nuclear" -> "RADIATION_RISK";
            default -> rawType.toUpperCase().replace(" ", "_");
        };
    }

    private String resolveSeverity(String eventType) {
        return switch (eventType) {
            case "AIR_RAID", "ARTILLERY", "RADIATION_RISK" -> "CRITICAL";
            case "CHEMICAL_RISK", "URBAN_FIGHTS" -> "WARNING";
            default -> "INFO";
        };
    }

    private Instant parseTimestamp(String rawStartedAt) {
        if (rawStartedAt == null || rawStartedAt.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(rawStartedAt);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }
}
