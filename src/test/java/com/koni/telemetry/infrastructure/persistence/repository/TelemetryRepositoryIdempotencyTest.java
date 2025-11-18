package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.domain.model.Telemetry;
import com.koni.telemetry.domain.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TelemetryRepository idempotency logic.
 * Tests duplicate detection and unique constraint enforcement at the database level.
 * 
 */
@DataJpaTest
@Import(JpaTelemetryRepositoryAdapter.class)
@ActiveProfiles("test")
class TelemetryRepositoryIdempotencyTest {
    
    @Autowired
    private TelemetryRepository telemetryRepository;
    
    @Autowired
    private TelemetryJpaRepository jpaRepository;
    
    @Test
    void shouldDetectDuplicateTelemetryUsingExistsMethod() {
        // Given
        Long deviceId = 1L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        Telemetry telemetry = new Telemetry(deviceId, measurement, date);
        
        // When - Save the telemetry first time
        telemetryRepository.save(telemetry);
        
        // Then - exists() should return true for the same deviceId and date
        boolean exists = telemetryRepository.exists(deviceId, date);
        assertThat(exists).isTrue();
    }
    
    @Test
    void shouldReturnFalseForNonExistentTelemetry() {
        // Given
        Long deviceId = 999L;
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        // When/Then - exists() should return false for non-existent telemetry
        boolean exists = telemetryRepository.exists(deviceId, date);
        assertThat(exists).isFalse();
    }
    
    @Test
    void shouldEnforceUniqueConstraintOnDeviceIdAndDate() {
        // Given
        Long deviceId = 1L;
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        Telemetry telemetry1 = new Telemetry(deviceId, new BigDecimal("25.5"), date);
        Telemetry telemetry2 = new Telemetry(deviceId, new BigDecimal("30.0"), date);
        
        // When - Save the first telemetry
        telemetryRepository.save(telemetry1);
        
        // Then - Attempting to save a second telemetry with same deviceId and date should fail
        assertThatThrownBy(() -> telemetryRepository.save(telemetry2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    
    @Test
    void shouldAllowSameDateForDifferentDevices() {
        // Given
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        Telemetry telemetry1 = new Telemetry(1L, new BigDecimal("25.5"), date);
        Telemetry telemetry2 = new Telemetry(2L, new BigDecimal("30.0"), date);
        
        // When - Save telemetry for two different devices with the same date
        telemetryRepository.save(telemetry1);
        telemetryRepository.save(telemetry2);
        
        // Then - Both should be saved successfully
        assertThat(telemetryRepository.exists(1L, date)).isTrue();
        assertThat(telemetryRepository.exists(2L, date)).isTrue();
        assertThat(jpaRepository.count()).isEqualTo(2);
    }
    
    @Test
    void shouldAllowSameDeviceWithDifferentDates() {
        // Given
        Long deviceId = 1L;
        Instant date1 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant date2 = Instant.now().minus(1, ChronoUnit.HOURS);
        
        Telemetry telemetry1 = new Telemetry(deviceId, new BigDecimal("25.5"), date1);
        Telemetry telemetry2 = new Telemetry(deviceId, new BigDecimal("30.0"), date2);
        
        // When - Save telemetry for the same device with different dates
        telemetryRepository.save(telemetry1);
        telemetryRepository.save(telemetry2);
        
        // Then - Both should be saved successfully
        assertThat(telemetryRepository.exists(deviceId, date1)).isTrue();
        assertThat(telemetryRepository.exists(deviceId, date2)).isTrue();
        assertThat(jpaRepository.count()).isEqualTo(2);
    }
    
    @Test
    void shouldHandleIdempotencyCheckBeforeSave() {
        // Given
        Long deviceId = 1L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        Telemetry telemetry = new Telemetry(deviceId, measurement, date);
        
        // When - First check should return false, then save, then check should return true
        boolean existsBeforeSave = telemetryRepository.exists(deviceId, date);
        telemetryRepository.save(telemetry);
        boolean existsAfterSave = telemetryRepository.exists(deviceId, date);
        
        // Then
        assertThat(existsBeforeSave).isFalse();
        assertThat(existsAfterSave).isTrue();
    }
}
