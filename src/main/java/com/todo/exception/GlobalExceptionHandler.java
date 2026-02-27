package com.todo.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized error handler for all exceptions thrown from controllers and services.
 *
 * Every exception is:
 *   1. Logged at the appropriate level with full context (traceId, path, method).
 *   2. Mapped to a structured, consistent JSON error body.
 *
 * This is the single place in the codebase where exceptions are converted to HTTP responses.
 * Services throw domain exceptions; this class owns the HTTP status mapping.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TodoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            TodoNotFoundException ex, HttpServletRequest request) {
        log.warn("[NOT_FOUND] {} | path={} method={}",
                ex.getMessage(), request.getRequestURI(), request.getMethod());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ItemImmutableException.class)
    public ResponseEntity<ErrorResponse> handleImmutable(
            ItemImmutableException ex, HttpServletRequest request) {
        log.warn("[IMMUTABLE_ITEM] {} | path={} method={}",
                ex.getMessage(), request.getRequestURI(), request.getMethod());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Handles concurrent update conflicts raised by JPA optimistic locking (@Version).
     * Two simultaneous PATCHes on the same item will result in one succeeding and the other
     * getting this exception, which is correctly surfaced as 409.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        String msg = "Concurrent update conflict. Please retry your request.";
        log.warn("[OPTIMISTIC_LOCK] {} | path={} traceId={}",
                msg, request.getRequestURI(), MDC.get("traceId"), ex);
        return buildResponse(HttpStatus.CONFLICT, msg, request);
    }

    /**
     * Handles @Valid / @Validated DTO validation failures.
     * Returns all field errors in a flat list so clients know exactly what to fix.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        String message = "Validation failed: " + String.join("; ", fieldErrors);
        log.warn("[VALIDATION] {} | path={} errors={}", message, request.getRequestURI(), fieldErrors);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[BAD_REQUEST] {} | path={}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Spring throws NoResourceFoundException for missing static resources like favicon.ico.
     * These are browser-initiated, not application errors — log at DEBUG not ERROR.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("[STATIC_NOT_FOUND] path={}", request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND,
                "Resource not found: " + request.getRequestURI(), request);
    }

    /**
     * Catch-all for unexpected exceptions.
     * Logs at ERROR with full stack trace. The response body does NOT expose internal details —
     * only a generic message is returned to the client for security reasons.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("[UNEXPECTED_ERROR] path={} method={} traceId={} | {}",
                request.getRequestURI(), request.getMethod(),
                MDC.get("traceId"), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                MDC.get("traceId"),
                Instant.now()
        );
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Structured error response body.
     * Consistent shape across all error types — clients can always expect these fields.
     */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            String traceId,
            Instant timestamp
    ) {}
}
