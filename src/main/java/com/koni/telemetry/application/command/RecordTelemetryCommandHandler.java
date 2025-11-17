package com.koni.telemetry.application.command;

import com.koni.telemetry.application.port.EventPublisher;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.model.Telemetry;
import com.koni.telemetry.domain.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Command handler for recording telemetry data.
 * This handler implements the write side of the CQRS pattern.
 * 
 * Responsibilities:
 * - Validate incoming telemetry commands
 * - Check for duplicate submissions (idempotency)
 * - Persist telemetry to the database
 * - Publish TelemetryRecorded domain events
 * 
 * Requirements: 1.3, 1.4, 5.1, 5.2, 5.3, 7.2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecordTelemetryCommandHandler {
    
    private final TelemetryRepository telemetryRepository;
    private final EventPublisher eventPublisher;
    
    /**
     * Handles the RecordTelemetryCommand by validating, persisting, and publishing events.
     * This method is idempotent - duplicate submissions are detected and handled gracefully.
     * 
     * @param command the command containing telemetry data to record
     * @throws com.koni.telemetry.domain.exception.ValidationException if validation fails
     */
    @Transactional
    public void handle(RecordTelemetryCommand command) {
        log.debug("Handling RecordTelemetryCommand: deviceId={}, measurement={}, date={}", 
                command.getDeviceId(), command.getMeasurement(), command.getDate());
        
        // 1. Create and validate telemetry value object
        Telemetry telemetry = new Telemetry(
                command.getDeviceId(),
                command.getMeasurement(),
                command.getDate()
        );
        telemetry.validate();
        
        // 2. Check for duplicates (idempotency)
        if (telemetryRepository.exists(telemetry.getDeviceId(), telemetry.getDate())) {
            log.info("Duplicate telemetry detected: deviceId={}, date={}. Skipping processing.", 
                    telemetry.getDeviceId(), telemetry.getDate());
            return; // Idempotent - already processed
        }
        
        // 3. Persist telemetry to database
        telemetryRepository.save(telemetry);
        log.info("Telemetry saved: deviceId={}, measurement={}, date={}", 
                telemetry.getDeviceId(), telemetry.getMeasurement(), telemetry.getDate());
        
        // 4. Create and publish domain event
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                telemetry.getDeviceId(),
                telemetry.getMeasurement(),
                telemetry.getDate(),
                Instant.now()
        );
        
        eventPublisher.publish(event);
        log.debug("TelemetryRecorded event published: eventId={}, deviceId={}", 
                event.getEventId(), event.getDeviceId());
    }
}
