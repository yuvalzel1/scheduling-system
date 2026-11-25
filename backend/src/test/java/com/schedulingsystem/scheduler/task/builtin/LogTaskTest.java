package com.schedulingsystem.scheduler.task.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.schedulingsystem.scheduler.support.LogCapture;
import com.schedulingsystem.scheduler.task.ParameterDefinition;
import com.schedulingsystem.scheduler.task.ParameterType;

import org.junit.jupiter.api.Test;

class LogTaskTest {

    private final LogTask task = new LogTask();

    @Test
    void writesTheMessageToTheLog() {
        try (LogCapture logs = LogCapture.of(LogTask.class)) {
            task.execute(Map.of("message", "hello from the test"));

            assertThat(logs.lines()).containsExactly("INFO Log Task: hello from the test");
        }
    }

    @Test
    void declaresOneRequiredTextInput() {
        assertThat(task.definition().key()).isEqualTo("log");
        assertThat(task.definition().parameters()).containsExactly(
                ParameterDefinition.required("message", "Message", ParameterType.STRING, "The text to write to the log."));
    }
}
