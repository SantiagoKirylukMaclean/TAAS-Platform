# Circuit Breaker

This document explains the circuit breaker pattern implementation in the telemetry system, including how it protects against Kafka failures, the fallback mechanism, and how to test and monitor circuit breaker behavior.

## Table of Contents

- [Overview](#overview)
- [Circuit Breaker States](#circuit-breaker-states)
- [How It Works](#how-it-works)
- [Fallback Mechanism](#fallback-mechanism)
- [Testing Circuit Breaker](#testing-circuit-breaker)
- [Monitoring](#monitoring)
- [Configuration](#configuration)
- [Best Practices](#best-practices)

## Overview

The circuit breaker pattern prevents cascading failures by failing fast when a dependency (Kafka) is unavailable. Instead of waiting for timeouts on every request, the circuit breaker "opens" after detecting failures and immediately rejects requests without attempting the operation.

**Benefits:**
- **Fail Fast:** Don't waste resources on operations that will fail
- **Prevent Cascading Failures:** Stop failures from spreading
- **Automatic Recovery:** Test for recovery and close circuit when service is healthy
- **No Data Loss:** Fallback mechanism stores events for later replay

**Technology:**
- **Resilience4j:** Circuit breaker implementation
- **Spring Boot Integration:** Automatic configuration and metrics
- **Micrometer:** Metrics and monitoring

## Circuit Breaker States

The circuit breaker has three states:

### CLOSED (Normal Operation)

```
┌─────────────┐
│   CLOSED    │  ← Normal state
│             │
│  Requests   │
│  pass       │
│  through    │
└─────────────┘
```

**Behavior:**
- All requests pass through to Kafka
- Failures are counted in sliding window
- If failure rate exceeds threshold → transition to OPEN

**Metrics:**
- Failure rate: 0-49%
- State: 0 (CLOSED)

### OPEN (Failing Fast)

```
┌─────────────┐
│    OPEN     │  ← Kafka is down
│             │
│  Requests   │
│  fail fast  │
│  (fallback) │
└─────────────┘
```

**Behavior:**
- Requests immediately fail without attempting Kafka
- Fallback mechanism is invoked
- After wait duration → transition to HALF_OPEN

**Metrics:**
- Failure rate: ≥50%
- State: 1 (OPEN)
- Not permitted calls: Increasing

### HALF_OPEN (Testing Recovery)

```
┌─────────────┐
│ HALF_OPEN   │  ← Testing if Kafka recovered
│             │
│  Limited    │
│  requests   │
│  allowed    │
└─────────────┘
```

**Behavior:**
- Limited number of requests allowed through (3)
- If all succeed → transition to CLOSED
- If any fail → transition back to OPEN

**Metrics:**
- State: 2 (HALF_OPEN)
- Permitted calls: 0-3

### State Transitions

```
        ┌──────────────────────────────────────┐
        │                                      │
        ▼                                      │
    ┌────────┐  Failure rate ≥ 50%      ┌──────────┐
    │ CLOSED │ ─────────────────────────>│   OPEN   │
    └────────┘                            └──────────┘
        ▲                                      │
        │                                      │
        │  All test calls succeed              │ Wait duration elapsed
        │                                      │
        │                                      ▼
    ┌──────────────┐                    ┌──────────┐
    │  HALF_OPEN   │<───────────────────│   OPEN   │
    └──────────────┘                    └──────────┘
        │
        │ Any test call fails
        │
        └──────────────────────────────────────┘
```

## How It Works

### Configuration

The circuit breaker is configured with the following parameters:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      kafka:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50.0
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**Parameters:**

| Parameter | Value | Description |
|-----------|-------|-------------|
| `sliding-window-size` | 10 | Number of calls to track |
| `failure-rate-threshold` | 50% | Failure rate to open circuit |
| `wait-duration-in-open-state` | 10s | Time to wait before testing recovery |
| `permitted-calls-in-half-open` | 3 | Test calls in half-open state |
| `automatic-transition` | true | Auto-transition to half-open |

### Request Flow

#### Normal Flow (Circuit CLOSED)

```
1. Client sends telemetry
   ↓
2. RecordTelemetryCommandHandler
   ↓
3. ResilientKafkaEventPublisher
   ↓
4. Circuit Breaker (CLOSED)
   ↓ allows request
5. KafkaTemplate.send()
   ↓
6. Kafka receives message
   ↓
7. Success response
   ↓
8. Circuit Breaker records success
```

#### Failure Flow (Circuit CLOSED → OPEN)

```
Request 1-5: Success (failure rate: 0%)
Request 6: Kafka timeout (failure rate: 16.7%)
Request 7: Kafka timeout (failure rate: 28.6%)
Request 8: Kafka timeout (failure rate: 37.5%)
Request 9: Kafka timeout (failure rate: 44.4%)
Request 10: Kafka timeout (failure rate: 50%) → Circuit OPENS
Request 11+: Fail fast (fallback invoked)
```

#### Recovery Flow (Circuit OPEN → HALF_OPEN → CLOSED)

```
Circuit OPEN for 10 seconds
   ↓
Automatic transition to HALF_OPEN
   ↓
Test call 1: Success (1/3)
Test call 2: Success (2/3)
Test call 3: Success (3/3)
   ↓
Circuit CLOSES (normal operation resumed)
```

### Implementation

**ResilientKafkaEventPublisher:**
```java
@Service
@RequiredArgsConstructor
public class ResilientKafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final FallbackEventRepository fallbackRepository;
    
    @Override
    public void publish(TelemetryRecorded event) {
        Try.ofSupplier(
            CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
                // Attempt to publish to Kafka
                kafkaTemplate.send("telemetry.recorded", 
                    event.getDeviceId().toString(), 
                    event).get(5, TimeUnit.SECONDS);
                return event;
            })
        ).recover(throwable -> {
            // Fallback: Store in database
            log.warn("Circuit breaker open or Kafka unavailable, using fallback", throwable);
            return handleFallback(event);
        }).get();
    }
    
    private TelemetryRecorded handleFallback(TelemetryRecorded event) {
        fallbackRepository.save(new FallbackEvent(
            event.getEventId(),
            event.getDeviceId(),
            event.getMeasurement(),
            event.getDate(),
            Instant.now()
        ));
        return event;
    }
}
```

## Fallback Mechanism

When the circuit breaker is open, events are stored in a fallback repository (database table) for later replay.

### Fallback Events Table

**Schema:**
```sql
CREATE TABLE IF NOT EXISTS fallback_events (
    event_id UUID PRIMARY KEY,
    device_id BIGINT NOT NULL,
    measurement DECIMAL(10, 2) NOT NULL,
    date TIMESTAMP NOT NULL,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fallback_events_failed_at ON fallback_events(failed_at DESC);
```

**Columns:**
- `event_id`: Unique event identifier
- `device_id`: Device that sent the telemetry
- `measurement`: Temperature measurement
- `date`: Timestamp of the measurement
- `failed_at`: When the event was stored in fallback

### Viewing Fallback Events

**SQL Query:**
```bash
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db \
  -c "SELECT * FROM fallback_events ORDER BY failed_at DESC;"
```

**Example Output:**
```
              event_id              | device_id | measurement |         date         |       failed_at
------------------------------------+-----------+-------------+----------------------+----------------------
 123e4567-e89b-12d3-a456-426614174000 |         1 |       25.50 | 2025-01-31 10:00:00 | 2025-01-31 10:00:05
 223e4567-e89b-12d3-a456-426614174001 |         2 |       18.20 | 2025-01-31 10:00:01 | 2025-01-31 10:00:06
```

### Replaying Fallback Events

**Endpoint:**
```
POST /api/v1/admin/fallback/replay
```

**Behavior:**
1. Retrieves all events from `fallback_events` table
2. Publishes each event to Kafka
3. Deletes successfully published events
4. Logs results

**cURL Example:**
```bash
# Replay all fallback events
curl -X POST http://localhost:8080/api/v1/admin/fallback/replay

# Response: 202 Accepted
```

**FallbackReplayService:**
```java
@Service
@RequiredArgsConstructor
public class FallbackReplayService {
    
    private final FallbackEventRepository fallbackRepository;
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    
    public void replayEvents() {
        List<FallbackEvent> events = fallbackRepository.findAll();
        
        log.info("Replaying {} fallback events", events.size());
        
        for (FallbackEvent event : events) {
            try {
                // Publish to Kafka
                TelemetryRecorded telemetryEvent = new TelemetryRecorded(
                    event.getEventId(),
                    event.getDeviceId(),
                    event.getMeasurement(),
                    event.getDate()
                );
                
                kafkaTemplate.send("telemetry.recorded", 
                    event.getDeviceId().toString(), 
                    telemetryEvent).get(5, TimeUnit.SECONDS);
                
                // Delete from fallback repository
                fallbackRepository.delete(event.getEventId());
                
                log.info("Replayed event: {}", event.getEventId());
            } catch (Exception e) {
                log.error("Failed to replay event: {}", event.getEventId(), e);
            }
        }
    }
}
```

**Automatic Replay:**

You can configure automatic replay when the circuit closes:

```java
circuitBreaker.getEventPublisher()
    .onStateTransition(event -> {
        if (event.getStateTransition() == StateTransition.OPEN_TO_CLOSED ||
            event.getStateTransition() == StateTransition.HALF_OPEN_TO_CLOSED) {
            log.info("Circuit closed, replaying fallback events");
            fallbackReplayService.replayEvents();
        }
    });
```

## Testing Circuit Breaker

### Manual Testing

Use the provided demo script to test circuit breaker behavior:

```bash
./demo-circuit-breaker.sh
```

**What it does:**
1. Checks initial circuit state (CLOSED)
2. Stops Kafka container
3. Sends 20 requests to trigger circuit opening
4. Verifies circuit is OPEN
5. Restarts Kafka container
6. Waits for circuit recovery
7. Verifies circuit is CLOSED

### Step-by-Step Manual Test

#### Step 1: Check Initial State

```bash
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka'
```

**Expected Output:**
```json
{
  "name": "kafka",
  "state": "CLOSED",
  "failureRate": "0.0%",
  "slowCallRate": "0.0%",
  "bufferedCalls": 0,
  "failedCalls": 0,
  "slowCalls": 0,
  "notPermittedCalls": 0
}
```

#### Step 2: Stop Kafka

```bash
docker stop $(docker ps -q -f name=kafka)
```

#### Step 3: Send Requests

```bash
# Send 20 requests (need 10 to fill sliding window, 5 failures to reach 50%)
for i in {1..20}; do
  echo "Request $i:"
  curl -X POST http://localhost:8080/api/v1/telemetry \
    -H "Content-Type: application/json" \
    -d "{\"deviceId\": 1, \"measurement\": 25, \"date\": \"2025-01-31T10:00:0${i}Z\"}"
  echo ""
done
```

**Expected Behavior:**
- First 5 requests: Timeout after 5 seconds (Kafka unavailable)
- Request 6-10: Timeout (failure rate reaches 50%)
- Request 11+: Fail fast (circuit opens, fallback invoked)

#### Step 4: Verify Circuit is OPEN

```bash
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka'
```

**Expected Output:**
```json
{
  "name": "kafka",
  "state": "OPEN",
  "failureRate": "50.0%",
  "bufferedCalls": 10,
  "failedCalls": 5,
  "notPermittedCalls": 10
}
```

#### Step 5: Check Fallback Events

```bash
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db \
  -c "SELECT COUNT(*) FROM fallback_events;"
```

**Expected Output:**
```
 count
-------
    15
```

#### Step 6: Restart Kafka

```bash
docker start $(docker ps -aq -f name=kafka)

# Wait for Kafka to be ready
sleep 30
```

#### Step 7: Wait for Circuit Recovery

```bash
# Circuit will automatically transition to HALF_OPEN after 10 seconds
sleep 15

# Check state
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka.state'
```

**Expected Output:**
```
"HALF_OPEN"
```

#### Step 8: Send Test Requests

```bash
# Send 3 requests (permitted in half-open state)
for i in {1..3}; do
  echo "Test request $i:"
  curl -X POST http://localhost:8080/api/v1/telemetry \
    -H "Content-Type: application/json" \
    -d "{\"deviceId\": 1, \"measurement\": 25, \"date\": \"2025-01-31T10:01:0${i}Z\"}"
  echo ""
done
```

**Expected Behavior:**
- All 3 requests succeed
- Circuit transitions to CLOSED

#### Step 9: Verify Circuit is CLOSED

```bash
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka'
```

**Expected Output:**
```json
{
  "name": "kafka",
  "state": "CLOSED",
  "failureRate": "0.0%",
  "bufferedCalls": 3,
  "failedCalls": 0,
  "notPermittedCalls": 0
}
```

#### Step 10: Replay Fallback Events

```bash
# Replay events stored during circuit open
curl -X POST http://localhost:8080/api/v1/admin/fallback/replay

# Verify fallback table is empty
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db \
  -c "SELECT COUNT(*) FROM fallback_events;"
```

**Expected Output:**
```
 count
-------
     0
```

## Monitoring

### Actuator Endpoints

#### Circuit Breaker State

```bash
curl http://localhost:8080/actuator/circuitbreakers | jq
```

**Response:**
```json
{
  "circuitBreakers": {
    "kafka": {
      "name": "kafka",
      "state": "CLOSED",
      "failureRate": "0.0%",
      "slowCallRate": "0.0%",
      "bufferedCalls": 10,
      "failedCalls": 0,
      "slowCalls": 0,
      "notPermittedCalls": 0
    }
  }
}
```

#### Circuit Breaker Events

```bash
curl http://localhost:8080/actuator/circuitbreakerevents | jq
```

**Response:**
```json
{
  "circuitBreakerEvents": [
    {
      "circuitBreakerName": "kafka",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-01-31T10:00:00Z",
      "stateTransition": "CLOSED_TO_OPEN",
      "errorMessage": "Failure rate threshold exceeded"
    },
    {
      "circuitBreakerName": "kafka",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-01-31T10:00:15Z",
      "stateTransition": "OPEN_TO_HALF_OPEN",
      "errorMessage": null
    },
    {
      "circuitBreakerName": "kafka",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-01-31T10:00:20Z",
      "stateTransition": "HALF_OPEN_TO_CLOSED",
      "errorMessage": null
    }
  ]
}
```

### Metrics

#### Circuit Breaker State Metric

```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq
```

**Response:**
```json
{
  "name": "resilience4j.circuitbreaker.state",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 0.0
    }
  ],
  "availableTags": [
    {
      "tag": "name",
      "values": ["kafka"]
    }
  ]
}
```

**Values:**
- `0` = CLOSED
- `1` = OPEN
- `2` = HALF_OPEN

#### Failure Rate Metric

```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.failure.rate | jq
```

#### Calls Metric

```bash
curl 'http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:kafka&tag=kind:successful' | jq
curl 'http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:kafka&tag=kind:failed' | jq
curl 'http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:kafka&tag=kind:not_permitted' | jq
```

### Prometheus Queries

```promql
# Circuit breaker state
resilience4j_circuitbreaker_state{name="kafka"}

# Failure rate
resilience4j_circuitbreaker_failure_rate{name="kafka"}

# Not permitted calls (circuit open)
rate(resilience4j_circuitbreaker_calls_total{name="kafka",kind="not_permitted"}[1m])

# Failed calls
rate(resilience4j_circuitbreaker_calls_total{name="kafka",kind="failed"}[1m])

# Successful calls
rate(resilience4j_circuitbreaker_calls_total{name="kafka",kind="successful"}[1m])
```

### Grafana Dashboard

The pre-configured dashboard includes a circuit breaker panel:

**Panel: Circuit Breaker State**
- Query: `resilience4j_circuitbreaker_state{name="kafka"}`
- Visualization: Stat or Time series
- Thresholds:
  - Green (0): CLOSED
  - Yellow (2): HALF_OPEN
  - Red (1): OPEN

### Logging

Circuit breaker events are logged:

```
2025-01-31 10:00:00.123 WARN  ... Circuit breaker open or Kafka unavailable, using fallback
2025-01-31 10:00:00.124 INFO  ... Stored event in fallback repository: 123e4567-e89b-12d3-a456-426614174000
2025-01-31 10:00:15.456 INFO  ... Circuit breaker transitioned from OPEN to HALF_OPEN
2025-01-31 10:00:20.789 INFO  ... Circuit breaker transitioned from HALF_OPEN to CLOSED
```

## Configuration

### Application Configuration

**application.yml:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      kafka:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50.0
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - org.apache.kafka.common.errors.TimeoutException
          - org.springframework.kafka.KafkaException
```

### Configuration Parameters

| Parameter | Default | Description | Recommendation |
|-----------|---------|-------------|----------------|
| `sliding-window-type` | COUNT_BASED | Type of sliding window | COUNT_BASED for consistent traffic |
| `sliding-window-size` | 10 | Number of calls to track | 10-100 depending on traffic |
| `failure-rate-threshold` | 50% | Threshold to open circuit | 50% is typical |
| `wait-duration-in-open-state` | 10s | Time before testing recovery | 10-60s depending on recovery time |
| `permitted-calls-in-half-open` | 3 | Test calls in half-open | 3-5 is typical |
| `automatic-transition` | true | Auto-transition to half-open | true for automatic recovery |

### Tuning for Production

**High Traffic:**
```yaml
sliding-window-size: 100
failure-rate-threshold: 60.0
wait-duration-in-open-state: 30s
```

**Low Traffic:**
```yaml
sliding-window-size: 5
failure-rate-threshold: 40.0
wait-duration-in-open-state: 5s
```

**Critical Service:**
```yaml
failure-rate-threshold: 30.0  # Open circuit earlier
wait-duration-in-open-state: 60s  # Wait longer before testing
```

## Best Practices

### When to Use Circuit Breaker

**Good Use Cases:**
- External service calls (Kafka, databases, APIs)
- Operations with timeouts
- Services with known failure modes
- Non-critical operations with fallbacks

**Bad Use Cases:**
- Internal method calls
- Operations without fallbacks
- Critical operations that must succeed
- Operations with no timeout

### Fallback Strategies

**1. Store and Replay (Current Implementation)**
- Store failed events in database
- Replay when service recovers
- No data loss

**2. Return Cached Data**
- Return stale data from cache
- Better than nothing
- May be outdated

**3. Return Default Value**
- Return sensible default
- Simple but limited
- May not be appropriate

**4. Fail Gracefully**
- Return error to client
- Let client decide
- Honest about failure

### Monitoring and Alerting

**Alert on:**
- Circuit opens (immediate)
- Circuit stays open >5 minutes (critical)
- High not-permitted call rate (warning)
- Fallback repository growing (warning)

**Monitor:**
- Circuit state over time
- Failure rate trends
- Recovery time
- Fallback event count

### Testing

**Unit Tests:**
- Test circuit opens after threshold
- Test fallback invocation
- Test circuit recovery

**Integration Tests:**
- Test with real Kafka
- Test complete failure scenario
- Test replay mechanism

**Load Tests:**
- Test under high traffic
- Test recovery under load
- Test fallback performance

### Documentation

**Document:**
- Circuit breaker configuration
- Fallback behavior
- Recovery procedures
- Monitoring and alerting

---

**Related Documentation:**
- [README-PRODUCTION.md](../README-PRODUCTION.md) - Production features overview
- [observability.md](observability.md) - Observability and metrics
- [distributed-tracing.md](distributed-tracing.md) - Distributed tracing guide
- [dead-letter-queue.md](dead-letter-queue.md) - DLQ documentation
