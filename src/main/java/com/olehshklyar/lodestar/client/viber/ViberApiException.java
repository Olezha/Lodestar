package com.olehshklyar.lodestar.client.viber;

/**
 * Exception thrown when a call to the Viber REST API fails or returns an error.
 */
public class ViberApiException extends RuntimeException {

    public ViberApiException(String message) {
        super(message);
    }

    public ViberApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
