package com.schedulingsystem.scheduler.schedule.quartz;

/** Quartz refused or failed an operation. Unchecked so that the surrounding transaction rolls back. */
public class SchedulingFailedException extends RuntimeException {

    public SchedulingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
