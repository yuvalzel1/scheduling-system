package com.schedulingsystem.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.schedulingsystem.scheduler.common.FieldViolation;
import com.schedulingsystem.scheduler.common.ValidationException;
import com.schedulingsystem.scheduler.task.TaskParameterValidator;
import com.schedulingsystem.scheduler.task.TaskRegistry;
import com.schedulingsystem.scheduler.task.builtin.LogTask;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleValidatorTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Map<String, Object> VALID_INPUTS = Map.of("message", "hello");
    private static final Timing VALID_TIMING = new Timing.Interval(15, IntervalUnit.MINUTES);

    private final ScheduleValidator validator = new ScheduleValidator(
            new TaskRegistry(List.of(new LogTask())),
            new TaskParameterValidator(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    class Task {

        @Test
        void acceptsAKnownTaskWithItsInputs() {
            assertValid(request("log", VALID_TIMING, "UTC", VALID_INPUTS));
        }

        @Test
        void rejectsAnUnknownTask() {
            assertThat(violations(request("no-such-task", VALID_TIMING, "UTC", Map.of())))
                    .containsExactly(new FieldViolation("taskKey", "Unknown task 'no-such-task'"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        void rejectsAMissingTask(String taskKey) {
            assertThat(fields(request(taskKey, VALID_TIMING, "UTC", Map.of()))).containsExactly("taskKey");
        }

        @Test
        void checksTheInputsAgainstTheChosenTask() {
            assertThat(fields(request("log", VALID_TIMING, "UTC", Map.of()))).containsExactly("parameters.message");
        }

        @Test
        void treatsLeftOutInputsAsNone() {
            assertThat(fields(new ScheduleRequest("log", VALID_TIMING, "UTC", null)))
                    .containsExactly("parameters.message");
        }
    }

    @Nested
    class TimeZone {

        @ParameterizedTest
        @ValueSource(strings = {"UTC", "Asia/Jerusalem", "America/New_York", "+02:00"})
        void acceptsRealTimeZones(String zone) {
            assertValid(request("log", VALID_TIMING, zone, VALID_INPUTS));
        }

        @Test
        void rejectsAnUnknownTimeZone() {
            assertThat(violations(request("log", VALID_TIMING, "Mars/Olympus_Mons", VALID_INPUTS)))
                    .containsExactly(new FieldViolation("timeZone", "Unknown time zone 'Mars/Olympus_Mons'"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        void fallsBackToUtcWhenLeftOut(String zone) {
            ScheduleRequest request = request("log", VALID_TIMING, zone, VALID_INPUTS);

            assertThat(request.timeZone()).isEqualTo("UTC");
            assertValid(request);
        }
    }

    @Nested
    class AnyTiming {

        @Test
        void rejectsAMissingTiming() {
            assertThat(violations(request("log", null, "UTC", VALID_INPUTS)))
                    .containsExactly(new FieldViolation("timing", "Timing is required"));
        }

        @Test
        void reportsProblemsFromAllPartsTogether() {
            ScheduleRequest request = request("log", new Timing.Interval(0, null), "Nowhere/Land", Map.of());

            assertThat(fields(request)).containsExactlyInAnyOrder(
                    "parameters.message", "timeZone", "timing.every", "timing.unit");
        }
    }

    @Nested
    class OneTime {

        @Test
        void acceptsAFutureMoment() {
            assertValid(withTiming(new Timing.OneTime(NOW.plusSeconds(1))));
        }

        @Test
        void rejectsAPastMoment() {
            assertThat(violations(withTiming(new Timing.OneTime(NOW.minusSeconds(1)))))
                    .containsExactly(new FieldViolation("timing.runAt", "Date and time must be in the future"));
        }

        @Test
        void rejectsThisVeryMoment() {
            assertThat(fields(withTiming(new Timing.OneTime(NOW)))).containsExactly("timing.runAt");
        }

        @Test
        void rejectsAMissingMoment() {
            assertThat(violations(withTiming(new Timing.OneTime(null))))
                    .containsExactly(new FieldViolation("timing.runAt", "Date and time are required"));
        }
    }

    @Nested
    class Interval {

        @ParameterizedTest
        @ValueSource(ints = {1, 15, 1440})
        void acceptsAWholeNumberOfOneOrMore(int every) {
            assertValid(withTiming(new Timing.Interval(every, IntervalUnit.MINUTES)));
            assertValid(withTiming(new Timing.Interval(every, IntervalUnit.HOURS)));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -15})
        void rejectsZeroAndNegativeNumbers(int every) {
            assertThat(violations(withTiming(new Timing.Interval(every, IntervalUnit.MINUTES))))
                    .containsExactly(new FieldViolation("timing.every", "Interval must be a whole number, 1 or more"));
        }

        @Test
        void rejectsAMissingNumber() {
            assertThat(fields(withTiming(new Timing.Interval(null, IntervalUnit.HOURS)))).containsExactly("timing.every");
        }

        @Test
        void rejectsAMissingUnit() {
            assertThat(fields(withTiming(new Timing.Interval(5, null)))).containsExactly("timing.unit");
        }
    }

    @Nested
    class Weekly {

        private static final LocalTime TIME = LocalTime.of(8, 30);

        @Test
        void acceptsOneDay() {
            assertValid(withTiming(new Timing.Weekly(new TreeSet<>(List.of(DayOfWeek.MONDAY)), TIME)));
        }

        @Test
        void acceptsAllSevenDays() {
            assertValid(withTiming(new Timing.Weekly(new TreeSet<>(EnumSet.allOf(DayOfWeek.class)), TIME)));
        }

        @Test
        void rejectsNoDays() {
            assertThat(violations(withTiming(new Timing.Weekly(new TreeSet<>(), TIME))))
                    .containsExactly(new FieldViolation("timing.days", "Pick at least one day"));
        }

        @Test
        void rejectsMissingDays() {
            assertThat(fields(withTiming(new Timing.Weekly(null, TIME)))).containsExactly("timing.days");
        }

        @Test
        void rejectsAMissingTime() {
            assertThat(violations(withTiming(new Timing.Weekly(new TreeSet<>(List.of(DayOfWeek.FRIDAY)), null))))
                    .containsExactly(new FieldViolation("timing.time", "Time is required"));
        }
    }

    @Nested
    class Cron {

        @ParameterizedTest
        @ValueSource(strings = {
                "0 0/5 * * * ?",       // every 5 minutes
                "0 30 8 ? * MON,WED",  // Monday and Wednesday at 08:30
                "0 0 12 1 * ?",        // first day of every month at noon
                "0 0 0 1 1 ? 2099",    // with the optional year part
                "  0 0 12 * * ?  "     // spaces around are trimmed
        })
        void acceptsValidQuartzExpressions(String expression) {
            assertValid(withTiming(new Timing.Cron(expression)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"not a cron at all", "0 0 12 * *", "61 0 12 * * ?", "0 0 25 * * ?", "0 0 12 * * *"})
        void rejectsInvalidExpressions(String expression) {
            assertThat(fields(withTiming(new Timing.Cron(expression)))).containsExactly("timing.expression");
        }

        @ParameterizedTest
        @ValueSource(strings = {"*/5 * * * *", "0 12 * * 1", "30 8 * * MON-FRI"})
        void explainsTheQuartzFormatWhenGivenAUnixCron(String unixCron) {
            assertThat(violations(withTiming(new Timing.Cron(unixCron))))
                    .singleElement()
                    .satisfies(violation -> {
                        assertThat(violation.field()).isEqualTo("timing.expression");
                        assertThat(violation.message())
                                .contains("Unix cron with 5 parts", "6 or 7 parts", "starts with seconds", "0 0/5 * * * ?");
                    });
        }

        @Test
        void explainsWhatToDoWhenBothDayPartsAreSet() {
            assertThat(violations(withTiming(new Timing.Cron("0 0 12 * * *"))))
                    .containsExactly(new FieldViolation("timing.expression",
                            "Day-of-month and day-of-week cannot both be set. Put ? in one of them, for example: 0 0 12 * * ?"));
        }

        @Test
        void passesOnQuartzsReasonForOtherMistakes() {
            assertThat(violations(withTiming(new Timing.Cron("0 0 25 * * ?"))))
                    .singleElement()
                    .satisfies(violation -> assertThat(violation.message())
                            .startsWith("Not a valid cron expression: ")
                            .contains("Hour values must be between 0 and 23")
                            .doesNotContain("..")
                            .endsWith("0 0/5 * * * ?"));
        }

        @Test
        void rejectsAnExpressionThatCanNeverFire() {
            assertThat(violations(withTiming(new Timing.Cron("0 0 0 1 1 ? 2020"))))
                    .containsExactly(new FieldViolation("timing.expression", "This cron expression will never fire"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void rejectsAMissingExpression(String expression) {
            assertThat(violations(withTiming(new Timing.Cron(expression))))
                    .containsExactly(new FieldViolation("timing.expression", "Cron expression is required"));
        }

        @Test
        void rejectsAnExpressionLongerThanTheColumn() {
            String tooLong = "0 " + "0,".repeat(100) + "0 * * * ?";

            assertThat(violations(withTiming(new Timing.Cron(tooLong))))
                    .singleElement()
                    .satisfies(violation -> assertThat(violation.message()).contains("too long"));
        }
    }

    private static ScheduleRequest request(String taskKey, Timing timing, String zone, Map<String, Object> inputs) {
        return new ScheduleRequest(taskKey, timing, zone, inputs);
    }

    private static ScheduleRequest withTiming(Timing timing) {
        return request("log", timing, "UTC", VALID_INPUTS);
    }

    private void assertValid(ScheduleRequest request) {
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    private List<FieldViolation> violations(ScheduleRequest request) {
        ValidationException thrown = catchThrowableOfType(ValidationException.class, () -> validator.validate(request));
        assertThat(thrown).as("expected the request to be rejected").isNotNull();
        return thrown.violations();
    }

    private List<String> fields(ScheduleRequest request) {
        return violations(request).stream().map(FieldViolation::field).toList();
    }
}
