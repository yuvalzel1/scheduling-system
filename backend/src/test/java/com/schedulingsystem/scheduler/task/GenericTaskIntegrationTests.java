package com.schedulingsystem.scheduler.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schedulingsystem.scheduler.IntegrationTest;
import com.schedulingsystem.scheduler.support.RecordingTask;

import org.junit.jupiter.api.Test;

/**
 * Adding a task is meant to be "write one class". This checks that claim from end to end with
 * {@link RecordingTask}, a task that lives in the test sources and that no application code
 * mentions: the API lists it, its declared inputs are enforced, and Quartz runs it.
 */
class GenericTaskIntegrationTests extends IntegrationTest {

    @Test
    void aNewTaskIsListedWithTheInputsItDeclares() {
        List<Map<String, Object>> tasks = get("/api/tasks").read("$[?(@.key == 'test-recorder')]");

        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task).containsEntry("name", "Recording Task");
            assertThat(task.get("parameters")).isEqualTo(List.of(
                    Map.of("name", "label", "label", "Label", "type", "STRING", "required", true,
                            "description", "Any text."),
                    Map.of("name", "count", "label", "Count", "type", "NUMBER", "required", false,
                            "description", "Any number.")));
        });
    }

    @Test
    void theInputsOfANewTaskAreEnforcedFromItsDeclarationAlone() {
        assertThat(post(SCHEDULES, schedule(RecordingTask.KEY, EVERY_15_MINUTES, "{}")).errorFields())
                .as("required input missing")
                .containsExactly("parameters.label");

        assertThat(post(SCHEDULES, schedule(RecordingTask.KEY, EVERY_15_MINUTES, """
                {"label": "x", "count": "three"}""")).errorFields())
                .as("text where a number is declared")
                .containsExactly("parameters.count");

        assertThat(post(SCHEDULES, schedule(RecordingTask.KEY, EVERY_15_MINUTES, """
                {"label": "x", "message": "an input of another task"}""")).errorFields())
                .as("an input this task does not declare")
                .containsExactly("parameters.message");

        assertThat(scheduleRepository.count()).isZero();
    }

    @Test
    void aNewTaskIsScheduledAndRunByQuartzWithItsSavedInputs() {
        UUID id = create(schedule(RecordingTask.KEY, oneTimeAt(Instant.now().plusSeconds(2)), """
                {"label": "first run", "count": 3}"""));

        await().atMost(Duration.ofSeconds(15)).until(() -> !recordingTask.runs().isEmpty());

        assertThat(recordingTask.runs()).containsExactly(Map.of("label", "first run", "count", 3));
        assertThat(get(SCHEDULES + "/" + id).<String>read("$.taskKey")).isEqualTo(RecordingTask.KEY);
    }
}
