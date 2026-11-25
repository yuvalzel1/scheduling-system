package com.schedulingsystem.scheduler.schedule.quartz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.schedulingsystem.scheduler.schedule.IntervalUnit;
import com.schedulingsystem.scheduler.schedule.Schedule;
import com.schedulingsystem.scheduler.schedule.ScheduleRepository;
import com.schedulingsystem.scheduler.schedule.Timing;
import com.schedulingsystem.scheduler.support.LogCapture;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskDefinition;
import com.schedulingsystem.scheduler.task.TaskRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduledTaskRunnerTest {

    private static final UUID ID = UUID.randomUUID();

    private final ScheduleRepository repository = mock(ScheduleRepository.class);
    private final Task task = mock(Task.class);
    private ScheduledTaskRunner runner;

    @BeforeEach
    void setUp() {
        when(task.definition()).thenReturn(new TaskDefinition("demo", "Demo Task", "", List.of()));
        runner = new ScheduledTaskRunner(repository, new TaskRegistry(List.of(task)));
    }

    @Test
    void runsTheSchedulesTaskWithTheSavedInputs() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(schedule("demo", Map.of("message", "hello", "count", 3))));

        runner.run(ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputs = ArgumentCaptor.forClass(Map.class);
        verify(task).execute(inputs.capture());
        assertThat(inputs.getValue()).containsOnly(Map.entry("message", "hello"), Map.entry("count", 3));
        assertThat(inputs.getValue().get("count")).isInstanceOf(Number.class);
    }

    @Test
    void skipsQuietlyWhenTheScheduleRowIsGone() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        try (LogCapture logs = LogCapture.of(ScheduledTaskRunner.class)) {
            assertThatCode(() -> runner.run(ID)).doesNotThrowAnyException();

            assertThat(logs.lines()).containsExactly("WARN Schedule " + ID + " no longer exists; skipping this run");
        }
        verify(task, never()).execute(any());
    }

    @Test
    void skipsWhenTheTaskIsNotKnownAnyMore() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(schedule("removed-task", Map.of())));

        try (LogCapture logs = LogCapture.of(ScheduledTaskRunner.class)) {
            assertThatCode(() -> runner.run(ID)).doesNotThrowAnyException();

            assertThat(logs.lines()).singleElement().asString().startsWith("ERROR ").contains("unknown task 'removed-task'");
        }
        verify(task, never()).execute(any());
    }

    @Test
    void logsAFailingTaskAndDoesNotLetTheErrorEscape() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(schedule("demo", Map.of())));
        doThrow(new IllegalStateException("boom")).when(task).execute(any());

        try (LogCapture logs = LogCapture.of(ScheduledTaskRunner.class)) {
            assertThatCode(() -> runner.run(ID)).doesNotThrowAnyException();

            assertThat(logs.lines()).anySatisfy(line ->
                    assertThat(line).startsWith("ERROR ").contains("Task 'demo' failed for schedule " + ID));
        }
    }

    private static Schedule schedule(String taskKey, Map<String, Object> inputs) {
        Schedule schedule = new Schedule(Instant.parse("2026-06-01T12:00:00Z"));
        schedule.setTaskKey(taskKey);
        schedule.setTimeZone("UTC");
        schedule.setTiming(new Timing.Interval(1, IntervalUnit.HOURS));
        schedule.setParameters(inputs);
        return schedule;
    }
}
