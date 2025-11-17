package com.koni.telemetry.infrastructure.web.dto;

import java.time.Instant;

/**
 * DTO for error responses returned by the REST API.
 * Provides consistent error information to clients.
 */
public class ErrorResponse {
    
    private final int status;
    private final String message;
    private final Instant timestamp;
    
    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
    }
    
    public int getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
}
