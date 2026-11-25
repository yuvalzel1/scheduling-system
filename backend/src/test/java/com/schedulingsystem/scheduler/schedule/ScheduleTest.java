package com.schedulingsystem.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    static Stream<Timing> everyKindOfTiming() {
        return Stream.of(
                new Timing.OneTime(Instant.parse("2026-12-01T07:00:00Z")),
                new Timing.Interval(15, IntervalUnit.MINUTES),
                new Timing.Interval(2, IntervalUnit.HOURS),
                new Timing.Weekly(new TreeSet<>(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), LocalTime.of(8, 30)),
                new Timing.Weekly(new TreeSet<>(List.of(DayOfWeek.SUNDAY)), LocalTime.MIDNIGHT),
                new Timing.Cron("0 0/5 * * * ?"));
    }

    @ParameterizedTest
    @MethodSource("everyKindOfTiming")
    void givesBackTheTimingItWasGiven(Timing timing) {
        Schedule schedule = new Schedule(NOW);

        schedule.setTiming(timing);

        assertThat(schedule.getTiming()).isEqualTo(timing);
    }

    @Test
    void emptiesTheColumnsOfThePreviousKindWhenTheKindChanges() {
        Schedule schedule = new Schedule(NOW);
        schedule.setTiming(new Timing.Weekly(new TreeSet<>(List.of(DayOfWeek.FRIDAY)), LocalTime.NOON));

        schedule.setTiming(new Timing.Cron("0 0 12 * * ?"));

        assertThat(schedule.getTiming()).isEqualTo(new Timing.Cron("0 0 12 * * ?"));
        assertThat(ReflectionTestUtils.getField(schedule, "weeklyDays")).isNull();
        assertThat(ReflectionTestUtils.getField(schedule, "weeklyTime")).isNull();
    }

    @Test
    void keepsWeeklyDaysInWeekOrderWithoutDuplicates() {
        Timing.Weekly weekly = new Timing.Weekly(
                new TreeSet<>(List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), LocalTime.NOON);

        assertThat(weekly.days()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void trimsSpacesAroundACronExpression() {
        assertThat(new Timing.Cron("  0 0 12 * * ?  ").expression()).isEqualTo("0 0 12 * * ?");
    }

    @Test
    void startsWithCreatedAndUpdatedSetToTheSameMoment() {
        Schedule schedule = new Schedule(NOW);

        assertThat(schedule.getCreatedAt()).isEqualTo(NOW);
        assertThat(schedule.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void copiesTheInputsSoLaterChangesToTheCallersMapDoNotLeakIn() {
        Map<String, Object> inputs = new HashMap<>(Map.of("message", "hello"));
        Schedule schedule = new Schedule(NOW);

        schedule.setParameters(inputs);
        inputs.put("message", "changed");

        assertThat(schedule.getParameters()).containsExactly(Map.entry("message", "hello"));
    }
}
