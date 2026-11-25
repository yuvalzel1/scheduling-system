package com.schedulingsystem.scheduler.task;

import java.util.List;

/**
 * What a task tells the outside world about itself.
 *
 * @param key         stable identifier stored with each schedule
 * @param name        what the user sees in the task list
 * @param description a short explanation of what the task does
 * @param parameters  the inputs the task accepts
 */
public record TaskDefinition(String key, String name, String description, List<ParameterDefinition> parameters) {

    public TaskDefinition {
        parameters = List.copyOf(parameters);
    }
}
