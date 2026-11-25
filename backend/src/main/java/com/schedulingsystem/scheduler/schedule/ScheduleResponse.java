package com.schedulingsystem.scheduler.schedule;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.execution.ExecutionResponse;

/**
 * A schedule as the API shows it.
 *
 * @param lastExecution the newest run of this schedule; null when it has not run yet
 */
public record ScheduleResponse(
        UUID id,
        String taskKey,
        Timing timing,
        String timeZone,
        Map<String, Object> parameters,
        Instant createdAt,
        Instant updatedAt,
        ExecutionResponse lastExecution) {

    static ScheduleResponse from(Schedule schedule, ExecutionResponse lastExecution) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getTaskKey(),
                schedule.getTiming(),
                schedule.getTimeZone(),
                schedule.getParameters(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt(),
                lastExecution);
    }
}
