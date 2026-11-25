package com.schedulingsystem.scheduler.schedule.quartz;

import java.time.ZoneId;
import java.util.UUID;

import com.schedulingsystem.scheduler.schedule.Schedule;
import com.schedulingsystem.scheduler.schedule.ScheduleAlarms;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

/**
 * The Quartz side of {@link ScheduleAlarms}: one Quartz job with one trigger per schedule,
 * both named after the schedule's id.
 */
@Component
public class QuartzScheduleRegistrar implements ScheduleAlarms {

    public static final String GROUP = "schedules";

    private final Scheduler scheduler;
    private final TriggerFactory triggerFactory;

    public QuartzScheduleRegistrar(Scheduler scheduler, TriggerFactory triggerFactory) {
        this.scheduler = scheduler;
        this.triggerFactory = triggerFactory;
    }

    public static JobKey jobKey(UUID scheduleId) {
        return JobKey.jobKey(scheduleId.toString(), GROUP);
    }

    public static TriggerKey triggerKey(UUID scheduleId) {
        return TriggerKey.triggerKey(scheduleId.toString(), GROUP);
    }

    /**
     * Sets the alarm for a new schedule, or replaces the alarm of a changed one.
     * Removing first means there is never more than one alarm per schedule, and it also covers
     * a one-time schedule that already ran: Quartz has dropped its alarm by then, and removing
     * something that is not there is not an error.
     */
    @Override
    public void register(Schedule schedule) {
        UUID id = schedule.getId();
        Trigger trigger = triggerFactory.build(triggerKey(id), schedule.getTiming(), ZoneId.of(schedule.getTimeZone()));
        try {
            scheduler.deleteJob(jobKey(id));
            scheduler.scheduleJob(jobDetail(id), trigger);
        } catch (SchedulerException e) {
            throw new SchedulingFailedException("Could not set the alarm for schedule " + id, e);
        }
    }

    @Override
    public void unregister(UUID scheduleId) {
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException e) {
            throw new SchedulingFailedException("Could not remove the alarm for schedule " + scheduleId, e);
        }
    }

    /** The job carries only the schedule's id. Task and inputs are read from our table at run time. */
    public static JobDetail jobDetail(UUID scheduleId) {
        return JobBuilder.newJob(ScheduledTaskJob.class)
                .withIdentity(jobKey(scheduleId))
                .usingJobData(ScheduledTaskJob.SCHEDULE_ID, scheduleId.toString())
                .build();
    }
}
