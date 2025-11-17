package com.koni.telemetry.domain.exception;

/**
 * Exception thrown when duplicate telemetry is detected.
 * This exception indicates that the same telemetry (same deviceId and date)
 * has already been processed, ensuring idempotent behavior.
 */
public class DuplicateTelemetryException extends RuntimeException {
    
    public DuplicateTelemetryException(String message) {
        super(message);
    }
    
    public DuplicateTelemetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
