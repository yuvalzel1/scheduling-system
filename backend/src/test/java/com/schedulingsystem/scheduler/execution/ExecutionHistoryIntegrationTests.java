package com.schedulingsystem.scheduler.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.IntegrationTest;
import com.schedulingsystem.scheduler.schedule.quartz.ScheduledTaskRunner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The run history: every finished run is written down and can be read through the API.
 *
 * <p>Most tests ring the alarm by hand ({@code runner.run}), which is exactly what the Quartz job
 * does, so they need no waiting. One test goes through Quartz for real.
 */
class ExecutionHistoryIntegrationTests extends IntegrationTest {

    @Autowired
    private ScheduledTaskRunner runner;

    @Autowired
    private ExecutionHistory history;

    @Nested
    class AfterARun {

        @Test
        void aSuccessfulRunIsInTheHistory() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

            runner.run(id);

            List<Map<String, Object>> runs = historyOf(id).read("$");
            assertThat(runs).singleElement().satisfies(run -> {
                assertThat(run).containsEntry("taskKey", "log")
                        .containsEntry("status", "SUCCEEDED")
                        .containsEntry("errorMessage", null)
                        .containsKey("id");
                Instant startedAt = Instant.parse((String) run.get("startedAt"));
                Instant finishedAt = Instant.parse((String) run.get("finishedAt"));
                assertThat(startedAt).isBetween(before, Instant.now());
                assertThat(finishedAt).isBetween(startedAt, Instant.now());
            });
        }

        @Test
        void aFailedRunIsInTheHistoryWithItsReason() {
            doThrow(new IllegalStateException("the disk is full")).when(logTask).execute(anyMap());
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            runner.run(id);

            assertThat(historyOf(id).<List<Map<String, Object>>>read("$")).singleElement().satisfies(run ->
                    assertThat(run).containsEntry("status", "FAILED")
                            .containsEntry("errorMessage", "IllegalStateException: the disk is full"));
        }

        @Test
        void runsAreListedNewestFirst() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            runner.run(id);
            doThrow(new IllegalStateException("second run failed")).when(logTask).execute(anyMap());
            runner.run(id);

            assertThat(historyOf(id).<List<String>>read("$[*].status")).containsExactly("FAILED", "SUCCEEDED");
        }

        @Test
        void theScheduleShowsItsNewestRunInTheListAndOnItsOwn() {
            UUID ran = create(logSchedule(EVERY_15_MINUTES));
            UUID neverRan = create(logSchedule(CRON_EVERY_5_MINUTES));
            runner.run(ran);
            doThrow(new IllegalStateException("newest run failed")).when(logTask).execute(anyMap());
            runner.run(ran);

            String newestRunId = historyOf(ran).read("$[0].id");
            assertThat(get(SCHEDULES + "/" + ran).<Map<String, Object>>read("$.lastExecution"))
                    .containsEntry("id", newestRunId)
                    .containsEntry("status", "FAILED")
                    .containsEntry("errorMessage", "IllegalStateException: newest run failed");

            ApiResponse list = get(SCHEDULES);
            assertThat(list.<List<String>>read("$[?(@.id == '%s')].lastExecution.id".formatted(ran)))
                    .containsExactly(newestRunId);
            assertThat(list.<List<Object>>read("$[?(@.id == '%s')].lastExecution".formatted(neverRan)))
                    .containsExactly((Object) null);
        }

        @Test
        void theHistoryKeepsTheTaskThatRanEvenAfterTheScheduleSwitchesTask() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            runner.run(id);
            put(SCHEDULES + "/" + id, schedule("db-query", EVERY_15_MINUTES, "{}"));
            runner.run(id);

            assertThat(historyOf(id).<List<String>>read("$[*].taskKey")).containsExactly("db-query", "log");
        }

        @Test
        void anEditedScheduleStillShowsItsNewestRun() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            runner.run(id);

            ApiResponse edited = put(SCHEDULES + "/" + id, logSchedule(CRON_EVERY_5_MINUTES));

            assertThat(edited.<String>read("$.lastExecution.status")).isEqualTo("SUCCEEDED");
        }
    }

    @Nested
    class BeforeAnyRun {

        @Test
        void theHistoryIsEmptyAndTheScheduleHasNoNewestRun() {
            ApiResponse created = post(SCHEDULES, logSchedule(EVERY_15_MINUTES));
            String id = created.read("$.id");

            assertThat(created.<Object>read("$.lastExecution")).isNull();
            assertThat(get(SCHEDULES + "/" + id).<Object>read("$.lastExecution")).isNull();
            ApiResponse runs = get(SCHEDULES + "/" + id + "/executions");
            assertThat(runs.status()).isEqualTo(200);
            assertThat(runs.<List<Object>>read("$")).isEmpty();
        }

        @Test
        void askingForTheHistoryOfAnUnknownScheduleGivesNotFound() {
            ApiResponse response = get(SCHEDULES + "/" + UUID.randomUUID() + "/executions");

            assertThat(response.status()).isEqualTo(404);
            assertThat(response.<String>read("$.title")).isEqualTo("Not found");
        }
    }

    @Nested
    class Housekeeping {

        @Test
        void deletingAScheduleDeletesItsHistory() {
            UUID removed = create(logSchedule(EVERY_15_MINUTES));
            UUID kept = create(logSchedule(CRON_EVERY_5_MINUTES));
            runner.run(removed);
            runner.run(removed);
            runner.run(kept);

            delete(SCHEDULES + "/" + removed);

            assertThat(executionRows()).isEqualTo(1);
            assertThat(historyOf(kept).<List<Object>>read("$")).hasSize(1);
        }

        @Test
        void onlyTheNewestRunsOfAScheduleAreKept() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            UUID other = create(logSchedule(CRON_EVERY_5_MINUTES));
            Instant first = Instant.parse("2026-01-01T00:00:00Z");
            int runs = ExecutionHistory.KEPT_PER_SCHEDULE + 5;
            history.record(other, "log", first, first, null);

            for (int minute = 0; minute < runs; minute++) {
                Instant at = first.plus(Duration.ofMinutes(minute));
                history.record(id, "log", at, at.plusMillis(10), null);
            }

            List<String> startTimes = historyOf(id).read("$[*].startedAt");
            assertThat(startTimes).hasSize(ExecutionHistory.KEPT_PER_SCHEDULE);
            assertThat(startTimes.getFirst()).as("the newest run is kept")
                    .isEqualTo(first.plus(Duration.ofMinutes(runs - 1)).toString());
            assertThat(startTimes.getLast()).as("the five oldest runs are gone")
                    .isEqualTo(first.plus(Duration.ofMinutes(5)).toString());
            assertThat(historyOf(other).<List<Object>>read("$")).as("other schedules are untouched").hasSize(1);
        }

        @Test
        void aVeryLongErrorIsCutShort() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            history.record(id, "log", now, now, "x".repeat(5000));

            String stored = historyOf(id).read("$[0].errorMessage");
            assertThat(stored).hasSize(ExecutionHistory.MAX_ERROR_LENGTH).endsWith("…");
        }

        @Test
        void aRunCannotBeRecordedForAScheduleThatDoesNotExist() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            assertThatThrownBy(() -> history.record(UUID.randomUUID(), "log", now, now, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(executionRows()).isZero();
        }

        @Test
        void theDatabaseRefusesAFailedRunWithoutAReasonAndASuccessfulOneWithAReason() {
            UUID id = create(logSchedule(EVERY_15_MINUTES));

            assertThatThrownBy(() -> insertRunDirectly(id, "FAILED", "NULL"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("executions_error_only_when_failed");
            assertThatThrownBy(() -> insertRunDirectly(id, "SUCCEEDED", "'but why'"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("executions_error_only_when_failed");

            insertRunDirectly(id, "FAILED", "'a reason'");
            assertThat(executionRows()).isEqualTo(1);
        }

        private void insertRunDirectly(UUID scheduleId, String status, String errorLiteral) {
            jdbcClient.sql("""
                    INSERT INTO executions (id, schedule_id, task_key, status, started_at, finished_at, error_message)
                    VALUES (gen_random_uuid(), '%s', 'log', '%s', now(), now(), %s)
                    """.formatted(scheduleId, status, errorLiteral)).update();
        }
    }

    @Test
    void aRunStartedByQuartzEndsUpInTheHistory() {
        UUID id = create(schedule("log", oneTimeAt(Instant.now().plusSeconds(2)), """
                {"message": "recorded for real"}"""));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> !historyOf(id).<List<Object>>read("$").isEmpty());

        assertThat(historyOf(id).<List<String>>read("$[*].status")).containsExactly("SUCCEEDED");
        assertThat(get(SCHEDULES + "/" + id).<String>read("$.lastExecution.status")).isEqualTo("SUCCEEDED");
    }

    private ApiResponse historyOf(UUID scheduleId) {
        return get(SCHEDULES + "/" + scheduleId + "/executions");
    }

    private int executionRows() {
        return jdbcClient.sql("SELECT count(*) FROM executions").query(Integer.class).single();
    }
}
