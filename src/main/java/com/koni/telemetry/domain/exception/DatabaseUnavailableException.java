package com.koni.telemetry.domain.exception;

/**
 * Exception thrown when the database is unavailable or fails to execute operations.
 * This exception indicates that the persistence layer is temporarily unavailable,
 * and the operation should be retried or the client should be notified.
 */
public class DatabaseUnavailableException extends RuntimeException {
    
    public DatabaseUnavailableException(String message) {
        super(message);
    }
    
    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
