package com.schedulingsystem.scheduler.schedule.quartz;

import java.util.Optional;
import java.util.UUID;

import com.schedulingsystem.scheduler.schedule.Schedule;
import com.schedulingsystem.scheduler.schedule.ScheduleRepository;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the task of one schedule with its saved inputs.
 *
 * <p>Nothing is allowed to escape from here: a failing task is logged and the scheduler carries
 * on, so the next run of the same schedule still happens.
 */
@Component
public class ScheduledTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunner.class);

    private final ScheduleRepository scheduleRepository;
    private final TaskRegistry taskRegistry;

    public ScheduledTaskRunner(ScheduleRepository scheduleRepository, TaskRegistry taskRegistry) {
        this.scheduleRepository = scheduleRepository;
        this.taskRegistry = taskRegistry;
    }

    public void run(UUID scheduleId) {
        Optional<Schedule> schedule = scheduleRepository.findById(scheduleId);
        if (schedule.isEmpty()) {
            log.warn("Schedule {} no longer exists; skipping this run", scheduleId);
            return;
        }

        String taskKey = schedule.get().getTaskKey();
        Optional<Task> task = taskRegistry.find(taskKey);
        if (task.isEmpty()) {
            log.error("Schedule {} refers to unknown task '{}'; skipping this run", scheduleId, taskKey);
            return;
        }

        try {
            log.debug("Running task '{}' for schedule {}", taskKey, scheduleId);
            task.get().execute(schedule.get().getParameters());
        } catch (Exception e) {
            log.error("Task '{}' failed for schedule {}", taskKey, scheduleId, e);
        }
    }
}
