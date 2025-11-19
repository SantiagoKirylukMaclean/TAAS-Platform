package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.infrastructure.persistence.entity.FallbackEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for FallbackEventEntity persistence operations.
 * This repository provides database access for storing events that failed
 * to publish to Kafka due to circuit breaker being open.
 * 
 * Spring Data JPA will automatically implement this interface at runtime,
 * providing standard CRUD operations and custom query methods.
 */
@Repository
public interface FallbackEventJpaRepository extends JpaRepository<FallbackEventEntity, UUID> {
    
    /**
     * Retrieves all fallback events ordered by failed_at timestamp in ascending order.
     * This ensures that the oldest events are replayed first when Kafka becomes available.
     * 
     * @return list of all fallback events ordered by failed_at (oldest first)
     */
    List<FallbackEventEntity> findAllByOrderByFailedAtAsc();
}
