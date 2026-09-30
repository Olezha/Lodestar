package com.olehshklyar.lodestar.client.discord;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiscordWebhookRequest(
        @JsonProperty("content") String content,
        @JsonProperty("username") String username
) {
    public static DiscordWebhookRequest of(String content, String username) {
        return new DiscordWebhookRequest(content, username);
    }
}
