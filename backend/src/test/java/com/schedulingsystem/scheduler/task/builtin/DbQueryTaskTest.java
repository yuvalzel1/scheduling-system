package com.schedulingsystem.scheduler.task.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.schedulingsystem.scheduler.task.ParameterDefinition;
import com.schedulingsystem.scheduler.task.ParameterType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The query itself runs against a real database in {@code DbQueryTaskIntegrationTests}. */
class DbQueryTaskTest {

    @Test
    void usesTheDefaultWhenNoLimitIsGiven() {
        assertThat(DbQueryTask.effectiveLimit(null)).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "5, 5", "100, 100", "0, 1", "-3, 1", "101, 100", "5000000000, 100"})
    void keepsTheLimitBetweenOneAndOneHundred(long given, int expected) {
        assertThat(DbQueryTask.effectiveLimit(given)).isEqualTo(expected);
    }

    @Test
    void dropsTheFractionOfADecimalLimit() {
        assertThat(DbQueryTask.effectiveLimit(2.9)).isEqualTo(2);
    }

    @Test
    void declaresOneOptionalNumberInput() {
        ParameterDefinition limit = new DbQueryTask(null).definition().parameters().getFirst();

        assertThat(limit.name()).isEqualTo("limit");
        assertThat(limit.type()).isEqualTo(ParameterType.NUMBER);
        assertThat(limit.required()).isFalse();
    }
}
