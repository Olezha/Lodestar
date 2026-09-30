package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ntfy.sh push notification integration.
 */
@ConfigurationProperties(prefix = "lodestar.ntfy")
public record NtfyProperties(
        String serverUrl,
        String defaultTopic,
        boolean dryRun
) {
    public NtfyProperties {
        if (serverUrl == null || serverUrl.isBlank()) {
            serverUrl = "https://ntfy.sh";
        }
        if (defaultTopic == null || defaultTopic.isBlank()) {
            defaultTopic = "lodestar-alerts";
        }
    }
}
