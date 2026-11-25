package com.schedulingsystem.scheduler.task.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.schedulingsystem.scheduler.support.LogCapture;
import com.schedulingsystem.scheduler.task.ParameterDefinition;

import org.junit.jupiter.api.Test;

class DummyEmailTaskTest {

    private final DummyEmailTask task = new DummyEmailTask();

    @Test
    void logsRecipientSubjectAndBody() {
        try (LogCapture logs = LogCapture.of(DummyEmailTask.class)) {
            task.execute(Map.of("to", "ada@example.com", "subject", "Weekly report", "body", "All good."));

            assertThat(logs.lines()).singleElement().asString()
                    .startsWith("INFO ")
                    .contains("to='ada@example.com'", "subject='Weekly report'", "body='All good.'");
        }
    }

    @Test
    void worksWithoutABody() {
        try (LogCapture logs = LogCapture.of(DummyEmailTask.class)) {
            task.execute(Map.of("to", "ada@example.com", "subject", "Ping"));

            assertThat(logs.lines()).singleElement().asString()
                    .contains("to='ada@example.com'", "subject='Ping'", "body=''");
        }
    }

    @Test
    void requiresRecipientAndSubjectButNotBody() {
        assertThat(task.definition().key()).isEqualTo("email");
        assertThat(task.definition().parameters())
                .extracting(ParameterDefinition::name, ParameterDefinition::required)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("to", true),
                        org.assertj.core.groups.Tuple.tuple("subject", true),
                        org.assertj.core.groups.Tuple.tuple("body", false));
    }
}
