# Dead Letter Queue (DLQ)

This document explains the Dead Letter Queue implementation in the telemetry system, including how failed messages are handled, how to view and reprocess DLQ messages, and best practices for error recovery.

## Table of Contents

- [Overview](#overview)
- [How DLQ Works](#how-dlq-works)
- [Retry Mechanism](#retry-mechanism)
- [Viewing DLQ Messages](#viewing-dlq-messages)
- [Reprocessing Messages](#reprocessing-messages)
- [Testing DLQ](#testing-dlq)
- [Monitoring](#monitoring)
- [Configuration](#configuration)
- [Best Practices](#best-practices)

## Overview

A Dead Letter Queue (DLQ) is a special Kafka topic that stores messages that cannot be processed successfully after multiple retry attempts. This prevents failed messages from blocking the consumer and allows for manual investigation and reprocessing.

**Benefits:**
- **Error Recovery:** Failed messages can be investigated and reprocessed
- **No Message Loss:** Failed messages are preserved, not discarded
- **Consumer Protection:** Failed messages don't block processing of other messages
- **Debugging:** Error details and retry count are preserved

**Technology:**
- **Spring Kafka:** Error handling and DLQ publishing
- **Kafka Topics:** Main topic + DLQ topic
- **Exponential Backoff:** Retry with increasing delays

## How DLQ Works

### Message Flow

#### Successful Processing

```
Kafka Topic: telemetry.recorded
    ↓
TelemetryEventConsumer
    ↓
Process message
    ↓
Update projection
    ↓
Commit offset
    ↓
✓ Success
```

#### Failed Processing (with Retries)

```
Kafka Topic: telemetry.recorded
    ↓
TelemetryEventConsumer
    ↓
Process message → ERROR
    ↓
Retry 1 (after 1s) → ERROR
    ↓
Retry 2 (after 2s) → ERROR
    ↓
Retry 3 (after 4s) → ERROR
    ↓
Send to DLQ Topic: telemetry.recorded.dlq
    ↓
Commit offset (message removed from main topic)
```

### DLQ Topic

**Topic Name:** `telemetry.recorded.dlq`

**Message Format:**
```json
{
  "event": {
    "eventId": "123e4567-e89b-12d3-a456-426614174000",
    "deviceId": 1,
    "measurement": 25.5,
    "date": "2025-01-31T10:00:00Z"
  }
}
```

**Headers:**
```
X-B3-TraceId: abc123def456
X-B3-SpanId: 789ghi012jkl
exception-message: Validation failed: measurement cannot be null
exception-type: com.koni.telemetry.domain.exception.ValidationException
retry-count: 3
original-topic: telemetry.recorded
original-partition: 0
original-offset: 12345
timestamp: 1706697610000
```

### Error Handler Configuration

**KafkaErrorHandlingConfig:**
```java
@Configuration
public class KafkaErrorHandlingConfig {
    
    @Bean
    public DefaultErrorHandler errorHandler(
            KafkaTemplate<String, Object> template,
            TelemetryMetrics metrics) {
        
        // Configure DLQ with custom recoverer
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(template,
                (record, ex) -> {
                    metrics.recordDlqMessage();
                    log.error("Sending message to DLQ after {} retries: {}", 
                        record.headers().lastHeader("retry-count"), 
                        record.value(), ex);
                    return new TopicPartition("telemetry.recorded.dlq", -1);
                });
        
        // Configure exponential backoff: 1s, 2s, 4s
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        
        // Add retry count to headers
        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            record.headers().add("retry-count", 
                String.valueOf(deliveryAttempt).getBytes());
        });
        
        return handler;
    }
}
```

## Retry Mechanism

### Exponential Backoff

The system uses exponential backoff for retries:

| Attempt | Delay | Total Time |
|---------|-------|------------|
| 1 (initial) | 0s | 0s |
| 2 (retry 1) | 1s | 1s |
| 3 (retry 2) | 2s | 3s |
| 4 (retry 3) | 4s | 7s |
| → DLQ | - | 7s |

**Configuration:**
```java
ExponentialBackOff backOff = new ExponentialBackOff(
    1000L,  // Initial interval: 1 second
    2.0     // Multiplier: 2x
);
backOff.setMaxAttempts(3);  // 3 retry attempts
```

### Retry Logic

**Retryable Errors:**
- Transient database errors
- Network timeouts
- Temporary service unavailability
- Validation errors (will fail all retries)

**Non-Retryable Errors:**
- Deserialization errors (sent to DLQ immediately)
- Authentication errors
- Authorization errors

**Example Timeline:**
```
10:00:00.000 - Message received, processing fails
10:00:00.001 - Log error, schedule retry 1
10:00:01.000 - Retry 1, processing fails
10:00:01.001 - Log error, schedule retry 2
10:00:03.000 - Retry 2, processing fails
10:00:03.001 - Log error, schedule retry 3
10:00:07.000 - Retry 3, processing fails
10:00:07.001 - Send to DLQ, commit offset
```

### Retry Headers

Each retry adds headers to the message:

```
retry-count: 1  (after first retry)
retry-count: 2  (after second retry)
retry-count: 3  (after third retry, before DLQ)
```

## Viewing DLQ Messages

### Via Admin Endpoint

**Endpoint:**
```
GET /api/v1/admin/dlq
```

**cURL Example:**
```bash
curl http://localhost:8080/api/v1/admin/dlq | jq
```

**Response:**
```json
[
  {
    "event": {
      "eventId": "123e4567-e89b-12d3-a456-426614174000",
      "deviceId": 1,
      "measurement": 25.5,
      "date": "2025-01-31T10:00:00Z"
    },
    "errorMessage": "Validation failed: measurement cannot be null",
    "retryCount": 3,
    "timestamp": "2025-01-31T10:00:10Z"
  },
  {
    "event": {
      "eventId": "223e4567-e89b-12d3-a456-426614174001",
      "deviceId": 2,
      "measurement": 18.2,
      "date": "2025-01-31T10:00:01Z"
    },
    "errorMessage": "Database connection timeout",
    "retryCount": 3,
    "timestamp": "2025-01-31T10:00:11Z"
  }
]
```

### Via Kafka Console Consumer

**Command:**
```bash
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic telemetry.recorded.dlq \
  --from-beginning \
  --property print.headers=true \
  --property print.timestamp=true
```

**Output:**
```
timestamp:1706697610000 headers:X-B3-TraceId:abc123,exception-message:Validation failed,retry-count:3
{"eventId":"123e4567-e89b-12d3-a456-426614174000","deviceId":1,"measurement":25.5,"date":"2025-01-31T10:00:00Z"}
```

### Via DlqManagementService

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class DlqManagementService {
    
    private final ConsumerFactory<String, TelemetryRecorded> consumerFactory;
    
    public List<DlqMessage> listDlqMessages() {
        List<DlqMessage> messages = new ArrayList<>();
        
        try (Consumer<String, TelemetryRecorded> consumer = 
                consumerFactory.createConsumer("dlq-reader", "dlq-reader")) {
            
            consumer.subscribe(Collections.singletonList("telemetry.recorded.dlq"));
            
            ConsumerRecords<String, TelemetryRecorded> records = 
                consumer.poll(Duration.ofSeconds(5));
            
            for (ConsumerRecord<String, TelemetryRecorded> record : records) {
                String error = new String(record.headers()
                    .lastHeader("exception-message").value());
                int retryCount = Integer.parseInt(new String(record.headers()
                    .lastHeader("retry-count").value()));
                
                messages.add(new DlqMessage(
                    record.value(),
                    error,
                    retryCount,
                    Instant.ofEpochMilli(record.timestamp())
                ));
            }
        }
        
        return messages;
    }
}
```

## Reprocessing Messages

### Via Admin Endpoint

**Endpoint:**
```
POST /api/v1/admin/dlq/reprocess
```

**Behavior:**
1. Consumes all messages from DLQ topic
2. Republishes each message to main topic (`telemetry.recorded`)
3. Logs success/failure for each message
4. Returns 202 Accepted

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess

# Response: 202 Accepted
```

**Implementation:**
```java
public void reprocessDlqMessages() {
    List<DlqMessage> messages = listDlqMessages();
    
    log.info("Reprocessing {} DLQ messages", messages.size());
    
    for (DlqMessage message : messages) {
        try {
            // Republish to main topic
            kafkaTemplate.send("telemetry.recorded", 
                message.getEvent().getDeviceId().toString(),
                message.getEvent());
            
            log.info("Reprocessed DLQ message: {}", message.getEvent().getEventId());
        } catch (Exception e) {
            log.error("Failed to reprocess DLQ message: {}", 
                message.getEvent().getEventId(), e);
        }
    }
}
```

### Manual Reprocessing

**Step 1: Identify the issue**
```bash
# View DLQ messages
curl http://localhost:8080/api/v1/admin/dlq | jq

# Analyze error messages
curl http://localhost:8080/api/v1/admin/dlq | jq '.[].errorMessage'
```

**Step 2: Fix the issue**
- Fix validation logic
- Fix database schema
- Fix consumer code
- Restart services

**Step 3: Reprocess messages**
```bash
# Reprocess all DLQ messages
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess
```

**Step 4: Verify success**
```bash
# Check DLQ is empty
curl http://localhost:8080/api/v1/admin/dlq | jq 'length'

# Expected: 0

# Check projection was updated
curl http://localhost:8080/api/v1/devices | jq
```

### Selective Reprocessing

For selective reprocessing, you can:

1. **Consume specific messages from DLQ**
2. **Republish only those messages**
3. **Leave others in DLQ**

**Example:**
```bash
# Consume DLQ messages
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic telemetry.recorded.dlq \
  --from-beginning \
  --max-messages 10 > dlq_messages.json

# Filter and republish specific messages
# (requires custom script)
```

## Testing DLQ

### Using Demo Script

```bash
./demo-dlq.sh
```

**What it does:**
1. Sends invalid telemetry to trigger error
2. Waits for retries and DLQ
3. Lists DLQ messages
4. Reprocesses DLQ messages
5. Verifies DLQ is empty

### Manual Testing

#### Step 1: Trigger an Error

**Option A: Send invalid data**
```bash
# Send telemetry with null measurement (will fail validation)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 999,
    "measurement": null,
    "date": "2025-01-31T10:00:00Z"
  }'
```

**Option B: Simulate database error**
```bash
# Stop PostgreSQL temporarily
docker stop $(docker ps -q -f name=postgres)

# Send valid telemetry (will fail due to database unavailable)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "measurement": 25.5,
    "date": "2025-01-31T10:00:00Z"
  }'

# Restart PostgreSQL
docker start $(docker ps -aq -f name=postgres)
```

#### Step 2: Wait for Retries

```bash
# Wait for retries to complete (7 seconds total)
sleep 10
```

**Watch logs:**
```bash
./gradlew bootRun | grep -E "retry|DLQ"
```

**Expected log output:**
```
2025-01-31 10:00:00.000 ERROR ... Processing failed, attempt 1/3
2025-01-31 10:00:01.000 ERROR ... Processing failed, attempt 2/3
2025-01-31 10:00:03.000 ERROR ... Processing failed, attempt 3/3
2025-01-31 10:00:07.000 ERROR ... Sending message to DLQ after 3 retries
```

#### Step 3: Verify Message in DLQ

```bash
# List DLQ messages
curl http://localhost:8080/api/v1/admin/dlq | jq

# Check DLQ topic directly
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic telemetry.recorded.dlq \
  --from-beginning \
  --max-messages 1
```

#### Step 4: Reprocess Messages

```bash
# Reprocess all DLQ messages
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess
```

#### Step 5: Verify DLQ is Empty

```bash
# Check DLQ is empty
curl http://localhost:8080/api/v1/admin/dlq | jq 'length'

# Expected: 0
```

## Monitoring

### Metrics

#### DLQ Message Counter

```bash
curl http://localhost:8080/actuator/metrics/telemetry.dlq.messages.total | jq
```

**Response:**
```json
{
  "name": "telemetry.dlq.messages.total",
  "description": "Total messages sent to DLQ",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 5.0
    }
  ]
}
```

#### DLQ Reprocessed Counter

```bash
curl http://localhost:8080/actuator/metrics/telemetry.dlq.reprocessed.total | jq
```

### Prometheus Queries

```promql
# DLQ message rate (per second)
rate(telemetry_dlq_messages_total[1m])

# Total DLQ messages
telemetry_dlq_messages_total

# DLQ reprocessing rate
rate(telemetry_dlq_reprocessed_total[1m])

# Alert if DLQ messages detected
telemetry_dlq_messages_total > 0
```

### Grafana Dashboard

**Panel: DLQ Messages**
- Query: `rate(telemetry_dlq_messages_total[1m])`
- Visualization: Time series
- Alert: Value > 0 for 2 minutes

**Panel: DLQ Message Count**
- Query: `telemetry_dlq_messages_total`
- Visualization: Stat
- Threshold: Red if > 0

### Kafka Monitoring

**Check DLQ topic lag:**
```bash
docker exec -it $(docker ps -q -f name=kafka) kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group dlq-reader
```

**Check DLQ topic size:**
```bash
docker exec -it $(docker ps -q -f name=kafka) kafka-run-class \
  kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic telemetry.recorded.dlq
```

### Logging

DLQ events are logged:

```
2025-01-31 10:00:00.000 ERROR [telemetry-service,abc123,span001] ... Processing failed: Validation error
2025-01-31 10:00:01.000 ERROR [telemetry-service,abc123,span001] ... Retry 1/3 failed
2025-01-31 10:00:03.000 ERROR [telemetry-service,abc123,span001] ... Retry 2/3 failed
2025-01-31 10:00:07.000 ERROR [telemetry-service,abc123,span001] ... Retry 3/3 failed
2025-01-31 10:00:07.001 ERROR [telemetry-service,abc123,span001] ... Sending message to DLQ after 3 retries: {...}
2025-01-31 10:05:00.000 INFO  [telemetry-service,def456,span002] ... Reprocessing 5 DLQ messages
2025-01-31 10:05:00.100 INFO  [telemetry-service,def456,span002] ... Reprocessed DLQ message: 123e4567-e89b-12d3-a456-426614174000
```

## Configuration

### Application Configuration

**application.yml:**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Manual offset commit
      auto-offset-reset: earliest
    
    listener:
      ack-mode: manual  # Manual acknowledgment
```

### Error Handler Configuration

**KafkaErrorHandlingConfig.java:**
```java
@Bean
public DefaultErrorHandler errorHandler(
        KafkaTemplate<String, Object> template,
        TelemetryMetrics metrics) {
    
    // DLQ recoverer
    DeadLetterPublishingRecoverer recoverer = 
        new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                metrics.recordDlqMessage();
                log.error("Sending to DLQ: {}", record.value(), ex);
                return new TopicPartition("telemetry.recorded.dlq", -1);
            });
    
    // Exponential backoff
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxAttempts(3);
    
    // Error handler
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    
    // Add retry count to headers
    handler.setRetryListeners((record, ex, deliveryAttempt) -> {
        record.headers().add("retry-count", 
            String.valueOf(deliveryAttempt).getBytes());
    });
    
    return handler;
}
```

### Tuning Parameters

| Parameter | Default | Description | Recommendation |
|-----------|---------|-------------|----------------|
| Initial interval | 1000ms | First retry delay | 1-5 seconds |
| Multiplier | 2.0 | Backoff multiplier | 2.0 is typical |
| Max attempts | 3 | Number of retries | 3-5 attempts |
| DLQ topic | `{topic}.dlq` | DLQ topic name | Use `.dlq` suffix |

**For transient errors:**
```java
ExponentialBackOff backOff = new ExponentialBackOff(
    2000L,  // 2 second initial delay
    2.0     // 2x multiplier
);
backOff.setMaxAttempts(5);  // 5 retries
// Sequence: 2s, 4s, 8s, 16s, 32s = 62s total
```

**For quick failures:**
```java
ExponentialBackOff backOff = new ExponentialBackOff(
    500L,   // 500ms initial delay
    1.5     // 1.5x multiplier
);
backOff.setMaxAttempts(3);  // 3 retries
// Sequence: 500ms, 750ms, 1125ms = 2.375s total
```

## Best Practices

### When to Use DLQ

**Good Use Cases:**
- Validation errors (permanent failures)
- Deserialization errors
- Business logic errors
- Poison messages

**Bad Use Cases:**
- Transient network errors (use retries)
- Temporary service unavailability (use circuit breaker)
- Expected errors (handle in code)

### DLQ Management

**Regular Monitoring:**
- Check DLQ daily
- Alert on non-zero DLQ count
- Investigate root causes
- Reprocess after fixes

**Retention Policy:**
- Keep DLQ messages for 7-30 days
- Archive old messages
- Clean up after reprocessing

**Documentation:**
- Document common DLQ scenarios
- Document reprocessing procedures
- Document escalation paths

### Error Handling Strategy

**Layered Approach:**
1. **Retries:** Handle transient errors (3-5 attempts)
2. **Circuit Breaker:** Handle service unavailability
3. **DLQ:** Handle permanent failures
4. **Alerting:** Notify on DLQ messages

**Error Classification:**
- **Transient:** Retry (network, timeout)
- **Permanent:** DLQ (validation, deserialization)
- **Critical:** Alert (data corruption, security)

### Reprocessing Strategy

**Before Reprocessing:**
1. Identify root cause
2. Fix the issue
3. Test the fix
4. Verify system health

**During Reprocessing:**
1. Reprocess in batches
2. Monitor for errors
3. Verify results
4. Check for new DLQ messages

**After Reprocessing:**
1. Verify DLQ is empty
2. Verify data integrity
3. Document the incident
4. Update runbooks

### Testing

**Unit Tests:**
- Test error handler configuration
- Test DLQ publishing
- Test reprocessing logic

**Integration Tests:**
- Test with real Kafka
- Test retry mechanism
- Test DLQ flow end-to-end

**Chaos Testing:**
- Inject random errors
- Test DLQ under load
- Test reprocessing at scale

### Monitoring and Alerting

**Alerts:**
- DLQ messages detected (warning)
- DLQ count > 100 (critical)
- DLQ reprocessing failed (critical)
- DLQ topic lag increasing (warning)

**Dashboards:**
- DLQ message rate
- DLQ message count
- Reprocessing rate
- Error types distribution

---

**Related Documentation:**
- [README-PRODUCTION.md](../README-PRODUCTION.md) - Production features overview
- [observability.md](observability.md) - Observability and metrics
- [distributed-tracing.md](distributed-tracing.md) - Distributed tracing guide
- [circuit-breaker.md](circuit-breaker.md) - Circuit breaker documentation
