package com.olehshklyar.lodestar.client.discord;

import com.olehshklyar.lodestar.config.DiscordProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementation of DiscordClient using Spring 6 RestClient.
 */
@Slf4j
@Component
public class DiscordClientImpl implements DiscordClient {

    private final RestClient restClient;
    private final DiscordProperties discordProperties;

    public DiscordClientImpl(RestClient.Builder restClientBuilder, DiscordProperties discordProperties) {
        this.discordProperties = discordProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean sendMessage(String webhookUrl, String message) {
        if (discordProperties.dryRun()) {
            log.info("[DRY-RUN] Discord message skipped. Target: [{}], message: [{}]", webhookUrl, message);
            return true;
        }

        String targetUrl = (webhookUrl != null && !webhookUrl.isBlank())
                ? webhookUrl
                : discordProperties.defaultWebhookUrl();

        if (targetUrl == null || targetUrl.isBlank()) {
            log.error("Discord webhook URL is not specified and no default URL is configured");
            throw new DiscordApiException("Discord webhook URL must not be blank");
        }

        DiscordWebhookRequest request = DiscordWebhookRequest.of(message, discordProperties.username());

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to send Discord message: status {}", response.getStatusCode());
                throw new DiscordApiException("Discord webhook returned non-2xx status: " + response.getStatusCode());
            }

            log.info("Successfully delivered Discord message to webhook [{}]", maskWebhookUrl(targetUrl));
            return true;

        } catch (RestClientResponseException ex) {
            log.error("HTTP error during Discord webhook call to [{}]: status {}, body: {}",
                    maskWebhookUrl(targetUrl), ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new DiscordApiException("HTTP error calling Discord webhook: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            if (ex instanceof DiscordApiException discordEx) {
                throw discordEx;
            }
            log.error("Unexpected error sending Discord webhook to [{}]: {}", maskWebhookUrl(targetUrl), ex.getMessage(), ex);
            throw new DiscordApiException("Unexpected error calling Discord webhook: " + ex.getMessage(), ex);
        }
    }

    private String maskWebhookUrl(String url) {
        if (url == null || url.length() <= 35) {
            return "***";
        }
        return url.substring(0, 35) + "...";
    }
}
