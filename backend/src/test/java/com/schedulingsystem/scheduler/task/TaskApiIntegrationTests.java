package com.schedulingsystem.scheduler.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.schedulingsystem.scheduler.IntegrationTest;

import org.junit.jupiter.api.Test;

class TaskApiIntegrationTests extends IntegrationTest {

    @Test
    void listsTheThreePredefinedTasks() {
        ApiResponse response = get("/api/tasks");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.<List<String>>read("$[*].key")).containsExactly("log", "email", "db-query");
        assertThat(response.<List<String>>read("$[*].name"))
                .containsExactly("Log Task", "Dummy Email Task", "DB Query Task");
        assertThat(response.<List<String>>read("$[*].description")).allSatisfy(text -> assertThat(text).isNotBlank());
    }

    @Test
    void describesTheInputsOfEachTask() {
        ApiResponse response = get("/api/tasks");

        assertThat(response.<List<Map<String, Object>>>read("$[0].parameters")).singleElement().satisfies(message -> {
            assertThat(message).containsEntry("name", "message").containsEntry("label", "Message")
                    .containsEntry("type", "STRING").containsEntry("required", true).containsKey("description");
        });

        assertThat(response.<List<String>>read("$[1].parameters[*].name")).containsExactly("to", "subject", "body");
        assertThat(response.<List<Boolean>>read("$[1].parameters[*].required")).containsExactly(true, true, false);
        assertThat(response.<List<String>>read("$[1].parameters[*].type")).containsOnly("STRING");

        assertThat(response.<List<Map<String, Object>>>read("$[2].parameters")).singleElement().satisfies(limit -> {
            assertThat(limit).containsEntry("name", "limit").containsEntry("type", "NUMBER").containsEntry("required", false);
        });
    }

    @Test
    void doesNotAllowCreatingOrChangingTasks() {
        assertThat(post("/api/tasks", "{}").status()).isEqualTo(405);
        assertThat(put("/api/tasks", "{}").status()).isEqualTo(405);
        assertThat(delete("/api/tasks").status()).isEqualTo(405);
    }
}
