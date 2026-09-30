package com.olehshklyar.lodestar.client.discord;

/**
 * Client interface for interacting with Discord Webhooks.
 */
public interface DiscordClient {

    /**
     * Sends a message to a Discord webhook URL.
     *
     * @param webhookUrl target Discord webhook URL (or blank to fallback to default webhook URL)
     * @param message    text content of the alert notification
     * @return true if delivery was successful
     * @throws DiscordApiException if the remote API returns an error or request fails
     */
    boolean sendMessage(String webhookUrl, String message);
}
