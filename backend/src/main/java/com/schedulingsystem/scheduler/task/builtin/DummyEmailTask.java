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

/** Pretends to send an email. Nothing leaves the server: the email is only written to the log. */
@Component
@Order(2)
public class DummyEmailTask implements Task {

    public static final String KEY = "email";
    public static final String TO = "to";
    public static final String SUBJECT = "subject";
    public static final String BODY = "body";

    private static final Logger log = LoggerFactory.getLogger(DummyEmailTask.class);

    private static final TaskDefinition DEFINITION = new TaskDefinition(
            KEY,
            "Dummy Email Task",
            "Pretends to send an email. It is only written to the application log.",
            List.of(
                    ParameterDefinition.required(TO, "To", ParameterType.STRING, "Who receives the email."),
                    ParameterDefinition.required(SUBJECT, "Subject", ParameterType.STRING, "The subject line."),
                    ParameterDefinition.optional(BODY, "Body", ParameterType.STRING, "The text of the email.")));

    @Override
    public TaskDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void execute(Map<String, Object> parameters) {
        Object body = parameters.getOrDefault(BODY, "");
        log.info("Dummy Email Task: pretending to send email to='{}' subject='{}' body='{}'",
                parameters.get(TO), parameters.get(SUBJECT), body);
    }
}
