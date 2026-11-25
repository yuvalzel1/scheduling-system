package com.schedulingsystem.scheduler.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.common.NotFoundException;
import com.schedulingsystem.scheduler.execution.ExecutionHistory;
import com.schedulingsystem.scheduler.execution.ExecutionResponse;
import com.schedulingsystem.scheduler.task.TaskParameterValidator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, changes and removes schedules.
 *
 * <p>Every change touches two things: our {@code schedules} row and the schedule's alarm. Both use
 * the same database connection inside one transaction, so either both happen or neither does.
 */
@Service
public class ScheduleService {

    private final ScheduleRepository repository;
    private final ScheduleValidator validator;
    private final ScheduleAlarms alarms;
    private final ExecutionHistory executionHistory;
    private final Clock clock;

    public ScheduleService(ScheduleRepository repository, ScheduleValidator validator,
            ScheduleAlarms alarms, ExecutionHistory executionHistory, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.alarms = alarms;
        this.executionHistory = executionHistory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list() {
        Map<UUID, ExecutionResponse> newestRuns = executionHistory.newestOfEachSchedule();
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(schedule -> ScheduleResponse.from(schedule, newestRuns.get(schedule.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID id) {
        return withNewestRun(find(id));
    }

    /** The run history of one schedule, newest first. */
    @Transactional(readOnly = true)
    public List<ExecutionResponse> executions(UUID id) {
        find(id);
        return executionHistory.of(id);
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        validator.validate(request);

        Schedule schedule = new Schedule(now());
        apply(request, schedule);
        repository.save(schedule);
        alarms.register(schedule);

        return ScheduleResponse.from(schedule, null);
    }

    @Transactional
    public ScheduleResponse update(UUID id, ScheduleRequest request) {
        Schedule schedule = find(id);
        validator.validate(request);

        apply(request, schedule);
        schedule.setUpdatedAt(now());
        alarms.register(schedule);

        return withNewestRun(schedule);
    }

    @Transactional
    public void delete(UUID id) {
        Schedule schedule = find(id);
        repository.delete(schedule);
        alarms.unregister(id);
    }

    /** Cut to what the database column keeps, so a saved schedule reads back exactly as returned. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private ScheduleResponse withNewestRun(Schedule schedule) {
        return ScheduleResponse.from(schedule, executionHistory.newestOf(schedule.getId()).orElse(null));
    }

    private Schedule find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No schedule with id " + id));
    }

    private static void apply(ScheduleRequest request, Schedule schedule) {
        schedule.setTaskKey(request.taskKey());
        schedule.setTiming(request.timing());
        schedule.setTimeZone(request.timeZone());
        schedule.setParameters(withoutMissingValues(request.parameters()));
    }

    /** An optional input sent as null or as empty text is the same as not sending it. */
    private static Map<String, Object> withoutMissingValues(Map<String, Object> parameters) {
        Map<String, Object> result = new LinkedHashMap<>();
        parameters.forEach((name, value) -> {
            if (!TaskParameterValidator.isMissing(value)) {
                result.put(name, value);
            }
        });
        return result;
    }
}
