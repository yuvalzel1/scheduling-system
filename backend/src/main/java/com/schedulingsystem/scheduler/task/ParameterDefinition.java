package com.schedulingsystem.scheduler.task;

/**
 * Describes one input of a task.
 *
 * @param name        the key used in a schedule's {@code parameters}
 * @param label       what the user sees next to the field
 * @param type        the kind of value expected
 * @param required    whether a schedule must provide it
 * @param description a short hint for the user
 */
public record ParameterDefinition(String name, String label, ParameterType type, boolean required, String description) {

    public static ParameterDefinition required(String name, String label, ParameterType type, String description) {
        return new ParameterDefinition(name, label, type, true, description);
    }

    public static ParameterDefinition optional(String name, String label, ParameterType type, String description) {
        return new ParameterDefinition(name, label, type, false, description);
    }
}
