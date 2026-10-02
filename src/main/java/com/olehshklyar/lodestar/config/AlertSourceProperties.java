package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for alert data source ingestion.
 */
@ConfigurationProperties(prefix = "lodestar.alert-source")
public record AlertSourceProperties(
        String type,
        String alertsInUaBaseUrl,
        String alertsInUaToken,
        Duration pollInterval,
        Duration connectTimeout,
        Duration readTimeout,
        Duration debounceTtl
) {
    public AlertSourceProperties {
        if (type == null || type.isBlank()) {
            type = "synthetic";
        }
        if (alertsInUaBaseUrl == null) {
            alertsInUaBaseUrl = "";
        }
        if (alertsInUaToken == null) {
            alertsInUaToken = "";
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(15);
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (debounceTtl == null) {
            debounceTtl = Duration.ofHours(1);
        }
    }

    public boolean isLive() {
        return "live".equalsIgnoreCase(type);
    }
}
