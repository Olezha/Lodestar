package com.olehshklyar.lodestar.client.viber;

import com.olehshklyar.lodestar.config.ViberProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementation of ViberClient using Spring Boot 3 RestClient.
 */
@Slf4j
@Component
public class ViberClientImpl implements ViberClient {

    private final RestClient restClient;
    private final ViberProperties viberProperties;

    public ViberClientImpl(RestClient.Builder restClientBuilder, ViberProperties viberProperties) {
        this.viberProperties = viberProperties;
        this.restClient = restClientBuilder
                .baseUrl(viberProperties.apiUrl())
                .defaultHeader("X-Viber-Auth-Token", viberProperties.authToken())
                .build();
    }

    @Override
    public boolean sendMessage(String recipientAddress, String message) {
        if (viberProperties.dryRun()) {
            log.info("[DRY-RUN] Viber message to [{}] skipped. Message: [{}]", recipientAddress, message);
            return true;
        }

        ViberSendMessageRequest request = ViberSendMessageRequest.textMessage(
                recipientAddress,
                message,
                viberProperties.senderName()
        );

        try {
            ViberSendMessageResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ViberSendMessageResponse.class);

            if (response == null || !response.isSuccess()) {
                String errorMsg = response != null ? response.statusMessage() : "Empty response from Viber API";
                log.error("Failed to send Viber message to [{}]: {}", recipientAddress, errorMsg);
                throw new ViberApiException("Viber API error: " + errorMsg);
            }

            log.info("Successfully delivered Viber message to [{}] (token: {})", recipientAddress, response.messageToken());
            return true;

        } catch (RestClientResponseException ex) {
            log.error("HTTP error during Viber API call for [{}]: status {}, body: {}",
                    recipientAddress, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ViberApiException("HTTP error calling Viber API: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            if (ex instanceof ViberApiException viberEx) {
                throw viberEx;
            }
            log.error("Unexpected error sending Viber message to [{}]: {}", recipientAddress, ex.getMessage(), ex);
            throw new ViberApiException("Unexpected error calling Viber API: " + ex.getMessage(), ex);
        }
    }
}
