package com.schedulingsystem.scheduler.schedule;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A schedule as the API shows it. */
public record ScheduleResponse(
        UUID id,
        String taskKey,
        Timing timing,
        String timeZone,
        Map<String, Object> parameters,
        Instant createdAt,
        Instant updatedAt) {

    static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getTaskKey(),
                schedule.getTiming(),
                schedule.getTimeZone(),
                schedule.getParameters(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
