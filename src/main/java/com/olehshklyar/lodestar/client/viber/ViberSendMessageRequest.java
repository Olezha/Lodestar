package com.olehshklyar.lodestar.client.viber;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ViberSendMessageRequest(
        @JsonProperty("receiver") String receiver,
        @JsonProperty("type") String type,
        @JsonProperty("text") String text,
        @JsonProperty("sender") ViberSender sender
) {
    public static ViberSendMessageRequest textMessage(String receiver, String text, String senderName) {
        return new ViberSendMessageRequest(receiver, "text", text, new ViberSender(senderName));
    }
}
