package com.koni.telemetry.domain.repository;

import com.koni.telemetry.infrastructure.persistence.entity.FallbackEventEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for FallbackEvent persistence operations.
 * This interface is part of the domain layer and defines the contract
 * for fallback event data access when Kafka is unavailable.
 * 
 * Fallback events are stored when the circuit breaker is open and Kafka
 * cannot accept messages. These events can be replayed later when Kafka
 * becomes available again.
 * 
 * Following Hexagonal Architecture principles, this interface will be implemented
 * by infrastructure adapters (e.g., JPA repositories).
 */
public interface FallbackEventRepository {
    
    /**
     * Persists a fallback event to the data store.
     * This method is called when the circuit breaker is open and an event
     * cannot be published to Kafka.
     * 
     * @param event the fallback event entity to save
     * @throws IllegalArgumentException if event is null
     */
    void save(FallbackEventEntity event);
    
    /**
     * Retrieves all fallback events from the data store.
     * This method is used to replay events when Kafka becomes available.
     * Events are ordered by failed_at timestamp in ascending order (oldest first).
     * 
     * @return list of all fallback events, empty list if none exist
     */
    List<FallbackEventEntity> findAll();
    
    /**
     * Deletes a fallback event from the data store by its event ID.
     * This method is called after successfully replaying an event to Kafka.
     * 
     * @param eventId the unique identifier of the event to delete
     * @throws IllegalArgumentException if eventId is null
     */
    void delete(UUID eventId);
}
