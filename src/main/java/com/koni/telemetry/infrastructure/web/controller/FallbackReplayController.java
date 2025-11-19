package com.koni.telemetry.infrastructure.web.controller;

import com.koni.telemetry.application.service.FallbackReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for fallback event replay administration.
 * This controller provides endpoints for managing fallback events that were
 * stored when Kafka was unavailable (circuit breaker was open).
 * 
 * Endpoints:
 * - POST /api/v1/admin/fallback/replay: Replay all fallback events to Kafka
 * 
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class FallbackReplayController {
    
    private final FallbackReplayService fallbackReplayService;
    
    /**
     * Replays all fallback events to Kafka.
     * 
     * This endpoint should be called after Kafka becomes available again
     * (circuit breaker is closed) to replay events that were stored in the
     * fallback repository during the outage.
     * 
     * The endpoint will:
     * - Check if circuit breaker is closed
     * - Retrieve all fallback events from the database
     * - Publish each event to Kafka
     * - Delete successfully published events from the fallback repository
     * 
     * If the circuit breaker is not closed, the endpoint will return 503 Service Unavailable.
     * 
     * Example request:
     * POST /api/v1/admin/fallback/replay
     * 
     * Example response (200 OK):
     * {
     *   "message": "Successfully replayed 5 events",
     *   "replayedCount": 5
     * }
     * 
     * @return 200 OK with replay count, or 503 if circuit breaker is not closed
     */
    @PostMapping("/fallback/replay")
    public ResponseEntity<Map<String, Object>> replayFallbackEvents() {
        log.info("Received request to replay fallback events");
        
        try {
            int replayedCount = fallbackReplayService.replayEvents();
            
            String message = replayedCount > 0 
                    ? String.format("Successfully replayed %d events", replayedCount)
                    : "No fallback events to replay";
            
            log.info("Fallback replay completed: {} events replayed", replayedCount);
            
            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "replayedCount", replayedCount
            ));
            
        } catch (Exception e) {
            log.error("Failed to replay fallback events", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "message", "Failed to replay fallback events: " + e.getMessage(),
                            "replayedCount", 0
                    ));
        }
    }
}
