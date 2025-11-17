package com.koni.telemetry.infrastructure.web.exception;

import com.koni.telemetry.domain.exception.DatabaseUnavailableException;
import com.koni.telemetry.domain.exception.DuplicateTelemetryException;
import com.koni.telemetry.domain.exception.KafkaUnavailableException;
import com.koni.telemetry.domain.exception.ValidationException;
import com.koni.telemetry.infrastructure.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST API endpoints.
 * Provides consistent error responses and appropriate HTTP status codes.
 * 
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle validation exceptions.
     * Returns 400 Bad Request when input validation fails.
     * 
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle duplicate telemetry exceptions.
     * Returns 200 OK when duplicate is detected (idempotent behavior).
     * 
     */
    @ExceptionHandler(DuplicateTelemetryException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTelemetryException(DuplicateTelemetryException ex) {
        log.info("Duplicate telemetry detected: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.OK.value(),
            "Already processed"
        );
        return ResponseEntity.ok(errorResponse);
    }
    
    /**
     * Handle Kafka unavailable exceptions.
     * Returns 503 Service Unavailable when Kafka is down or unreachable.
     * 
     */
    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKafkaUnavailableException(KafkaUnavailableException ex) {
        log.error("Kafka unavailable: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "Service temporarily unavailable"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle database unavailable exceptions.
     * Returns 503 Service Unavailable when database is down or unreachable.
     * 
     */
    @ExceptionHandler(DatabaseUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailableException(DatabaseUnavailableException ex) {
        log.error("Database unavailable: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "Service temporarily unavailable"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle all other unexpected exceptions.
     * Returns 500 Internal Server Error for unhandled exceptions.
     * 
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal server error"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
