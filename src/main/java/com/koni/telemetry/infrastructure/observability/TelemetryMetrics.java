package com.koni.telemetry.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Component for tracking telemetry-specific metrics.
 * Provides counters and timers for monitoring system behavior.
 */
@Slf4j
@Component
public class TelemetryMetrics {

    private final Counter telemetryReceived;
    private final Counter duplicatesDetected;
    private final Counter outOfOrderEvents;
    private final Counter dlqMessagesSent;
    private final Counter dlqMessagesReprocessed;
    private final Timer processingTime;

    public TelemetryMetrics(MeterRegistry registry) {
        this.telemetryReceived = Counter.builder("telemetry.received.total")
                .description("Total telemetry messages received")
                .tag("type", "temperature")
                .register(registry);

        this.duplicatesDetected = Counter.builder("telemetry.duplicates.total")
                .description("Total duplicate telemetry messages detected")
                .register(registry);

        this.outOfOrderEvents = Counter.builder("telemetry.out_of_order.total")
                .description("Total out-of-order events detected")
                .register(registry);

        this.dlqMessagesSent = Counter.builder("telemetry.dlq.sent.total")
                .description("Total messages sent to Dead Letter Queue")
                .register(registry);

        this.dlqMessagesReprocessed = Counter.builder("telemetry.dlq.reprocessed.total")
                .description("Total messages reprocessed from Dead Letter Queue")
                .register(registry);

        this.processingTime = Timer.builder("telemetry.processing.time")
                .description("Time to process telemetry")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Increment the counter for received telemetry messages.
     */
    public void recordTelemetryReceived() {
        telemetryReceived.increment();
        log.debug("Telemetry received counter incremented");
    }

    /**
     * Increment the counter for duplicate telemetry messages.
     */
    public void recordDuplicate() {
        duplicatesDetected.increment();
        log.debug("Duplicate telemetry counter incremented");
    }

    /**
     * Increment the counter for out-of-order events.
     */
    public void recordOutOfOrder() {
        outOfOrderEvents.increment();
        log.debug("Out-of-order event counter incremented");
    }

    /**
     * Record the processing time for a telemetry operation.
     * 
     * @param operation The operation to time
     * @param <T> The return type of the operation
     * @return The result of the operation
     */
    public <T> T recordProcessingTime(Supplier<T> operation) {
        return processingTime.record(operation);
    }

    /**
     * Record the processing time for a void operation.
     * 
     * @param operation The operation to time
     */
    public void recordProcessingTime(Runnable operation) {
        processingTime.record(operation);
    }

    /**
     * Increment the counter for messages sent to Dead Letter Queue.
     */
    public void recordDlqMessageSent() {
        dlqMessagesSent.increment();
        log.debug("DLQ message sent counter incremented");
    }

    /**
     * Increment the counter for messages reprocessed from Dead Letter Queue.
     */
    public void recordDlqMessageReprocessed() {
        dlqMessagesReprocessed.increment();
        log.debug("DLQ message reprocessed counter incremented");
    }
}
