package com.schedulingsystem.scheduler.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.common.NotFoundException;
import com.schedulingsystem.scheduler.schedule.quartz.QuartzScheduleRegistrar;
import com.schedulingsystem.scheduler.task.TaskParameterValidator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, changes and removes schedules.
 *
 * <p>Every change touches two things: our {@code schedules} row and the Quartz alarm. Both use the
 * same database connection inside one transaction, so either both happen or neither does.
 */
@Service
public class ScheduleService {

    private final ScheduleRepository repository;
    private final ScheduleValidator validator;
    private final QuartzScheduleRegistrar registrar;
    private final Clock clock;

    public ScheduleService(ScheduleRepository repository, ScheduleValidator validator,
            QuartzScheduleRegistrar registrar, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.registrar = registrar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID id) {
        return ScheduleResponse.from(find(id));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        validator.validate(request);

        Schedule schedule = new Schedule(now());
        apply(request, schedule);
        repository.save(schedule);
        registrar.register(schedule);

        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public ScheduleResponse update(UUID id, ScheduleRequest request) {
        Schedule schedule = find(id);
        validator.validate(request);

        apply(request, schedule);
        schedule.setUpdatedAt(now());
        registrar.register(schedule);

        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(UUID id) {
        Schedule schedule = find(id);
        repository.delete(schedule);
        registrar.unregister(id);
    }

    /** Cut to what the database column keeps, so a saved schedule reads back exactly as returned. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
