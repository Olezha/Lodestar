package com.olehshklyar.lodestar.client.ntfy;

import com.olehshklyar.lodestar.config.NtfyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementation of NtfyClient using Spring 6 RestClient.
 */
@Slf4j
@Component
public class NtfyClientImpl implements NtfyClient {

    private final RestClient restClient;
    private final NtfyProperties ntfyProperties;

    public NtfyClientImpl(RestClient.Builder restClientBuilder, NtfyProperties ntfyProperties) {
        this.ntfyProperties = ntfyProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean sendMessage(String topicOrUrl, String message) {
        return sendMessage(topicOrUrl, message, "Lodestar Alert", "high");
    }

    @Override
    public boolean sendMessage(String topicOrUrl, String message, String title, String priority) {
        if (ntfyProperties.dryRun()) {
            log.info("[DRY-RUN] ntfy message skipped. Target: [{}], title: [{}], message: [{}]",
                    topicOrUrl, title, message);
            return true;
        }

        String targetUrl = resolveUrl(topicOrUrl);

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Title", title != null ? title : "Lodestar Alert")
                    .header("Priority", priority != null ? priority : "high")
                    .body(message != null ? message : "")
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to send ntfy push notification: status {}", response.getStatusCode());
                throw new NtfyApiException("ntfy returned non-2xx status: " + response.getStatusCode());
            }

            log.info("Successfully delivered ntfy push notification to [{}]", targetUrl);
            return true;

        } catch (RestClientResponseException ex) {
            log.error("HTTP error during ntfy API call to [{}]: status {}, body: {}",
                    targetUrl, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new NtfyApiException("HTTP error calling ntfy API: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            if (ex instanceof NtfyApiException ntfyEx) {
                throw ntfyEx;
            }
            log.error("Unexpected error sending ntfy notification to [{}]: {}", targetUrl, ex.getMessage(), ex);
            throw new NtfyApiException("Unexpected error calling ntfy API: " + ex.getMessage(), ex);
        }
    }

    private String resolveUrl(String topicOrUrl) {
        if (topicOrUrl == null || topicOrUrl.isBlank()) {
            topicOrUrl = ntfyProperties.defaultTopic();
        }

        if (topicOrUrl.startsWith("http://") || topicOrUrl.startsWith("https://")) {
            return topicOrUrl;
        }

        String serverUrl = ntfyProperties.serverUrl();
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        if (topicOrUrl.startsWith("/")) {
            topicOrUrl = topicOrUrl.substring(1);
        }
        return serverUrl + "/" + topicOrUrl;
    }
}
