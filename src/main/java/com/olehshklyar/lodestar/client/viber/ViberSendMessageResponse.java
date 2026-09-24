package com.olehshklyar.lodestar.client.viber;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ViberSendMessageResponse(
        @JsonProperty("status") int status,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("message_token") Long messageToken
) {
    public boolean isSuccess() {
        return status == 0;
    }
}
