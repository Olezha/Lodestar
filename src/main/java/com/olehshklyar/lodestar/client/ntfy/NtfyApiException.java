package com.olehshklyar.lodestar.client.ntfy;

/**
 * Exception thrown when a call to the ntfy.sh API fails or returns an error.
 */
public class NtfyApiException extends RuntimeException {

    public NtfyApiException(String message) {
        super(message);
    }

    public NtfyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
