package com.schedulingsystem.scheduler.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TaskRegistryTest {

    @Test
    void findsATaskByItsKey() {
        Task first = task("first");
        TaskRegistry registry = new TaskRegistry(List.of(first, task("second")));

        assertThat(registry.find("first")).containsSame(first);
    }

    @Test
    void findsNothingForAnUnknownKey() {
        assertThat(new TaskRegistry(List.of(task("first"))).find("nope")).isEmpty();
    }

    @Test
    void listsDefinitionsInTheOrderGiven() {
        TaskRegistry registry = new TaskRegistry(List.of(task("b"), task("a")));

        assertThat(registry.definitions()).extracting(TaskDefinition::key).containsExactly("b", "a");
    }

    @Test
    void refusesTwoTasksWithTheSameKey() {
        assertThatThrownBy(() -> new TaskRegistry(List.of(task("same"), task("same"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    private static Task task(String key) {
        TaskDefinition definition = new TaskDefinition(key, key, "", List.of());
        return new Task() {
            @Override
            public TaskDefinition definition() {
                return definition;
            }

            @Override
            public void execute(Map<String, Object> parameters) {
                // nothing to do
            }
        };
    }
}
