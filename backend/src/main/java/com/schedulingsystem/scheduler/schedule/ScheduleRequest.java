package com.schedulingsystem.scheduler.schedule;

import java.util.Map;

/**
 * What a client sends to create or replace a schedule.
 *
 * @param taskKey    which predefined task to run
 * @param timing     when to run it
 * @param timeZone   IANA zone the timing is meant in, for example {@code Europe/Berlin}; UTC when left out
 * @param parameters input values for the task; empty when left out
 */
public record ScheduleRequest(String taskKey, Timing timing, String timeZone, Map<String, Object> parameters) {

    public static final String DEFAULT_TIME_ZONE = "UTC";

    public ScheduleRequest {
        timeZone = (timeZone == null || timeZone.isBlank()) ? DEFAULT_TIME_ZONE : timeZone.strip();
        parameters = parameters == null ? Map.of() : parameters;
    }
}
