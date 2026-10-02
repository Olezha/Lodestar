package com.olehshklyar.lodestar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RegionConflictException extends RuntimeException {

    public RegionConflictException(String message) {
        super(message);
    }
}
