package com.schedulingsystem.scheduler.task.builtin;

import java.util.List;
import java.util.Map;

import com.schedulingsystem.scheduler.task.ParameterDefinition;
import com.schedulingsystem.scheduler.task.ParameterType;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Runs a fixed, read-only query: reads the newest schedules and logs how many it found. */
@Component
@Order(3)
public class DbQueryTask implements Task {

    public static final String KEY = "db-query";
    public static final String LIMIT = "limit";

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(DbQueryTask.class);

    private static final TaskDefinition DEFINITION = new TaskDefinition(
            KEY,
            "DB Query Task",
            "Reads the newest schedules from the database and logs how many it found.",
            List.of(ParameterDefinition.optional(LIMIT, "Limit", ParameterType.NUMBER,
                    "How many schedules to read at most (1 to " + MAX_LIMIT + "). Default: " + DEFAULT_LIMIT + ".")));

    private final JdbcClient jdbcClient;

    public DbQueryTask(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public TaskDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void execute(Map<String, Object> parameters) {
        int limit = effectiveLimit(parameters.get(LIMIT));
        List<String> taskKeys = jdbcClient
                .sql("SELECT task_key FROM schedules ORDER BY created_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(String.class)
                .list();
        log.info("DB Query Task: found {} schedule(s) (limit {}), tasks: {}", taskKeys.size(), limit, taskKeys);
    }

    /** No limit means the default. Anything outside 1..MAX_LIMIT is pulled back into that range. */
    static int effectiveLimit(Object value) {
        if (!(value instanceof Number number)) {
            return DEFAULT_LIMIT;
        }
        return (int) Math.max(1, Math.min(MAX_LIMIT, number.longValue()));
    }
}
