package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the Stream Analysis & Anomaly Detection engine.
 */
@ConfigurationProperties(prefix = "lodestar.analytics")
public record AnalyticsProperties(
        Duration windowDuration,
        int spikeThreshold,
        Duration cooldownDuration,
        boolean enabled
) {
    public AnalyticsProperties {
        if (windowDuration == null) {
            windowDuration = Duration.ofMinutes(5);
        }
        if (spikeThreshold <= 0) {
            spikeThreshold = 3;
        }
        if (cooldownDuration == null) {
            cooldownDuration = Duration.ofMinutes(10);
        }
    }
}
