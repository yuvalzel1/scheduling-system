package com.schedulingsystem.scheduler.common;

/** One problem with one field of a request, for example {@code parameters.message}. */
public record FieldViolation(String field, String message) {
}
