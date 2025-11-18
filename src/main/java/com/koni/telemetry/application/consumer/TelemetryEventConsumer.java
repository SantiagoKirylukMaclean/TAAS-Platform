package com.koni.telemetry.application.consumer;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.model.DeviceProjection;
import com.koni.telemetry.domain.repository.DeviceProjectionRepository;
import com.koni.telemetry.infrastructure.observability.TelemetryMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TelemetryEventConsumer processes TelemetryRecorded events from Kafka.
 * This consumer is part of the read side (query side) in the CQRS pattern.
 * 
 * It listens to the "telemetry.recorded" topic and updates the device projection
 * with the latest temperature measurement for each device.
 * 
 * Key responsibilities:
 * - Consume TelemetryRecorded events from Kafka
 * - Detect out-of-order events (events with older timestamps)
 * - Update device projections only when events are newer
 * - Manually commit Kafka offsets after successful processing
 * 
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryEventConsumer {
    
    private final DeviceProjectionRepository deviceProjectionRepository;
    private final TelemetryMetrics telemetryMetrics;
    
    /**
     * Consumes TelemetryRecorded events from the Kafka topic.
     * 
     * This method:
     * 1. Retrieves the existing DeviceProjection for the device (if any)
     * 2. Checks if the event is out-of-order using isNewerThan()
     * 3. Updates the projection if the event is newer, skips if older
     * 4. Logs a warning for out-of-order events
     * 5. Manually commits the Kafka offset after successful processing
     * 
     * @param event the TelemetryRecorded event from Kafka
     * @param acknowledgment the Kafka acknowledgment for manual offset commit
     */
    @KafkaListener(
            topics = "${telemetry.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(TelemetryRecorded event, Acknowledgment acknowledgment) {
        log.debug("Received TelemetryRecorded event: {}", event);
        
        try {
            // Record processing time for the entire consumer operation
            telemetryMetrics.recordProcessingTime(() -> {
                // Retrieve existing DeviceProjection by deviceId (Requirement 3.1)
                Optional<DeviceProjection> existingProjection = 
                        deviceProjectionRepository.findByDeviceId(event.getDeviceId());
                
                // Check if event is out-of-order using isNewerThan() (Requirement 3.2, 6.2)
                if (existingProjection.isPresent() && existingProjection.get().isNewerThan(event.getDate())) {
                    // Event is out-of-order - skip update (Requirement 3.4, 6.3)
                    log.warn("Out-of-order telemetry detected and skipped: deviceId={}, eventDate={}, latestDate={}", 
                            event.getDeviceId(), 
                            event.getDate(), 
                            existingProjection.get().getLatestDate());
                    // Requirement 6.4: Log warning for out-of-order events
                    
                    // Record out-of-order event metric
                    telemetryMetrics.recordOutOfOrder();
                    
                    // Still commit offset even for out-of-order events (Requirement 3.5)
                    acknowledgment.acknowledge();
                    return;
                }
                
                // Update projection if event is newer, or create new projection (Requirement 3.3, 6.1)
                DeviceProjection projection = existingProjection.orElse(new DeviceProjection(event.getDeviceId()));
                projection.updateWith(event.getMeasurement(), event.getDate());
                
                // Persist the updated projection
                deviceProjectionRepository.save(projection);
                
                log.info("Device projection updated: deviceId={}, measurement={}, date={}", 
                        event.getDeviceId(), 
                        event.getMeasurement(), 
                        event.getDate());
                
                // Commit Kafka offset manually after successful processing (Requirement 3.5)
                acknowledgment.acknowledge();
            });
            
        } catch (Exception e) {
            log.error("Error processing TelemetryRecorded event: {}", event, e);
            // Don't acknowledge - let Kafka retry
            throw e;
        }
    }
}
