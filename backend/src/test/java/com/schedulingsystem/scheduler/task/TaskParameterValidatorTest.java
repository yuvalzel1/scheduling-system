package com.schedulingsystem.scheduler.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.schedulingsystem.scheduler.common.FieldViolation;

import org.junit.jupiter.api.Test;

class TaskParameterValidatorTest {

    private static final TaskDefinition TASK = new TaskDefinition("demo", "Demo Task", "For tests", List.of(
            ParameterDefinition.required("title", "Title", ParameterType.STRING, ""),
            ParameterDefinition.optional("note", "Note", ParameterType.STRING, ""),
            ParameterDefinition.required("count", "Count", ParameterType.NUMBER, ""),
            ParameterDefinition.optional("ratio", "Ratio", ParameterType.NUMBER, "")));

    private static final TaskDefinition TASK_WITHOUT_REQUIRED_INPUTS = new TaskDefinition("free", "Free Task", "",
            List.of(ParameterDefinition.optional("note", "Note", ParameterType.STRING, "")));

    private final TaskParameterValidator validator = new TaskParameterValidator();

    @Test
    void acceptsAllRequiredInputs() {
        assertThat(validator.validate(TASK, Map.of("title", "Hello", "count", 3))).isEmpty();
    }

    @Test
    void acceptsOptionalInputsWhenGivenWithTheRightType() {
        assertThat(validator.validate(TASK, Map.of("title", "Hello", "count", 3, "note", "x", "ratio", 0.5))).isEmpty();
    }

    @Test
    void acceptsNoInputsAtAllWhenNothingIsRequired() {
        assertThat(validator.validate(TASK_WITHOUT_REQUIRED_INPUTS, Map.of())).isEmpty();
    }

    @Test
    void reportsAMissingRequiredInputOnItsField() {
        assertThat(validator.validate(TASK, Map.of("count", 3)))
                .containsExactly(new FieldViolation("parameters.title", "Title is required"));
    }

    @Test
    void treatsNullAsMissing() {
        Map<String, Object> values = new HashMap<>();
        values.put("title", null);
        values.put("count", 3);

        assertThat(validator.validate(TASK, values))
                .containsExactly(new FieldViolation("parameters.title", "Title is required"));
    }

    @Test
    void treatsEmptyTextAsMissing() {
        assertThat(validator.validate(TASK, Map.of("title", "", "count", 3)))
                .containsExactly(new FieldViolation("parameters.title", "Title is required"));
    }

    @Test
    void treatsTextOfOnlySpacesAsMissing() {
        assertThat(validator.validate(TASK, Map.of("title", "   ", "count", 3)))
                .containsExactly(new FieldViolation("parameters.title", "Title is required"));
    }

    @Test
    void acceptsAnOptionalInputLeftEmpty() {
        assertThat(validator.validate(TASK, Map.of("title", "Hello", "count", 3, "note", ""))).isEmpty();
    }

    @Test
    void rejectsANumberWhereTextIsExpected() {
        assertThat(validator.validate(TASK, Map.of("title", 42, "count", 3)))
                .containsExactly(new FieldViolation("parameters.title", "Title must be text"));
    }

    @Test
    void rejectsTextWhereANumberIsExpected() {
        assertThat(validator.validate(TASK, Map.of("title", "Hello", "count", "3")))
                .containsExactly(new FieldViolation("parameters.count", "Count must be a number"));
    }

    @Test
    void rejectsOtherJsonTypes() {
        assertThat(validator.validate(TASK, Map.of("title", List.of("a"), "count", true)))
                .extracting(FieldViolation::field)
                .containsExactly("parameters.title", "parameters.count");
    }

    @Test
    void rejectsAnInputTheTaskDoesNotKnow() {
        assertThat(validator.validate(TASK, Map.of("title", "Hello", "count", 3, "colour", "red")))
                .containsExactly(new FieldViolation("parameters.colour", "'colour' is not an input of Demo Task"));
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThat(validator.validate(TASK, Map.of("count", "many", "colour", "red")))
                .extracting(FieldViolation::field)
                .containsExactlyInAnyOrder("parameters.title", "parameters.count", "parameters.colour");
    }
}
