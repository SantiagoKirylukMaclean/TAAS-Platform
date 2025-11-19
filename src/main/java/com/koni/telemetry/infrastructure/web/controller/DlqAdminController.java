package com.koni.telemetry.infrastructure.web.controller;

import com.koni.telemetry.application.service.DlqManagementService;
import com.koni.telemetry.infrastructure.web.dto.DlqMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Dead Letter Queue (DLQ) administration.
 * This controller provides endpoints for managing messages that failed processing
 * and were sent to the DLQ after exhausting retry attempts.
 * 
 * Endpoints:
 * - GET /api/v1/admin/dlq: List all messages in the DLQ
 * - POST /api/v1/admin/dlq/reprocess: Reprocess all DLQ messages
 *
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class DlqAdminController {
    
    private final DlqManagementService dlqManagementService;
    
    /**
     * Lists all messages currently in the Dead Letter Queue.
     * 
     * This endpoint retrieves all messages from the DLQ topic and returns them
     * with their error details, retry count, and timestamp. This allows operators
     * to inspect failed messages and understand why they failed.
     * 
     * The response includes:
     * - event: the original TelemetryRecorded event
     * - errorMessage: the error that caused the failure
     * - retryCount: number of retry attempts before DLQ
     * - timestamp: when the message was sent to DLQ
     * 
     * Example request:
     * GET /api/v1/admin/dlq
     * 
     * Example response (200 OK):
     * [
     *   {
     *     "event": {
     *       "eventId": "123e4567-e89b-12d3-a456-426614174000",
     *       "deviceId": 1,
     *       "measurement": 25.5,
     *       "date": "2025-01-31T10:00:00Z"
     *     },
     *     "errorMessage": "Validation failed: measurement out of range",
     *     "retryCount": 3,
     *     "timestamp": "2025-01-31T10:05:00Z"
     *   }
     * ]
     * 
     * Requirement 10.1: Expose GET /api/v1/admin/dlq endpoint to list all messages in the DLQ
     * Requirement 10.2: Return message content, error details, timestamp, and retry count
     * 
     * @return 200 OK with list of DLQ messages
     */
    @GetMapping("/dlq")
    public ResponseEntity<List<DlqMessage>> listDlqMessages() {
        log.info("Received request to list DLQ messages");
        
        try {
            List<DlqMessage> messages = dlqManagementService.listDlqMessages();
            
            log.info("Successfully retrieved {} messages from DLQ", messages.size());
            
            return ResponseEntity.ok(messages);
            
        } catch (Exception e) {
            log.error("Failed to list DLQ messages", e);
            // Return empty list on error to avoid exposing internal details
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Reprocesses all messages from the Dead Letter Queue.
     * 
     * This endpoint retrieves all messages from the DLQ and attempts to reprocess
     * them by republishing to the main Kafka topic. This is useful for recovering
     * from transient failures after the underlying issue has been resolved.
     * 
     * The endpoint will:
     * - Retrieve all messages from the DLQ topic
     * - Republish each message to the main "telemetry.recorded" topic
     * - Commit offsets to remove successfully reprocessed messages from DLQ
     * - Return the count of successfully reprocessed messages
     * 
     * Note: If reprocessing fails for a message, it will remain in the DLQ
     * and can be retried later.
     * 
     * Example request:
     * POST /api/v1/admin/dlq/reprocess
     * 
     * Example response (202 Accepted):
     * {
     *   "message": "Successfully reprocessed 5 messages from DLQ",
     *   "reprocessedCount": 5
     * }
     * 
     * Requirement 10.3: Expose POST /api/v1/admin/dlq/reprocess endpoint
     * Requirement 10.4: Consume messages from DLQ and attempt to process them again
     * Requirement 10.5: Remove message from DLQ when reprocessing succeeds
     * 
     * @return 202 Accepted with reprocessed count
     */
    @PostMapping("/dlq/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDlqMessages() {
        log.info("Received request to reprocess DLQ messages");
        
        try {
            int reprocessedCount = dlqManagementService.reprocessDlqMessages();
            
            String message = reprocessedCount > 0
                    ? String.format("Successfully reprocessed %d messages from DLQ", reprocessedCount)
                    : "No messages to reprocess in DLQ";
            
            log.info("DLQ reprocessing completed: {} messages reprocessed", reprocessedCount);
            
            return ResponseEntity.accepted().body(Map.of(
                    "message", message,
                    "reprocessedCount", reprocessedCount
            ));
            
        } catch (Exception e) {
            log.error("Failed to reprocess DLQ messages", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "message", "Failed to reprocess DLQ messages: " + e.getMessage(),
                            "reprocessedCount", 0
                    ));
        }
    }
}
