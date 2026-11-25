package com.schedulingsystem.scheduler.schedule.quartz;

import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * The single Quartz job class behind every schedule. Quartz creates it when an alarm rings;
 * Spring supplies the runner. It only reads the schedule id and hands over.
 *
 * <p>{@link DisallowConcurrentExecution} applies per schedule: one schedule never overlaps
 * itself, while different schedules still run side by side.
 */
@DisallowConcurrentExecution
public class ScheduledTaskJob extends QuartzJobBean {

    public static final String SCHEDULE_ID = "scheduleId";

    private final ScheduledTaskRunner runner;

    public ScheduledTaskJob(ScheduledTaskRunner runner) {
        this.runner = runner;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String scheduleId = context.getMergedJobDataMap().getString(SCHEDULE_ID);
        runner.run(UUID.fromString(scheduleId));
    }
}
