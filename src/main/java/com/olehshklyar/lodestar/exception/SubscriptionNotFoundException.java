package com.olehshklyar.lodestar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(Long id) {
        super("Subscription with ID " + id + " not found");
    }

    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}
