package com.schedulingsystem.scheduler.schedule;

import java.text.ParseException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import com.schedulingsystem.scheduler.common.FieldViolation;
import com.schedulingsystem.scheduler.common.ValidationException;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskParameterValidator;
import com.schedulingsystem.scheduler.task.TaskRegistry;

import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Decides whether a schedule request is acceptable: the task exists, its inputs match what the
 * task declares, the time zone is real, and the timing can actually fire.
 * All problems are collected and reported together, one per field.
 */
@Component
public class ScheduleValidator {

    static final int MAX_CRON_LENGTH = 200;

    private static final String CRON_FORMAT_HINT =
            "Quartz cron needs 6 or 7 parts and starts with seconds, for example: 0 0/5 * * * ?";

    private final TaskRegistry taskRegistry;
    private final TaskParameterValidator parameterValidator;
    private final Clock clock;

    public ScheduleValidator(TaskRegistry taskRegistry, TaskParameterValidator parameterValidator, Clock clock) {
        this.taskRegistry = taskRegistry;
        this.parameterValidator = parameterValidator;
        this.clock = clock;
    }

    /** @throws ValidationException listing every problem found */
    public void validate(ScheduleRequest request) {
        List<FieldViolation> violations = new ArrayList<>();

        validateTask(request, violations);
        Optional<ZoneId> zone = validateTimeZone(request.timeZone(), violations);
        validateTiming(request.timing(), zone.orElse(ZoneOffset.UTC), violations);

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void validateTask(ScheduleRequest request, List<FieldViolation> violations) {
        if (request.taskKey() == null || request.taskKey().isBlank()) {
            violations.add(new FieldViolation("taskKey", "Task is required"));
            return;
        }
        Optional<Task> task = taskRegistry.find(request.taskKey());
        if (task.isEmpty()) {
            violations.add(new FieldViolation("taskKey", "Unknown task '" + request.taskKey() + "'"));
            return;
        }
        violations.addAll(parameterValidator.validate(task.get().definition(), request.parameters()));
    }

    private Optional<ZoneId> validateTimeZone(String timeZone, List<FieldViolation> violations) {
        try {
            return Optional.of(ZoneId.of(timeZone));
        } catch (DateTimeException e) {
            violations.add(new FieldViolation("timeZone", "Unknown time zone '" + timeZone + "'"));
            return Optional.empty();
        }
    }

    private void validateTiming(Timing timing, ZoneId zone, List<FieldViolation> violations) {
        switch (timing) {
            case null -> violations.add(new FieldViolation("timing", "Timing is required"));
            case Timing.OneTime oneTime -> validateOneTime(oneTime, violations);
            case Timing.Interval interval -> validateInterval(interval, violations);
            case Timing.Weekly weekly -> validateWeekly(weekly, violations);
            case Timing.Cron cron -> validateCron(cron, zone, violations);
        }
    }

    private void validateOneTime(Timing.OneTime timing, List<FieldViolation> violations) {
        if (timing.runAt() == null) {
            violations.add(new FieldViolation("timing.runAt", "Date and time are required"));
        } else if (!timing.runAt().isAfter(clock.instant())) {
            violations.add(new FieldViolation("timing.runAt", "Date and time must be in the future"));
        }
    }

    private void validateInterval(Timing.Interval timing, List<FieldViolation> violations) {
        if (timing.every() == null) {
            violations.add(new FieldViolation("timing.every", "Interval is required"));
        } else if (timing.every() < 1) {
            violations.add(new FieldViolation("timing.every", "Interval must be a whole number, 1 or more"));
        }
        if (timing.unit() == null) {
            violations.add(new FieldViolation("timing.unit", "Unit is required (MINUTES or HOURS)"));
        }
    }

    private void validateWeekly(Timing.Weekly timing, List<FieldViolation> violations) {
        if (timing.days() == null || timing.days().isEmpty()) {
            violations.add(new FieldViolation("timing.days", "Pick at least one day"));
        }
        if (timing.time() == null) {
            violations.add(new FieldViolation("timing.time", "Time is required"));
        }
    }

    private void validateCron(Timing.Cron timing, ZoneId zone, List<FieldViolation> violations) {
        cronProblem(timing.expression(), zone)
                .ifPresent(message -> violations.add(new FieldViolation("timing.expression", message)));
    }

    /** Quartz's own wording, except for its most common and most cryptic complaint. */
    private static String describe(ParseException e) {
        String reason = String.valueOf(e.getMessage()).strip().replaceAll("\\.+$", "");
        if (reason.contains("day-of-week AND a day-of-month")) {
            return "Day-of-month and day-of-week cannot both be set. Put ? in one of them, for example: 0 0 12 * * ?";
        }
        return "Not a valid cron expression: " + reason + ". " + CRON_FORMAT_HINT;
    }

    private Optional<String> cronProblem(String expression, ZoneId zone) {
        if (expression == null || expression.isBlank()) {
            return Optional.of("Cron expression is required");
        }
        if (expression.length() > MAX_CRON_LENGTH) {
            return Optional.of("Cron expression is too long (at most " + MAX_CRON_LENGTH + " characters)");
        }
        if (expression.split("\\s+").length == 5) {
            return Optional.of("This looks like a Unix cron with 5 parts. " + CRON_FORMAT_HINT);
        }

        CronExpression cron;
        try {
            cron = new CronExpression(expression);
        } catch (ParseException e) {
            return Optional.of(describe(e));
        }

        cron.setTimeZone(TimeZone.getTimeZone(zone));
        if (cron.getNextValidTimeAfter(Date.from(clock.instant())) == null) {
            return Optional.of("This cron expression will never fire");
        }
        return Optional.empty();
    }
}
