package com.schedulingsystem.scheduler.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the {@code schedules} table: which task to run, when, and with which inputs.
 * The timing is kept in plain columns; only the columns of the current kind are filled.
 */
@Entity
@Table(name = "schedules")
public class Schedule {

    private static final String DAY_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_key", nullable = false)
    private String taskKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing_type", nullable = false)
    private TimingType timingType;

    @Column(name = "run_at")
    private Instant runAt;

    @Column(name = "interval_value")
    private Integer intervalValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit")
    private IntervalUnit intervalUnit;

    @Column(name = "weekly_days")
    private String weeklyDays;

    @Column(name = "weekly_time")
    private LocalTime weeklyTime;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false)
    private Map<String, Object> parameters = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Schedule() {
        // for JPA
    }

    public Schedule(Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Timing getTiming() {
        return switch (timingType) {
            case ONE_TIME -> new Timing.OneTime(runAt);
            case INTERVAL -> new Timing.Interval(intervalValue, intervalUnit);
            case WEEKLY -> new Timing.Weekly(parseDays(weeklyDays), weeklyTime);
            case CRON -> new Timing.Cron(cronExpression);
        };
    }

    /** Replaces the timing. Columns of the previous kind are emptied. */
    public void setTiming(Timing timing) {
        runAt = null;
        intervalValue = null;
        intervalUnit = null;
        weeklyDays = null;
        weeklyTime = null;
        cronExpression = null;

        switch (timing) {
            case Timing.OneTime oneTime -> {
                timingType = TimingType.ONE_TIME;
                runAt = oneTime.runAt();
            }
            case Timing.Interval interval -> {
                timingType = TimingType.INTERVAL;
                intervalValue = interval.every();
                intervalUnit = interval.unit();
            }
            case Timing.Weekly weekly -> {
                timingType = TimingType.WEEKLY;
                weeklyDays = weekly.days().stream().map(DayOfWeek::name).collect(Collectors.joining(DAY_SEPARATOR));
                weeklyTime = weekly.time();
            }
            case Timing.Cron cron -> {
                timingType = TimingType.CRON;
                cronExpression = cron.expression();
            }
        }
    }

    private static SortedSet<DayOfWeek> parseDays(String days) {
        return Arrays.stream(days.split(DAY_SEPARATOR))
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public UUID getId() {
        return id;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = new LinkedHashMap<>(parameters);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
