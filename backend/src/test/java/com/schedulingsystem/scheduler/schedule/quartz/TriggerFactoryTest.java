package com.schedulingsystem.scheduler.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;

import com.schedulingsystem.scheduler.schedule.IntervalUnit;
import com.schedulingsystem.scheduler.schedule.Timing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

class TriggerFactoryTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final TriggerKey KEY = TriggerKey.triggerKey("a-schedule-id", "schedules");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final TriggerFactory factory = new TriggerFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void namesTheTriggerWithTheGivenKey() {
        Trigger trigger = factory.build(KEY, new Timing.Cron("0 0 12 * * ?"), BERLIN);

        assertThat(trigger.getKey()).isEqualTo(KEY);
    }

    @Nested
    class OneTime {

        private final Instant runAt = Instant.parse("2026-12-01T07:00:00Z");
        private final SimpleTrigger trigger = (SimpleTrigger) factory.build(KEY, new Timing.OneTime(runAt), BERLIN);

        @Test
        void firesAtTheExactMoment() {
            assertThat(trigger.getStartTime().toInstant()).isEqualTo(runAt);
        }

        @Test
        void firesOnlyOnce() {
            assertThat(trigger.getRepeatCount()).isZero();
            assertThat(trigger.getFireTimeAfter(Date.from(runAt))).isNull();
        }

        @Test
        void stillRunsOnceIfItsMomentWasMissed() {
            assertThat(trigger.getMisfireInstruction()).isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        }
    }

    @Nested
    class Interval {

        @Test
        void repeatsForeverWithTheGapInMinutes() {
            SimpleTrigger trigger = build(new Timing.Interval(15, IntervalUnit.MINUTES));

            assertThat(trigger.getRepeatInterval()).isEqualTo(Duration.ofMinutes(15).toMillis());
            assertThat(trigger.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
        }

        @Test
        void repeatsForeverWithTheGapInHours() {
            SimpleTrigger trigger = build(new Timing.Interval(2, IntervalUnit.HOURS));

            assertThat(trigger.getRepeatInterval()).isEqualTo(Duration.ofHours(2).toMillis());
            assertThat(trigger.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
        }

        @Test
        void firstRunsOneGapFromNow() {
            assertThat(build(new Timing.Interval(15, IntervalUnit.MINUTES)).getStartTime().toInstant())
                    .isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            assertThat(build(new Timing.Interval(2, IntervalUnit.HOURS)).getStartTime().toInstant())
                    .isEqualTo(NOW.plus(Duration.ofHours(2)));
        }

        @Test
        void skipsMissedRunsAndContinuesAtTheNextNormalTime() {
            assertThat(build(new Timing.Interval(15, IntervalUnit.MINUTES)).getMisfireInstruction())
                    .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_EXISTING_COUNT);
        }

        private SimpleTrigger build(Timing.Interval timing) {
            return (SimpleTrigger) factory.build(KEY, timing, BERLIN);
        }
    }

    @Nested
    class Weekly {

        @Test
        void buildsACronExpressionFromDaysAndTime() {
            CronTrigger trigger = build(weekly(LocalTime.of(8, 30), DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), BERLIN);

            assertThat(trigger.getCronExpression()).isEqualTo("0 30 8 ? * MON,WED");
        }

        @Test
        void handlesSundayAtMidnight() {
            CronTrigger trigger = build(weekly(LocalTime.MIDNIGHT, DayOfWeek.SUNDAY), BERLIN);

            assertThat(trigger.getCronExpression()).isEqualTo("0 0 0 ? * SUN");
            ZonedDateTime next = nextFireAfter(trigger, Instant.now(), BERLIN);
            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(next.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        void listsAllSevenDays() {
            Timing.Weekly everyDay = new Timing.Weekly(new TreeSet<>(EnumSet.allOf(DayOfWeek.class)), LocalTime.of(23, 59));

            assertThat(build(everyDay, BERLIN).getCronExpression()).isEqualTo("0 59 23 ? * MON,TUE,WED,THU,FRI,SAT,SUN");
        }

        @Test
        void firesOnlyOnTheChosenDaysAtTheChosenLocalTime() {
            CronTrigger trigger = build(weekly(LocalTime.of(8, 30), DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), BERLIN);

            Instant cursor = Instant.now();
            for (int i = 0; i < 6; i++) {
                ZonedDateTime fire = nextFireAfter(trigger, cursor, BERLIN);
                assertThat(fire.getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
                assertThat(fire.toLocalTime()).isEqualTo(LocalTime.of(8, 30));
                cursor = fire.toInstant();
            }
        }

        @Test
        void usesTheSchedulesTimeZone() {
            CronTrigger trigger = build(weekly(LocalTime.of(8, 30), DayOfWeek.MONDAY), ZoneId.of("Asia/Tokyo"));

            assertThat(trigger.getTimeZone().toZoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        }

        @Test
        void keepsTheSameLocalTimeAcrossADaylightSavingChange() {
            ZoneId newYork = ZoneId.of("America/New_York");
            Timing.Weekly everyDay = new Timing.Weekly(new TreeSet<>(EnumSet.allOf(DayOfWeek.class)), LocalTime.of(8, 30));
            CronTrigger trigger = build(everyDay, newYork);
            ZoneOffsetTransition clockChange = newYork.getRules().nextTransition(Instant.now());

            ZonedDateTime before = nextFireAfter(trigger, clockChange.getInstant().minus(Duration.ofDays(2)), newYork);
            ZonedDateTime after = nextFireAfter(trigger, clockChange.getInstant().plus(Duration.ofDays(1)), newYork);

            assertThat(before.getOffset()).isNotEqualTo(after.getOffset());
            assertThat(before.toLocalTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(after.toLocalTime()).isEqualTo(LocalTime.of(8, 30));
        }

        @Test
        void skipsMissedRuns() {
            assertThat(build(weekly(LocalTime.NOON, DayOfWeek.FRIDAY), BERLIN).getMisfireInstruction())
                    .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        }

        private static Timing.Weekly weekly(LocalTime time, DayOfWeek... days) {
            return new Timing.Weekly(new TreeSet<>(List.of(days)), time);
        }

        private CronTrigger build(Timing.Weekly timing, ZoneId zone) {
            return (CronTrigger) factory.build(KEY, timing, zone);
        }
    }

    @Nested
    class Cron {

        private final CronTrigger trigger = (CronTrigger) factory.build(KEY, new Timing.Cron("0 0/5 * * * ?"), BERLIN);

        @Test
        void usesTheExpressionAsGiven() {
            assertThat(trigger.getCronExpression()).isEqualTo("0 0/5 * * * ?");
        }

        @Test
        void usesTheSchedulesTimeZone() {
            assertThat(trigger.getTimeZone().toZoneId()).isEqualTo(BERLIN);
        }

        @Test
        void skipsMissedRuns() {
            assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        }
    }

    private static ZonedDateTime nextFireAfter(Trigger trigger, Instant moment, ZoneId zone) {
        return trigger.getFireTimeAfter(Date.from(moment)).toInstant().atZone(zone);
    }
}
