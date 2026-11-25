package com.schedulingsystem.scheduler.task.builtin;

import java.util.List;
import java.util.Map;

import com.schedulingsystem.scheduler.task.ParameterDefinition;
import com.schedulingsystem.scheduler.task.ParameterType;
import com.schedulingsystem.scheduler.task.Task;
import com.schedulingsystem.scheduler.task.TaskDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Writes a message to the application log. */
@Component
@Order(1)
public class LogTask implements Task {

    public static final String KEY = "log";
    public static final String MESSAGE = "message";

    private static final Logger log = LoggerFactory.getLogger(LogTask.class);

    private static final TaskDefinition DEFINITION = new TaskDefinition(
            KEY,
            "Log Task",
            "Writes a message to the application log.",
            List.of(ParameterDefinition.required(MESSAGE, "Message", ParameterType.STRING, "The text to write to the log.")));

    @Override
    public TaskDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void execute(Map<String, Object> parameters) {
        log.info("Log Task: {}", parameters.get(MESSAGE));
    }
}
