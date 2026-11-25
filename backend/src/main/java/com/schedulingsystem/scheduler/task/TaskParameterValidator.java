package com.schedulingsystem.scheduler.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.schedulingsystem.scheduler.common.FieldViolation;

import org.springframework.stereotype.Component;

/** Checks the values given for a task against the inputs the task declares. */
@Component
public class TaskParameterValidator {

    public static final String FIELD_PREFIX = "parameters.";

    public List<FieldViolation> validate(TaskDefinition task, Map<String, Object> values) {
        List<FieldViolation> violations = new ArrayList<>();

        for (ParameterDefinition parameter : task.parameters()) {
            Object value = values.get(parameter.name());
            if (isMissing(value)) {
                if (parameter.required()) {
                    violations.add(violation(parameter.name(), parameter.label() + " is required"));
                }
                continue;
            }
            if (!hasType(value, parameter.type())) {
                violations.add(violation(parameter.name(), parameter.label() + " must be " + describe(parameter.type())));
            }
        }

        Set<String> known = task.parameters().stream().map(ParameterDefinition::name).collect(Collectors.toSet());
        values.keySet().stream()
                .filter(name -> !known.contains(name))
                .forEach(name -> violations.add(violation(name, "'" + name + "' is not an input of " + task.name())));

        return violations;
    }

    /** Not given, given as null, or given as text with nothing in it. */
    public static boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private static boolean hasType(Object value, ParameterType type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
        };
    }

    private static String describe(ParameterType type) {
        return switch (type) {
            case STRING -> "text";
            case NUMBER -> "a number";
        };
    }

    private static FieldViolation violation(String parameterName, String message) {
        return new FieldViolation(FIELD_PREFIX + parameterName, message);
    }
}
