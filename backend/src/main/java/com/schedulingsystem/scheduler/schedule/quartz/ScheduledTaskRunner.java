package com.schedulingsystem.scheduler.schedule.quartz;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.schedulingsystem.scheduler.execution.ExecutionHistory;
import com.schedulingsystem.scheduler.schedule.Schedule;
import com.schedulingsystem.scheduler.schedule.ScheduleRepository;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the task of one schedule with its saved inputs, and writes the outcome to the run history.
 *
 * <p>Nothing is allowed to escape from here: a failing task is logged and recorded as a failed
 * run, and the scheduler carries on, so the next run of the same schedule still happens.
 */
@Component
public class ScheduledTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunner.class);

    private final ScheduleRepository scheduleRepository;
    private final TaskRegistry taskRegistry;
    private final ExecutionHistory executionHistory;
    private final Clock clock;

    public ScheduledTaskRunner(ScheduleRepository scheduleRepository, TaskRegistry taskRegistry,
            ExecutionHistory executionHistory, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.taskRegistry = taskRegistry;
        this.executionHistory = executionHistory;
        this.clock = clock;
    }

    public void run(UUID scheduleId) {
        Optional<Schedule> schedule = scheduleRepository.findById(scheduleId);
        if (schedule.isEmpty()) {
            log.warn("Schedule {} no longer exists; skipping this run", scheduleId);
            return;
        }

        String taskKey = schedule.get().getTaskKey();
        Instant startedAt = now();

        Optional<Task> task = taskRegistry.find(taskKey);
        if (task.isEmpty()) {
            log.error("Schedule {} refers to unknown task '{}'; skipping this run", scheduleId, taskKey);
            record(scheduleId, taskKey, startedAt, "Task '" + taskKey + "' does not exist any more");
            return;
        }

        String failure = null;
        try {
            log.debug("Running task '{}' for schedule {}", taskKey, scheduleId);
            task.get().execute(schedule.get().getParameters());
        } catch (Exception e) {
            log.error("Task '{}' failed for schedule {}", taskKey, scheduleId, e);
            failure = ExecutionHistory.describe(e);
        }
        record(scheduleId, taskKey, startedAt, failure);
    }

    /**
     * Writing the history must never break a run. The usual reason it cannot be written is that
     * the schedule was deleted while its task was still running.
     */
    private void record(UUID scheduleId, String taskKey, Instant startedAt, String failure) {
        try {
            executionHistory.record(scheduleId, taskKey, startedAt, now(), failure);
        } catch (Exception e) {
            log.warn("Could not record the run of schedule {} (was it deleted meanwhile?): {}", scheduleId, e.toString());
        }
    }

    /** Cut to what the database column keeps. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
