package com.olehshklyar.lodestar.viber.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing incoming Viber webhook callback events.
 * Handles events: 'webhook', 'subscribed', 'unsubscribed', 'conversation_started', 'message'.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViberCallbackEvent(
        String event,
        Long timestamp,
        @JsonProperty("message_token")
        Long messageToken,
        ViberUser user,
        @JsonProperty("user_id")
        String userId,
        String desc
) {
    /**
     * Extracts the effective Viber user ID regardless of event payload variation.
     */
    public String getEffectiveUserId() {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        if (user != null && user.id() != null && !user.id().isBlank()) {
            return user.id();
        }
        return null;
    }
}
