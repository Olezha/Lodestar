package com.olehshklyar.lodestar.client.viber;

/**
 * Client interface for interacting with Viber Bot REST API.
 */
public interface ViberClient {

    /**
     * Sends a text message to a specific Viber recipient address (chat ID or subscriber ID).
     *
     * @param recipientAddress recipient Viber user ID
     * @param message          text content of the alert notification
     * @return true if delivery was successful
     * @throws ViberApiException if the remote API returns an error or request fails
     */
    boolean sendMessage(String recipientAddress, String message);
}
