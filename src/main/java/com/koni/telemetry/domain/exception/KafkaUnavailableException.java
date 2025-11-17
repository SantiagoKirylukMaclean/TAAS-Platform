package com.koni.telemetry.domain.exception;

/**
 * Exception thrown when Kafka is unavailable or fails to publish events.
 * This exception indicates that the event bus is temporarily unavailable,
 * and the operation should be retried or the client should be notified.
 */
public class KafkaUnavailableException extends RuntimeException {
    
    public KafkaUnavailableException(String message) {
        super(message);
    }
    
    public KafkaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
