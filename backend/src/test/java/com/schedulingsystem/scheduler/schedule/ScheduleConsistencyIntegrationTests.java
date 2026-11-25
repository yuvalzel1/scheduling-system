package com.schedulingsystem.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.schedulingsystem.scheduler.IntegrationTest;
import com.schedulingsystem.scheduler.schedule.quartz.QuartzScheduleRegistrar;
import com.schedulingsystem.scheduler.schedule.quartz.SchedulingFailedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.quartz.CronTrigger;
import org.quartz.SchedulerException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Our {@code schedules} table and Quartz must never disagree, and alarms must live in the
 * database so that they survive a restart.
 */
class ScheduleConsistencyIntegrationTests extends IntegrationTest {

    @Test
    void noRowIsLeftWhenQuartzRefusesTheAlarm() throws SchedulerException {
        doThrow(new SchedulingFailedException("Quartz said no", null)).when(registrar).register(any());

        ApiResponse response = post(SCHEDULES, logSchedule(EVERY_15_MINUTES));

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.<String>read("$.title")).isEqualTo("Server error");
        assertThat(response.body()).as("internal details are not shown to the client").doesNotContain("Quartz said no");
        assertThat(scheduleRepository.count()).isZero();
        assertThat(totalJobs()).isZero();
    }

    @Test
    void noAlarmIsLeftWhenSavingFailsAfterQuartzAcceptedIt() throws SchedulerException {
        failRightAfterQuartzAcceptedTheAlarm();

        ApiResponse response = post(SCHEDULES, logSchedule(EVERY_15_MINUTES));

        assertThat(response.status()).isEqualTo(500);
        assertThat(totalJobs()).as("the Quartz job was rolled back").isZero();
        assertThat(totalAlarms()).as("the Quartz trigger was rolled back").isZero();
        assertThat(count("qrtz_triggers")).isZero();
        assertThat(count("qrtz_job_details")).isZero();
        assertThat(scheduleRepository.count()).isZero();
    }

    @Test
    void aFailedEditRollsBackBothTheRowAndTheAlarm() throws SchedulerException {
        UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));
        String before = get(SCHEDULES + "/" + id).body();
        failRightAfterQuartzAcceptedTheAlarm();

        ApiResponse response = put(SCHEDULES + "/" + id, logSchedule(MONDAY_AND_WEDNESDAY_AT_0830));

        assertThat(response.status()).isEqualTo(500);
        assertThat(get(SCHEDULES + "/" + id).body()).isEqualTo(before);
        assertThat(alarmsOf(id)).singleElement()
                .satisfies(alarm -> assertThat(((CronTrigger) alarm).getCronExpression()).isEqualTo("0 0/5 * * * ?"));
    }

    @Test
    void aFailedDeleteKeepsBothTheRowAndTheAlarm() throws SchedulerException {
        UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));
        doThrow(new SchedulingFailedException("Quartz said no", null)).when(registrar).unregister(any());

        ApiResponse response = delete(SCHEDULES + "/" + id);

        assertThat(response.status()).isEqualTo(500);
        assertThat(get(SCHEDULES + "/" + id).status()).isEqualTo(200);
        assertThat(alarmsOf(id)).hasSize(1);
    }

    @Test
    void alarmsAreStoredInTheDatabaseSoTheySurviveARestart() throws SchedulerException {
        assertThat(scheduler.getMetaData().isJobStoreSupportsPersistence())
                .as("Quartz must use the database job store, not memory")
                .isTrue();

        UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));

        assertThat(jdbcClient.sql("SELECT cron_expression, time_zone_id FROM qrtz_cron_triggers WHERE trigger_name = :id")
                .param("id", id.toString()).query().singleRow())
                .containsEntry("cron_expression", "0 0/5 * * * ?")
                .containsEntry("time_zone_id", "Europe/Berlin");
        assertThat(jdbcClient.sql("SELECT job_class_name FROM qrtz_job_details WHERE job_name = :id")
                .param("id", id.toString()).query(String.class).single())
                .isEqualTo("com.schedulingsystem.scheduler.schedule.quartz.ScheduledTaskJob");
    }

    @Test
    void theStoredJobDataHoldsOnlyTheScheduleId() throws SchedulerException {
        UUID id = create(logSchedule(CRON_EVERY_5_MINUTES));

        assertThat(scheduler.getJobDetail(QuartzScheduleRegistrar.jobKey(id)).getJobDataMap().getWrappedMap())
                .isEqualTo(Map.of("scheduleId", id.toString()));
    }

    @Test
    void flywayBuiltEveryTable() {
        List<String> tables = jdbcClient
                .sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class)
                .list();

        assertThat(tables).contains("schedules", "executions", "flyway_schema_history",
                "qrtz_job_details", "qrtz_triggers", "qrtz_simple_triggers", "qrtz_cron_triggers",
                "qrtz_simprop_triggers", "qrtz_blob_triggers", "qrtz_calendars", "qrtz_paused_trigger_grps",
                "qrtz_fired_triggers", "qrtz_scheduler_state", "qrtz_locks");
        assertThat(jdbcClient.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Integer.class).single())
                .isEqualTo(2);
    }

    static Stream<Arguments> rowsWhoseTimingColumnsDoNotMatchTheirKind() {
        String moment = "'2099-01-01T00:00:00Z'";
        return Stream.of(
                //            what is wrong                      type         run_at  every   unit        days        time       cron
                Arguments.of("one-time without a moment",        "ONE_TIME",  "NULL", "NULL", "NULL",     "NULL",     "NULL",    "NULL"),
                Arguments.of("interval without a number",        "INTERVAL",  "NULL", "NULL", "'HOURS'",  "NULL",     "NULL",    "NULL"),
                Arguments.of("interval of zero",                 "INTERVAL",  "NULL", "0",    "'HOURS'",  "NULL",     "NULL",    "NULL"),
                Arguments.of("interval without a unit",          "INTERVAL",  "NULL", "5",    "NULL",     "NULL",     "NULL",    "NULL"),
                Arguments.of("weekly without days",              "WEEKLY",    "NULL", "NULL", "NULL",     "NULL",     "'08:30'", "NULL"),
                Arguments.of("weekly with empty days",           "WEEKLY",    "NULL", "NULL", "NULL",     "''",       "'08:30'", "NULL"),
                Arguments.of("weekly without a time",            "WEEKLY",    "NULL", "NULL", "NULL",     "'MONDAY'", "NULL",    "NULL"),
                Arguments.of("cron without an expression",       "CRON",      "NULL", "NULL", "NULL",     "NULL",     "NULL",    "NULL"),
                Arguments.of("cron with an empty expression",    "CRON",      "NULL", "NULL", "NULL",     "NULL",     "NULL",    "''"),
                Arguments.of("cron with leftover interval",      "CRON",      "NULL", "5",    "'HOURS'",  "NULL",     "NULL",    "'0 0 12 * * ?'"),
                Arguments.of("one-time with leftover cron",      "ONE_TIME",  moment, "NULL", "NULL",     "NULL",     "NULL",    "'0 0 12 * * ?'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rowsWhoseTimingColumnsDoNotMatchTheirKind")
    void theDatabaseItselfRefusesARowWhoseTimingColumnsDoNotMatchItsKind(
            String what, String type, String runAt, String every, String unit, String days, String time, String cron) {
        assertThatThrownBy(() -> insertRowDirectly(type, runAt, every, unit, days, time, cron))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("schedules_timing_columns_match_type");
        assertThat(scheduleRepository.count()).isZero();
    }

    @Test
    void theDatabaseItselfRefusesAnUnknownTimingKind() {
        assertThatThrownBy(() -> insertRowDirectly("WHENEVER", "NULL", "NULL", "NULL", "NULL", "NULL", "NULL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("violates check constraint");
    }

    @Test
    void theDatabaseAcceptsAWellFormedRowWrittenDirectly() {
        // Proves the direct insert used above is itself valid SQL, so those rejections mean what they say.
        insertRowDirectly("INTERVAL", "NULL", "5", "'HOURS'", "NULL", "NULL", "NULL");

        assertThat(scheduleRepository.count()).isEqualTo(1);
        assertThat(scheduleRepository.findAll().getFirst().getTiming())
                .isEqualTo(new Timing.Interval(5, IntervalUnit.HOURS));
    }

    /** Writes a row behind the application's back. The arguments are SQL literals. */
    private void insertRowDirectly(
            String type, String runAt, String every, String unit, String days, String time, String cron) {
        jdbcClient.sql("""
                INSERT INTO schedules (id, task_key, timing_type, run_at, interval_value, interval_unit,
                                       weekly_days, weekly_time, cron_expression, time_zone, parameters,
                                       created_at, updated_at)
                VALUES (gen_random_uuid(), 'log', '%s', %s, %s, %s, %s, %s, %s, 'UTC', '{"message": "x"}', now(), now())
                """.formatted(type, runAt, every, unit, days, time, cron)).update();
    }

    /**
     * Lets Quartz really store the alarm, then fails, as if a later step of the same save broke.
     * It first checks that Quartz did accept the alarm, so the tests above cannot pass merely
     * because nothing was ever stored.
     */
    private void failRightAfterQuartzAcceptedTheAlarm() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            Schedule schedule = invocation.getArgument(0);
            assertThat(alarmsOf(schedule.getId())).as("Quartz accepted the alarm before the failure").hasSize(1);
            throw new IllegalStateException("something broke after Quartz accepted the alarm");
        }).when(registrar).register(any());
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
