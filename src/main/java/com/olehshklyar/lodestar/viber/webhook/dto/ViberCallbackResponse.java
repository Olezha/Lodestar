package com.olehshklyar.lodestar.viber.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard HTTP response payload required by Viber Webhook API.
 */
public record ViberCallbackResponse(
        int status,
        @JsonProperty("status_message")
        String statusMessage
) {
    public static final ViberCallbackResponse OK = new ViberCallbackResponse(0, "ok");
}
