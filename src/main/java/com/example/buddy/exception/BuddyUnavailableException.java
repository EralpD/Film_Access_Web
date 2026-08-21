package com.example.buddy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class BuddyUnavailableException
        extends RuntimeException {

    public BuddyUnavailableException(String message) {
        super(message);
    }

    public BuddyUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}