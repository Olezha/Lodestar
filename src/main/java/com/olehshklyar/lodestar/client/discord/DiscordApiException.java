package com.olehshklyar.lodestar.client.discord;

/**
 * Exception thrown when a call to the Discord Webhook API fails or returns an error.
 */
public class DiscordApiException extends RuntimeException {

    public DiscordApiException(String message) {
        super(message);
    }

    public DiscordApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
