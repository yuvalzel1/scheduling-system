package com.schedulingsystem.scheduler.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

class ScheduledTaskJobTest {

    @Test
    void neverRunsTheSameScheduleTwiceAtOnce() {
        assertThat(ScheduledTaskJob.class).hasAnnotation(DisallowConcurrentExecution.class);
    }

    @Test
    void handsTheScheduleIdToTheRunner() {
        UUID id = UUID.randomUUID();
        ScheduledTaskRunner runner = mock(ScheduledTaskRunner.class);
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(Map.of("scheduleId", id.toString())));

        new ScheduledTaskJob(runner).executeInternal(context);

        verify(runner).run(id);
    }
}
