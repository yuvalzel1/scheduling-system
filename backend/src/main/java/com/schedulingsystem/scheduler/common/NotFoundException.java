package com.schedulingsystem.scheduler.common;

/** The requested item does not exist. Becomes a 404 response. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
