package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.domain.repository.FallbackEventRepository;
import com.koni.telemetry.infrastructure.persistence.entity.FallbackEventEntity;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * JPA adapter for FallbackEventRepository that adapts the domain interface
 * to the JPA infrastructure layer.
 * 
 * This adapter follows Hexagonal Architecture principles by implementing
 * the domain repository interface and delegating to the JPA repository.
 * It handles persistence of fallback events when Kafka is unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaFallbackEventRepositoryAdapter implements FallbackEventRepository {
    
    private final FallbackEventJpaRepository jpaRepository;
    
    /**
     * Persists a fallback event to the database.
     * This method is called when the circuit breaker is open and an event
     * cannot be published to Kafka.
     * 
     * @param event the fallback event entity to save
     * @throws IllegalArgumentException if event is null
     */
    @Override
    @Observed(name = "repository.save", contextualName = "fallback-event-save")
    public void save(FallbackEventEntity event) {
        if (event == null) {
            throw new IllegalArgumentException("FallbackEventEntity cannot be null");
        }
        
        log.debug("Saving fallback event with eventId: {}", event.getEventId());
        jpaRepository.save(event);
        log.info("Fallback event saved successfully: eventId={}, deviceId={}", 
            event.getEventId(), event.getDeviceId());
    }
    
    /**
     * Retrieves all fallback events from the database.
     * Events are ordered by failed_at timestamp in ascending order (oldest first).
     * This method is used to replay events when Kafka becomes available.
     * 
     * @return list of all fallback events, empty list if none exist
     */
    @Override
    @Observed(name = "repository.findAll", contextualName = "fallback-event-findAll")
    public List<FallbackEventEntity> findAll() {
        log.debug("Retrieving all fallback events");
        List<FallbackEventEntity> events = jpaRepository.findAllByOrderByFailedAtAsc();
        log.info("Retrieved {} fallback events", events.size());
        return events;
    }
    
    /**
     * Deletes a fallback event from the database by its event ID.
     * This method is called after successfully replaying an event to Kafka.
     * 
     * @param eventId the unique identifier of the event to delete
     * @throws IllegalArgumentException if eventId is null
     */
    @Override
    @Observed(name = "repository.delete", contextualName = "fallback-event-delete")
    public void delete(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("EventId cannot be null");
        }
        
        log.debug("Deleting fallback event with eventId: {}", eventId);
        jpaRepository.deleteById(eventId);
        log.info("Fallback event deleted successfully: eventId={}", eventId);
    }
}
