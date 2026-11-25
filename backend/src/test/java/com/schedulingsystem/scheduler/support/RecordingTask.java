package com.schedulingsystem.scheduler.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.schedulingsystem.scheduler.task.ParameterDefinition;
import com.schedulingsystem.scheduler.task.ParameterType;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskDefinition;

/**
 * A task that exists only in the tests. Nothing in the application knows about it, which is the
 * point: if it can be listed, checked, scheduled and run, then adding a task really is "one class".
 * It remembers the inputs of every run.
 */
public class RecordingTask implements Task {

    public static final String KEY = "test-recorder";

    private static final TaskDefinition DEFINITION = new TaskDefinition(
            KEY,
            "Recording Task",
            "Test only: remembers what it was run with.",
            List.of(
                    ParameterDefinition.required("label", "Label", ParameterType.STRING, "Any text."),
                    ParameterDefinition.optional("count", "Count", ParameterType.NUMBER, "Any number.")));

    private final List<Map<String, Object>> runs = new CopyOnWriteArrayList<>();

    @Override
    public TaskDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void execute(Map<String, Object> parameters) {
        runs.add(Map.copyOf(parameters));
    }

    /** The inputs of each run so far, oldest first. */
    public List<Map<String, Object>> runs() {
        return List.copyOf(runs);
    }

    public void forgetRuns() {
        runs.clear();
    }
}
