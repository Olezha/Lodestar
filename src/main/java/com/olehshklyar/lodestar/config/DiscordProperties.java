package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Discord Webhook integration.
 */
@ConfigurationProperties(prefix = "lodestar.discord")
public record DiscordProperties(
        String defaultWebhookUrl,
        String username,
        boolean dryRun
) {
    public DiscordProperties {
        if (defaultWebhookUrl == null) {
            defaultWebhookUrl = "";
        }
        if (username == null || username.isBlank()) {
            username = "Lodestar Alerts";
        }
    }
}
