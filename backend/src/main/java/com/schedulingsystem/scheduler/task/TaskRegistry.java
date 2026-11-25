package com.schedulingsystem.scheduler.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** The fixed list of tasks, looked up by key. */
@Component
public class TaskRegistry {

    private final Map<String, Task> tasksByKey = new LinkedHashMap<>();

    public TaskRegistry(List<Task> tasks) {
        for (Task task : tasks) {
            String key = task.definition().key();
            if (tasksByKey.putIfAbsent(key, task) != null) {
                throw new IllegalStateException("Two tasks use the same key: " + key);
            }
        }
    }

    public Optional<Task> find(String key) {
        return Optional.ofNullable(tasksByKey.get(key));
    }

    public List<TaskDefinition> definitions() {
        return tasksByKey.values().stream().map(Task::definition).toList();
    }
}
