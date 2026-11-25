package com.schedulingsystem.scheduler.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The run history: what ran, when, and how it ended.
 *
 * <p>Only the newest {@value #KEPT_PER_SCHEDULE} runs of each schedule are kept, so a schedule
 * that fires every minute cannot grow the table without limit.
 */
@Service
public class ExecutionHistory {

    public static final int KEPT_PER_SCHEDULE = 100;

    static final int MAX_ERROR_LENGTH = 2000;

    private final ExecutionRepository repository;

    public ExecutionHistory(ExecutionRepository repository) {
        this.repository = repository;
    }

    /**
     * Writes down one finished run and drops the schedule's runs that are now too old to keep.
     *
     * @param failure why it failed, or null when it succeeded
     */
    @Transactional
    public void record(UUID scheduleId, String taskKey, Instant startedAt, Instant finishedAt, String failure) {
        repository.saveAndFlush(new Execution(scheduleId, taskKey, startedAt, finishedAt, shorten(failure)));
        repository.deleteAllButNewest(scheduleId, KEPT_PER_SCHEDULE);
    }

    /** The runs of one schedule, newest first. */
    @Transactional(readOnly = true)
    public List<ExecutionResponse> of(UUID scheduleId) {
        return repository.findByScheduleIdOrderByStartedAtDesc(scheduleId).stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionResponse> newestOf(UUID scheduleId) {
        return repository.findFirstByScheduleIdOrderByStartedAtDesc(scheduleId).map(ExecutionResponse::from);
    }

    /** The newest run of every schedule that has one, by schedule id. */
    @Transactional(readOnly = true)
    public Map<UUID, ExecutionResponse> newestOfEachSchedule() {
        return repository.findNewestOfEachSchedule().stream()
                .collect(Collectors.toMap(Execution::getScheduleId, ExecutionResponse::from));
    }

    /** What to show for a failed run: the kind of error and what it said. */
    public static String describe(Throwable failure) {
        String kind = failure.getClass().getSimpleName();
        String said = failure.getMessage();
        return (said == null || said.isBlank()) ? kind : kind + ": " + said;
    }

    private static String shorten(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
