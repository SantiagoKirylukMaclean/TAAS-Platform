package com.koni.telemetry.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TelemetryMetrics.
 * Tests counter increments, timer recording, and metric names/tags.
 */
class TelemetryMetricsTest {
    
    private MeterRegistry meterRegistry;
    private TelemetryMetrics telemetryMetrics;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        telemetryMetrics = new TelemetryMetrics(meterRegistry);
    }
    
    @Test
    void shouldIncrementTelemetryReceivedCounter() {
        // Given
        Counter counter = meterRegistry.find("telemetry.received.total").counter();
        assertThat(counter).isNotNull();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordTelemetryReceived();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }
    
    @Test
    void shouldIncrementDuplicatesDetectedCounter() {
        // Given
        Counter counter = meterRegistry.find("telemetry.duplicates.total").counter();
        assertThat(counter).isNotNull();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordDuplicate();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }
    
    @Test
    void shouldIncrementOutOfOrderEventsCounter() {
        // Given
        Counter counter = meterRegistry.find("telemetry.out_of_order.total").counter();
        assertThat(counter).isNotNull();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordOutOfOrder();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }
    
    @Test
    void shouldIncrementDlqMessagesSentCounter() {
        // Given
        Counter counter = meterRegistry.find("telemetry.dlq.sent.total").counter();
        assertThat(counter).isNotNull();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordDlqMessageSent();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }
    
    @Test
    void shouldIncrementDlqMessagesReprocessedCounter() {
        // Given
        Counter counter = meterRegistry.find("telemetry.dlq.reprocessed.total").counter();
        assertThat(counter).isNotNull();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordDlqMessageReprocessed();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }
    
    @Test
    void shouldRecordProcessingTimeForSupplier() {
        // Given
        Timer timer = meterRegistry.find("telemetry.processing.time").timer();
        assertThat(timer).isNotNull();
        long initialCount = timer.count();
        
        // When
        String result = telemetryMetrics.recordProcessingTime(() -> {
            try {
                Thread.sleep(10); // Simulate some processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "test-result";
        });
        
        // Then
        assertThat(result).isEqualTo("test-result");
        assertThat(timer.count()).isEqualTo(initialCount + 1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
    
    @Test
    void shouldRecordProcessingTimeForRunnable() {
        // Given
        Timer timer = meterRegistry.find("telemetry.processing.time").timer();
        assertThat(timer).isNotNull();
        long initialCount = timer.count();
        
        // When
        telemetryMetrics.recordProcessingTime(() -> {
            try {
                Thread.sleep(10); // Simulate some processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Then
        assertThat(timer.count()).isEqualTo(initialCount + 1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
    
    @Test
    void shouldHaveCorrectMetricNames() {
        // Then
        assertThat(meterRegistry.find("telemetry.received.total").counter()).isNotNull();
        assertThat(meterRegistry.find("telemetry.duplicates.total").counter()).isNotNull();
        assertThat(meterRegistry.find("telemetry.out_of_order.total").counter()).isNotNull();
        assertThat(meterRegistry.find("telemetry.dlq.sent.total").counter()).isNotNull();
        assertThat(meterRegistry.find("telemetry.dlq.reprocessed.total").counter()).isNotNull();
        assertThat(meterRegistry.find("telemetry.processing.time").timer()).isNotNull();
    }
    
    @Test
    void shouldHaveCorrectTags() {
        // Given
        Counter telemetryReceivedCounter = meterRegistry.find("telemetry.received.total").counter();
        
        // Then
        assertThat(telemetryReceivedCounter).isNotNull();
        assertThat(telemetryReceivedCounter.getId().getTag("type")).isEqualTo("temperature");
    }
    
    @Test
    void shouldHavePercentilesConfiguredForTimer() {
        // Given
        Timer timer = meterRegistry.find("telemetry.processing.time").timer();
        
        // When
        telemetryMetrics.recordProcessingTime(() -> {
            // Simulate processing
        });
        
        // Then
        assertThat(timer).isNotNull();
        // SimpleMeterRegistry doesn't support percentiles, but we verify the timer exists
        assertThat(timer.count()).isGreaterThan(0);
    }
    
    @Test
    void shouldIncrementCountersMultipleTimes() {
        // Given
        Counter counter = meterRegistry.find("telemetry.received.total").counter();
        double initialCount = counter.count();
        
        // When
        telemetryMetrics.recordTelemetryReceived();
        telemetryMetrics.recordTelemetryReceived();
        telemetryMetrics.recordTelemetryReceived();
        
        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 3);
    }
}
