package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Viber Bot integration.
 */
@ConfigurationProperties(prefix = "lodestar.viber")
public record ViberProperties(
        String apiUrl,
        String authToken,
        String senderName,
        boolean dryRun
) {
    public ViberProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "https://chatapi.viber.com/pa/send_message";
        }
        if (authToken == null) {
            authToken = "";
        }
        if (senderName == null || senderName.isBlank()) {
            senderName = "Lodestar Alerts";
        }
    }
}
