package com.olehshklyar.lodestar.client.ntfy;

/**
 * Client interface for interacting with ntfy.sh HTTP Pub/Sub push notification API.
 */
public interface NtfyClient {

    /**
     * Sends a push notification via ntfy with default title and priority.
     *
     * @param topicOrUrl target topic name (e.g., "lodestar-alerts") or full ntfy URL
     * @param message    text content of the alert notification
     * @return true if delivery was successful
     * @throws NtfyApiException if the remote API returns an error or request fails
     */
    boolean sendMessage(String topicOrUrl, String message);

    /**
     * Sends a push notification via ntfy with specified title and priority headers.
     *
     * @param topicOrUrl target topic name or full ntfy URL
     * @param message    text content of the alert notification
     * @param title      notification title (e.g. "Air Raid Alert")
     * @param priority   ntfy priority (e.g. "urgent", "high", "default", "low", "min")
     * @return true if delivery was successful
     * @throws NtfyApiException if the remote API returns an error or request fails
     */
    boolean sendMessage(String topicOrUrl, String message, String title, String priority);
}
