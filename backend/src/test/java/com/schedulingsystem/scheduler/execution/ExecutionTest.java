package com.schedulingsystem.scheduler.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExecutionTest {

    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void aRunWithoutAnErrorSucceeded() {
        Execution execution = new Execution(UUID.randomUUID(), "log", START, START.plusMillis(40), null);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getErrorMessage()).isNull();
    }

    @Test
    void aRunWithAnErrorFailed() {
        Execution execution = new Execution(UUID.randomUUID(), "log", START, START.plusMillis(40), "Boom: no disk");

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).isEqualTo("Boom: no disk");
    }

    @Test
    void describesAFailureByItsKindAndWhatItSaid() {
        assertThat(ExecutionHistory.describe(new IllegalStateException("no disk left")))
                .isEqualTo("IllegalStateException: no disk left");
    }

    @Test
    void describesAFailureWithoutAMessageByItsKindAlone() {
        assertThat(ExecutionHistory.describe(new NullPointerException())).isEqualTo("NullPointerException");
        assertThat(ExecutionHistory.describe(new IllegalStateException("  "))).isEqualTo("IllegalStateException");
    }
}
