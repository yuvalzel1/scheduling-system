package com.schedulingsystem.scheduler.execution;

import java.time.Instant;
import java.util.UUID;

/**
 * One run as the API shows it.
 *
 * @param errorMessage why the run failed; null when it succeeded
 */
public record ExecutionResponse(
        UUID id,
        String taskKey,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage) {

    static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getTaskKey(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getErrorMessage());
    }
}
