package com.koni.telemetry.domain.exception;

/**
 * Exception thrown when validation of domain objects fails.
 * This exception indicates that the provided data does not meet business rules.
 */
public class ValidationException extends RuntimeException {
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
