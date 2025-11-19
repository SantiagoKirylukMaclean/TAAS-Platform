package com.koni.telemetry.infrastructure.web.dto;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for Dead Letter Queue messages.
 * This DTO is used to represent messages that failed processing and were sent to the DLQ.
 * 
 * Contains:
 * - event: the original TelemetryRecorded event that failed processing
 * - errorMessage: the error message describing why processing failed
 * - retryCount: the number of times the message was retried before being sent to DLQ
 * - timestamp: the timestamp when the message was sent to the DLQ
 * 
 * This DTO is used by the DLQ management endpoints to list and inspect failed messages.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage {
    
    private TelemetryRecorded event;
    private String errorMessage;
    private int retryCount;
    private Instant timestamp;
}
