package com.schedulingsystem.scheduler.task.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.schedulingsystem.scheduler.IntegrationTest;
import com.schedulingsystem.scheduler.support.LogCapture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DbQueryTaskIntegrationTests extends IntegrationTest {

    @Autowired
    private DbQueryTask task;

    @Test
    void findsNothingInAnEmptyTable() {
        assertThat(logOf(Map.of())).contains("found 0 schedule(s)");
    }

    @Test
    void readsAllSchedulesWhenThereAreFewerThanTheDefaultLimit() {
        create(logSchedule(EVERY_15_MINUTES));
        create(logSchedule(CRON_EVERY_5_MINUTES));
        create(schedule("email", MONDAY_AND_WEDNESDAY_AT_0830, """
                {"to": "ada@example.com", "subject": "Hi"}"""));

        assertThat(logOf(Map.of())).contains("found 3 schedule(s) (limit 10)", "tasks: [email, log, log]");
    }

    @Test
    void readsNoMoreThanTheGivenLimitNewestFirst() {
        create(logSchedule(EVERY_15_MINUTES));
        create(logSchedule(CRON_EVERY_5_MINUTES));
        create(schedule("email", MONDAY_AND_WEDNESDAY_AT_0830, """
                {"to": "ada@example.com", "subject": "Hi"}"""));

        assertThat(logOf(Map.of("limit", 2))).contains("found 2 schedule(s) (limit 2)", "tasks: [email, log]");
    }

    private String logOf(Map<String, Object> inputs) {
        try (LogCapture logs = LogCapture.of(DbQueryTask.class)) {
            task.execute(inputs);
            return logs.lines().getFirst();
        }
    }
}
