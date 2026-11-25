package com.schedulingsystem.scheduler.common;

import java.util.List;

/** The request was understood but its content is not acceptable. Becomes a 400 response. */
public class ValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super("Validation failed: " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
