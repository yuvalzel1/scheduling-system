package com.schedulingsystem.scheduler.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.schedule.Schedule;
import com.schedulingsystem.scheduler.schedule.Timing;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.test.util.ReflectionTestUtils;

class QuartzScheduleRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final Scheduler scheduler = mock(Scheduler.class);
    private final QuartzScheduleRegistrar registrar =
            new QuartzScheduleRegistrar(scheduler, new TriggerFactory(Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void namesJobAndTriggerAfterTheSchedule() {
        assertThat(QuartzScheduleRegistrar.jobKey(ID)).isEqualTo(JobKey.jobKey(ID.toString(), "schedules"));
        assertThat(QuartzScheduleRegistrar.triggerKey(ID)).isEqualTo(TriggerKey.triggerKey(ID.toString(), "schedules"));
    }

    @Test
    void putsOnlyTheScheduleIdIntoTheJobData() {
        JobDetail job = QuartzScheduleRegistrar.jobDetail(ID);

        assertThat(job.getJobClass()).isEqualTo(ScheduledTaskJob.class);
        assertThat(job.getKey()).isEqualTo(QuartzScheduleRegistrar.jobKey(ID));
        assertThat(job.getJobDataMap().getWrappedMap()).isEqualTo(Map.of("scheduleId", ID.toString()));
    }

    @Test
    void removesAnyOldAlarmBeforeSettingTheNewOne() throws SchedulerException {
        registrar.register(cronSchedule("0 0 12 * * ?"));

        ArgumentCaptor<JobDetail> job = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        InOrder order = inOrder(scheduler);
        order.verify(scheduler).deleteJob(QuartzScheduleRegistrar.jobKey(ID));
        order.verify(scheduler).scheduleJob(job.capture(), trigger.capture());
        order.verifyNoMoreInteractions();

        assertThat(job.getValue().getKey()).isEqualTo(QuartzScheduleRegistrar.jobKey(ID));
        assertThat(trigger.getValue().getKey()).isEqualTo(QuartzScheduleRegistrar.triggerKey(ID));
        assertThat(((CronTrigger) trigger.getValue()).getCronExpression()).isEqualTo("0 0 12 * * ?");
        assertThat(((CronTrigger) trigger.getValue()).getTimeZone().getID()).isEqualTo("Europe/Berlin");
    }

    @Test
    void turnsAQuartzFailureOnRegisterIntoAnUncheckedException() throws SchedulerException {
        SchedulerException quartzFailure = new SchedulerException("store is down");
        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenThrow(quartzFailure);

        assertThatThrownBy(() -> registrar.register(cronSchedule("0 0 12 * * ?")))
                .isInstanceOf(SchedulingFailedException.class)
                .hasMessageContaining(ID.toString())
                .hasCause(quartzFailure);
    }

    @Test
    void removesTheAlarmOnUnregister() throws SchedulerException {
        registrar.unregister(ID);

        verify(scheduler).deleteJob(QuartzScheduleRegistrar.jobKey(ID));
    }

    @Test
    void turnsAQuartzFailureOnUnregisterIntoAnUncheckedException() throws SchedulerException {
        when(scheduler.deleteJob(any(JobKey.class))).thenThrow(new SchedulerException("store is down"));

        assertThatThrownBy(() -> registrar.unregister(ID)).isInstanceOf(SchedulingFailedException.class);
    }

    private static Schedule cronSchedule(String expression) {
        Schedule schedule = new Schedule(NOW);
        ReflectionTestUtils.setField(schedule, "id", ID);
        schedule.setTaskKey("log");
        schedule.setTimeZone("Europe/Berlin");
        schedule.setTiming(new Timing.Cron(expression));
        return schedule;
    }
}
