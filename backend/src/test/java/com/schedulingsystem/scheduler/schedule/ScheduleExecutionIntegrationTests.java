package com.schedulingsystem.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.IntegrationTest;
import com.schedulingsystem.scheduler.schedule.quartz.QuartzScheduleRegistrar;

import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

/** Schedules that really fire: Quartz rings the alarm and the task runs. */
class ScheduleExecutionIntegrationTests extends IntegrationTest {

    private static final long PATIENCE_MS = 15_000;

    @Test
    void aOneTimeScheduleFiresOnceWithItsSavedInputs() {
        create(schedule("log", oneTimeAt(Instant.now().plusSeconds(2)), """
                {"message": "it is time"}"""));

        verify(logTask, timeout(PATIENCE_MS)).execute(Map.of("message", "it is time"));

        stayTrueFor(Duration.ofSeconds(2), () -> verify(logTask, times(1)).execute(anyMap()));
    }

    @Test
    void aRepeatingScheduleFiresAgainAndAgain() throws SchedulerException {
        UUID id = create(logSchedule(EVERY_15_MINUTES));
        fireEverySecond(id);

        verify(logTask, timeout(PATIENCE_MS).atLeast(3)).execute(Map.of("message", "hello"));
    }

    @Test
    void aFailingTaskDoesNotStopTheNextRun() throws SchedulerException {
        doThrow(new IllegalStateException("the task blew up")).when(logTask).execute(anyMap());
        UUID id = create(logSchedule(EVERY_15_MINUTES));
        fireEverySecond(id);

        verify(logTask, timeout(PATIENCE_MS).atLeast(3)).execute(anyMap());
        assertThat(alarmsOf(id)).as("the alarm is still set").hasSize(1);
    }

    @Test
    void aDeletedScheduleIsNotRunAgain() throws SchedulerException, InterruptedException {
        UUID id = create(logSchedule(EVERY_15_MINUTES));
        fireEverySecond(id);
        verify(logTask, timeout(PATIENCE_MS).atLeast(1)).execute(anyMap());

        assertThat(delete(SCHEDULES + "/" + id).status()).isEqualTo(204);
        Thread.sleep(1_500); // let a run that had already started finish
        clearInvocations(logTask);

        stayTrueFor(Duration.ofSeconds(3), () -> verify(logTask, never()).execute(anyMap()));
    }

    @Test
    void aScheduleWhoseRowVanishedIsSkippedWithoutBreakingTheScheduler() throws SchedulerException {
        UUID orphan = create(logSchedule(EVERY_15_MINUTES));
        fireEverySecond(orphan);
        scheduleRepository.deleteById(orphan); // row gone, alarm still there

        UUID healthy = create(schedule("log", oneTimeAt(Instant.now().plusSeconds(2)), """
                {"message": "still working"}"""));

        verify(logTask, timeout(PATIENCE_MS)).execute(Map.of("message", "still working"));
        verify(logTask, never()).execute(Map.of("message", "hello"));
        assertThat(healthy).isNotNull();
    }

    @Test
    void aOneTimeScheduleThatAlreadyRanCanStillBeEdited() throws SchedulerException {
        UUID id = createOneTimeScheduleAndWaitUntilItRan();

        ApiResponse response = put(SCHEDULES + "/" + id, logSchedule(oneTimeAt(Instant.now().plusSeconds(3600))));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(alarmsOf(id)).as("a fresh alarm is set").hasSize(1);
    }

    @Test
    void aOneTimeScheduleThatAlreadyRanCanStillBeDeleted() throws SchedulerException {
        UUID id = createOneTimeScheduleAndWaitUntilItRan();

        assertThat(delete(SCHEDULES + "/" + id).status()).isEqualTo(204);
        assertThat(get(SCHEDULES + "/" + id).status()).isEqualTo(404);
    }

    @Test
    void aOneTimeScheduleStaysInTheListAfterItRan() throws SchedulerException {
        UUID id = createOneTimeScheduleAndWaitUntilItRan();

        assertThat(get(SCHEDULES + "/" + id).status()).isEqualTo(200);
    }

    private UUID createOneTimeScheduleAndWaitUntilItRan() throws SchedulerException {
        UUID id = create(logSchedule(oneTimeAt(Instant.now().plusSeconds(2))));
        verify(logTask, timeout(PATIENCE_MS)).execute(anyMap());
        // Quartz drops the job of a finished one-time alarm by itself.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> !scheduler.checkExists(QuartzScheduleRegistrar.jobKey(id)));
        return id;
    }

    /**
     * The API's smallest gap is one minute, too slow for a test. This swaps the schedule's alarm
     * for one that rings every second; job, schedule row and everything after the ring stay real.
     */
    private void fireEverySecond(UUID scheduleId) throws SchedulerException {
        Trigger everySecond = TriggerBuilder.newTrigger()
                .withIdentity(QuartzScheduleRegistrar.triggerKey(scheduleId))
                .startNow()
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever())
                .build();
        scheduler.rescheduleJob(QuartzScheduleRegistrar.triggerKey(scheduleId), everySecond);
    }

    private static void stayTrueFor(Duration duration, Runnable assertion) {
        await().during(duration).atMost(duration.plusSeconds(5)).untilAsserted(assertion::run);
    }
}
