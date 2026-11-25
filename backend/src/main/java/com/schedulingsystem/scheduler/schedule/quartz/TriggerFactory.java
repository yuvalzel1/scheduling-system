package com.schedulingsystem.scheduler.schedule.quartz;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.schedulingsystem.scheduler.schedule.Timing;

import org.quartz.CronScheduleBuilder;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

/**
 * Turns a timing into a Quartz trigger (an "alarm").
 *
 * <p>Each kind also says what to do with runs missed while the server was down, instead of
 * relying on Quartz defaults: a one-time run still happens once; repeating ones skip what they
 * missed and carry on at the next normal time.
 */
@Component
public class TriggerFactory {

    private final Clock clock;

    public TriggerFactory(Clock clock) {
        this.clock = clock;
    }

    public Trigger build(TriggerKey key, Timing timing, ZoneId zone) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger().withIdentity(key);
        return switch (timing) {
            case Timing.OneTime oneTime -> builder
                    .startAt(Date.from(oneTime.runAt()))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                    .build();
            case Timing.Interval interval -> builder
                    .startAt(Date.from(firstIntervalRun(interval)))
                    .withSchedule(intervalSchedule(interval))
                    .build();
            case Timing.Weekly weekly -> builder
                    .withSchedule(cronSchedule(weeklyCron(weekly), zone))
                    .build();
            case Timing.Cron cron -> builder
                    .withSchedule(cronSchedule(cron.expression(), zone))
                    .build();
        };
    }

    /** "Every 15 minutes" first runs 15 minutes from now, not immediately. */
    private Instant firstIntervalRun(Timing.Interval interval) {
        Duration gap = switch (interval.unit()) {
            case MINUTES -> Duration.ofMinutes(interval.every());
            case HOURS -> Duration.ofHours(interval.every());
        };
        return clock.instant().plus(gap);
    }

    private static ScheduleBuilder<?> intervalSchedule(Timing.Interval interval) {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .repeatForever()
                .withMisfireHandlingInstructionNextWithExistingCount();
        return switch (interval.unit()) {
            case MINUTES -> schedule.withIntervalInMinutes(interval.every());
            case HOURS -> schedule.withIntervalInHours(interval.every());
        };
    }

    private static ScheduleBuilder<?> cronSchedule(String expression, ZoneId zone) {
        return CronScheduleBuilder.cronSchedule(expression)
                .inTimeZone(TimeZone.getTimeZone(zone))
                .withMisfireHandlingInstructionDoNothing();
    }

    /** Monday and Wednesday at 08:30 becomes {@code 0 30 8 ? * MON,WED}. */
    static String weeklyCron(Timing.Weekly weekly) {
        String days = weekly.days().stream().map(TriggerFactory::cronDay).collect(Collectors.joining(","));
        return "0 %d %d ? * %s".formatted(weekly.time().getMinute(), weekly.time().getHour(), days);
    }

    /** Day names avoid Quartz's numbering, where 1 means Sunday. */
    private static String cronDay(DayOfWeek day) {
        return day.name().substring(0, 3);
    }
}
