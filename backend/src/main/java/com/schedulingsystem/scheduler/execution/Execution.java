package com.schedulingsystem.scheduler.execution;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One finished run of a schedule's task. Written once, never changed.
 *
 * <p>It knows its schedule only by id: this package does not depend on the schedule code.
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_id", nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(name = "task_key", nullable = false, updatable = false)
    private String taskKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false, updatable = false)
    private Instant finishedAt;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    protected Execution() {
        // for JPA
    }

    /** @param errorMessage why the run failed, or null when it succeeded */
    public Execution(UUID scheduleId, String taskKey, Instant startedAt, Instant finishedAt, String errorMessage) {
        this.scheduleId = scheduleId;
        this.taskKey = taskKey;
        this.status = errorMessage == null ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
