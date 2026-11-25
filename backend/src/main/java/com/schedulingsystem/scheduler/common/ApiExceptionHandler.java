package com.schedulingsystem.scheduler.common;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;

/**
 * Turns every error into an RFC 9457 problem detail. Validation errors carry an extra
 * {@code errors} list of {@code {field, message}} so a form can show each one next to its input.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    ProblemDetail handleValidation(ValidationException ex) {
        return validationProblem(ex.violations());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling a request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server. Please try again.");
        problem.setTitle("Server error");
        return problem;
    }

    /**
     * The JSON could not be read into the request object: broken JSON, or a value of the wrong
     * shape (text where a number belongs, an unknown timing kind, a time like 25:00). When Jackson
     * tells us which field it was, we report it as a normal field error.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = unreadableField(ex)
                .map(field -> validationProblem(List.of(new FieldViolation(field, "Invalid value"))))
                .orElseGet(() -> {
                    ProblemDetail malformed = ProblemDetail.forStatusAndDetail(
                            HttpStatus.BAD_REQUEST, "The request body is not valid JSON.");
                    malformed.setTitle("Malformed request");
                    return malformed;
                });
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail validationProblem(List<FieldViolation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are not valid.");
        problem.setTitle("Validation failed");
        problem.setProperty(ERRORS_PROPERTY, violations);
        return problem;
    }

    private static Optional<String> unreadableField(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jacksonException) {
                String field = jacksonException.getPath().stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("."));
                return field.isEmpty() ? Optional.empty() : Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
