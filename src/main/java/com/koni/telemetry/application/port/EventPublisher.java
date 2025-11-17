package com.koni.telemetry.application.port;

import com.koni.telemetry.domain.event.TelemetryRecorded;

/**
 * Port interface for publishing domain events.
 * This interface follows the Hexagonal Architecture pattern, defining an output port
 * that will be implemented by infrastructure adapters (e.g., Kafka publisher).
 * 
 * The application layer depends on this abstraction, not on concrete implementations.
 */
public interface EventPublisher {
    
    /**
     * Publishes a TelemetryRecorded domain event to the event bus.
     * 
     * @param event the TelemetryRecorded event to publish
     * @throws IllegalArgumentException if event is null
     */
    void publish(TelemetryRecorded event);
}
