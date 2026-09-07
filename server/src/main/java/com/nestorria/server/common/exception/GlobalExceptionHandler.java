package com.nestorria.server.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    private String text(String code, Object[] args, String fallback) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messages.getMessage(code, args, locale);
        } catch (Exception e) {
            return fallback;
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        String raw = ex.getMessage();
        boolean isCode = raw != null && raw.matches("[a-z0-9.\\-]+");
        String code = isCode ? raw : "not-found";
        String message = isCode ? text(code, null, raw) : raw;
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorBody(HttpStatus.NOT_FOUND, code, message));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        String raw = ex.getMessage();
        boolean isCode = raw != null && raw.matches("[a-z0-9.\\-]+");
        String code = isCode ? raw : "conflict";
        String message = isCode ? text(code, null, raw) : raw;
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(errorBody(HttpStatus.CONFLICT, code, message));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        String raw = ex.getMessage();
        boolean isCode = raw != null && raw.matches("[a-z0-9.\\-]+");
        String code = isCode ? raw : "bad-request";
        String message = isCode ? text(code, null, raw) : raw;
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                err -> err.getField(),
                err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "invalid",
                (a, b) -> a + "; " + b,
                LinkedHashMap::new
            ));

        String globalMessage = ex.getBindingResult().getGlobalErrors().stream()
            .map(err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "validation error")
            .collect(Collectors.joining("; "));

        if (!globalMessage.isEmpty()) {
            fieldErrors.put("_global", globalMessage);
        }

        String combinedMessage = fieldErrors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining(", "));

        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, "bad-request", combinedMessage);
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(com.nestorria.server.common.ai.AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiServiceException(
            com.nestorria.server.common.ai.AiServiceException ex,
            HttpServletResponse response) {
        if (response.isCommitted()) {
            log.warn("AiServiceException (response already committed): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorBody(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = text("error.invalid-param", new Object[]{ ex.getName() }, "Invalid parameter: " + ex.getName());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, "error.invalid-param", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.error("Unexpected error (response already committed): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        String message = text("error.internal", null, "Internal server error");
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", message));
    }

    private Map<String, Object> errorBody(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
