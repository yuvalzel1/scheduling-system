package com.schedulingsystem.scheduler.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * When a schedule runs. In JSON the {@code type} property says which of the four kinds it is,
 * and only that kind's fields are read; fields of the other kinds are ignored.
 *
 * <p>Values may be null or out of range here: this is the raw request shape.
 * {@link ScheduleValidator} is what decides whether a timing is acceptable.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Timing.OneTime.class, name = "ONE_TIME"),
        @JsonSubTypes.Type(value = Timing.Interval.class, name = "INTERVAL"),
        @JsonSubTypes.Type(value = Timing.Weekly.class, name = "WEEKLY"),
        @JsonSubTypes.Type(value = Timing.Cron.class, name = "CRON")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Timing {

    /** Runs once, at an exact moment. */
    record OneTime(Instant runAt) implements Timing {
    }

    /** Runs again and again with a fixed gap, for example every 15 minutes. */
    record Interval(Integer every, IntervalUnit unit) implements Timing {
    }

    /** Runs on the chosen days of the week at one time of day, in the schedule's time zone. */
    record Weekly(SortedSet<DayOfWeek> days, @JsonFormat(pattern = "HH:mm") LocalTime time) implements Timing {

        public Weekly {
            days = days == null ? null : Collections.unmodifiableSortedSet(new TreeSet<>(days));
        }
    }

    /** Runs when a Quartz cron expression says so (6 or 7 parts, starting with seconds). */
    record Cron(String expression) implements Timing {

        public Cron {
            expression = expression == null ? null : expression.strip();
        }
    }
}
