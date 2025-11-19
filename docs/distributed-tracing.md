# Distributed Tracing

This document explains how distributed tracing works in the telemetry system, including trace propagation through Kafka, viewing traces in Zipkin, and debugging with trace IDs.

## Table of Contents

- [Overview](#overview)
- [How Tracing Works](#how-tracing-works)
- [Trace Propagation](#trace-propagation)
- [Viewing Traces in Zipkin](#viewing-traces-in-zipkin)
- [Trace IDs in Logs](#trace-ids-in-logs)
- [Debugging with Traces](#debugging-with-traces)
- [Configuration](#configuration)
- [Best Practices](#best-practices)

## Overview

Distributed tracing allows you to follow a single request as it flows through multiple components of the system. Each operation creates a "span" that records timing and metadata, and all spans for a request share the same "trace ID".

**Benefits:**
- **End-to-end visibility:** See the complete request flow
- **Performance analysis:** Identify slow operations and bottlenecks
- **Debugging:** Correlate logs and errors across components
- **Dependency mapping:** Understand service interactions

**Technology Stack:**
- **Micrometer Tracing:** Instrumentation library
- **Brave:** Tracing implementation (Zipkin-compatible)
- **Zipkin:** Trace collection and visualization

## How Tracing Works

### Trace Hierarchy

```
Trace (abc123def456)
  │
  ├─ Span 1: http-post (50ms)
  │   │
  │   ├─ Span 2: command-handler (45ms)
  │   │   │
  │   │   ├─ Span 3: db-insert (10ms)
  │   │   │
  │   │   └─ Span 4: kafka-publish (30ms)
  │   │
  │   └─ Span 5: kafka-consume (20ms)
  │       │
  │       ├─ Span 6: db-select (5ms)
  │       │
  │       └─ Span 7: db-update (10ms)
```

### Key Concepts

**Trace:**
- Represents a complete request flow
- Has a unique trace ID (e.g., `abc123def456`)
- Contains one or more spans
- Spans form a parent-child hierarchy

**Span:**
- Represents a single operation
- Has a unique span ID (e.g., `789ghi012jkl`)
- Has a parent span ID (except root span)
- Records:
  - Start time and duration
  - Operation name
  - Tags (metadata)
  - Logs (events)
  - Errors (if any)

**Context Propagation:**
- Trace context (trace ID, span ID) is propagated across boundaries
- HTTP: Via headers (`X-B3-TraceId`, `X-B3-SpanId`)
- Kafka: Via message headers
- Logs: Via MDC (Mapped Diagnostic Context)

### Request Flow with Tracing

**1. HTTP Request Arrives**
```
POST /api/v1/telemetry
  ↓
[Span: http-post] created
  traceId: abc123def456
  spanId: span001
  parentId: null (root span)
```

**2. Command Handler Processes**
```
RecordTelemetryCommandHandler.handle()
  ↓
[Span: command-handler] created
  traceId: abc123def456
  spanId: span002
  parentId: span001
```

**3. Database Write**
```
TelemetryRepository.save()
  ↓
[Span: db-insert] created
  traceId: abc123def456
  spanId: span003
  parentId: span002
  tags: {db.system: postgresql, db.operation: INSERT}
```

**4. Kafka Publish**
```
KafkaEventPublisher.publish()
  ↓
[Span: kafka-publish] created
  traceId: abc123def456
  spanId: span004
  parentId: span002
  tags: {messaging.system: kafka, messaging.destination: telemetry.recorded}
  
Kafka message headers:
  X-B3-TraceId: abc123def456
  X-B3-SpanId: span004
```

**5. Kafka Consumer Receives**
```
TelemetryEventConsumer.consume()
  ↓
Extract trace context from headers
  traceId: abc123def456 (continued)
  
[Span: kafka-consume] created
  traceId: abc123def456
  spanId: span005
  parentId: span004 (linked to producer span)
```

**6. Projection Update**
```
DeviceProjectionRepository.save()
  ↓
[Span: db-update] created
  traceId: abc123def456
  spanId: span006
  parentId: span005
  tags: {db.system: postgresql, db.operation: UPDATE}
```

**7. All Spans Sent to Zipkin**
```
All spans with traceId abc123def456 are sent to Zipkin
  ↓
Zipkin reconstructs the complete trace
  ↓
Trace is available for viewing in Zipkin UI
```

## Trace Propagation

### HTTP Propagation

Trace context is automatically propagated via HTTP headers using the B3 format.

**Request Headers:**
```
X-B3-TraceId: abc123def456
X-B3-SpanId: 789ghi012jkl
X-B3-ParentSpanId: parent123
X-B3-Sampled: 1
```

**Spring Boot Auto-configuration:**
- Automatically extracts trace context from incoming requests
- Automatically injects trace context into outgoing requests
- No manual code required

### Kafka Propagation

Trace context is propagated through Kafka message headers using custom interceptors.

**Producer Interceptor** (`TracingProducerInterceptor`):
```java
@Override
public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
    Span span = tracer.currentSpan();
    if (span != null) {
        // Add trace context to Kafka headers
        record.headers().add("X-B3-TraceId", 
            span.context().traceIdString().getBytes());
        record.headers().add("X-B3-SpanId", 
            span.context().spanIdString().getBytes());
    }
    return record;
}
```

**Consumer Interceptor** (`TracingConsumerInterceptor`):
```java
@Override
public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    for (ConsumerRecord<K, V> record : records) {
        // Extract trace context from headers
        Header traceIdHeader = record.headers().lastHeader("X-B3-TraceId");
        Header spanIdHeader = record.headers().lastHeader("X-B3-SpanId");
        
        if (traceIdHeader != null && spanIdHeader != null) {
            String traceId = new String(traceIdHeader.value());
            String spanId = new String(spanIdHeader.value());
            
            // Continue trace in consumer
            TraceContext context = TraceContext.newBuilder()
                .traceId(traceId)
                .spanId(spanId)
                .build();
            
            tracer.nextSpan(context).start();
        }
    }
    return records;
}
```

**Kafka Message Headers:**
```
Headers:
  X-B3-TraceId: abc123def456
  X-B3-SpanId: 789ghi012jkl
  content-type: application/json
```

### Database Propagation

Database operations are automatically traced by Spring Data JPA.

**Automatic Instrumentation:**
- JPA repository methods create spans
- Span names: `db-select`, `db-insert`, `db-update`, `db-delete`
- Tags include: `db.system`, `db.operation`, `db.statement`

**Example Span:**
```
Span: db-insert
  traceId: abc123def456
  spanId: span003
  duration: 10ms
  tags:
    db.system: postgresql
    db.operation: INSERT
    db.statement: INSERT INTO telemetry (...)
```

## Viewing Traces in Zipkin

### Accessing Zipkin

**URL:** http://localhost:9411

### Zipkin UI Overview

**Main Page:**
- Search bar for filtering traces
- Service name dropdown
- Span name dropdown
- Time range selector
- "Run Query" button

**Trace List:**
- Shows matching traces
- Displays: trace ID, timestamp, duration, span count
- Click trace to view details

**Trace Detail:**
- Complete span hierarchy
- Timeline visualization
- Span details (tags, logs, errors)
- Service dependency graph

### Searching for Traces

#### By Time Range

1. Select time range (e.g., "Last 15 minutes")
2. Click "Run Query"
3. View recent traces

#### By Service Name

1. Select service: `telemetry-service`
2. Click "Run Query"
3. View traces for that service

#### By Span Name

1. Select span name: `http-post`, `command-handler`, etc.
2. Click "Run Query"
3. View traces containing that span

#### By Trace ID

1. Enter trace ID in search bar
2. Click "Run Query"
3. View specific trace

**Example:**
```
Search: abc123def456
Result: Single trace with that ID
```

#### By Tags

1. Click "Add tag"
2. Enter tag key and value
3. Click "Run Query"

**Example Tags:**
- `http.method=POST`
- `http.status_code=200`
- `error=true`
- `db.operation=INSERT`

#### By Duration

1. Set min/max duration
2. Click "Run Query"
3. View slow traces

**Example:**
```
Min Duration: 100ms
Max Duration: 1000ms
Result: Traces taking 100-1000ms
```

### Analyzing Traces

#### Timeline View

Shows spans on a timeline with duration bars:

```
http-post                 [====================] 50ms
  command-handler         [==================] 45ms
    db-insert             [====] 10ms
    kafka-publish         [==============] 30ms
  kafka-consume           [==========] 20ms
    db-select             [==] 5ms
    db-update             [====] 10ms
```

**Interpretation:**
- Longer bars = slower operations
- Gaps = waiting/idle time
- Overlapping bars = parallel operations
- Sequential bars = serial operations

#### Span Details

Click on a span to view:

**Basic Info:**
- Span name
- Service name
- Duration
- Start time

**Tags:**
```
http.method: POST
http.url: /api/v1/telemetry
http.status_code: 200
component: spring-webmvc
```

**Logs:**
```
2025-01-31 10:00:00.123 - Processing telemetry for device 1
2025-01-31 10:00:00.145 - Telemetry saved successfully
```

**Errors:**
```
error: true
error.message: Database connection timeout
error.stack: java.sql.SQLException: Connection timeout...
```

#### Service Dependency Graph

Shows how services interact:

```
[Client] → [telemetry-service] → [PostgreSQL]
                ↓
            [Kafka]
                ↓
         [telemetry-service] → [PostgreSQL]
```

### Example Trace Analysis

**Scenario:** Slow request (500ms)

**Step 1: Find the trace**
```
Filter: Min Duration = 500ms
Result: Trace abc123def456 (520ms)
```

**Step 2: View timeline**
```
http-post                 [====================] 520ms
  command-handler         [==================] 515ms
    db-insert             [====] 15ms
    kafka-publish         [==============] 500ms  ← SLOW!
```

**Step 3: Analyze slow span**
```
Span: kafka-publish
Duration: 500ms
Tags:
  messaging.system: kafka
  messaging.destination: telemetry.recorded
  error: false
  
Logs:
  2025-01-31 10:00:00.123 - Waiting for Kafka acknowledgment
  2025-01-31 10:00:00.623 - Acknowledgment received
```

**Step 4: Conclusion**
- Kafka publish is slow (500ms)
- No errors, just slow acknowledgment
- Possible causes:
  - Network latency
  - Kafka broker overload
  - Large message size
  - Replication lag

## Trace IDs in Logs

### Log Format

All log messages include trace and span IDs:

```
2025-01-31 10:00:00.123 INFO [telemetry-service,abc123def456,789ghi012jkl] c.k.t.a.c.RecordTelemetryCommandHandler : Processing telemetry for device 1
                                                  ↑ application  ↑ traceId    ↑ spanId
```

**Format:**
```
[application-name,trace-id,span-id]
```

**When no trace:**
```
[telemetry-service,,,]
```

### Configuration

**application.yml:**
```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**MDC (Mapped Diagnostic Context):**
- Trace ID and span ID are stored in MDC
- Automatically included in log messages
- Available in all log statements within the trace

### Searching Logs by Trace ID

**Grep for trace ID:**
```bash
# View logs for specific trace
./gradlew bootRun | grep "abc123def456"

# Example output:
# 2025-01-31 10:00:00.123 INFO [telemetry-service,abc123def456,span001] ... HTTP POST received
# 2025-01-31 10:00:00.145 INFO [telemetry-service,abc123def456,span002] ... Processing command
# 2025-01-31 10:00:00.155 INFO [telemetry-service,abc123def456,span003] ... Saving to database
# 2025-01-31 10:00:00.165 INFO [telemetry-service,abc123def456,span004] ... Publishing to Kafka
```

**Log aggregation tools:**
- ELK Stack: Filter by `traceId` field
- Splunk: Search `traceId="abc123def456"`
- CloudWatch: Filter pattern `[traceId=abc123def456]`

### Correlating Logs with Traces

**Workflow:**
1. Find error in logs
2. Extract trace ID from log message
3. Search for trace ID in Zipkin
4. View complete trace with timing
5. Identify root cause

**Example:**
```bash
# 1. Find error in logs
./gradlew bootRun | grep ERROR

# Output:
# 2025-01-31 10:00:00.500 ERROR [telemetry-service,abc123def456,span004] ... Kafka publish failed

# 2. Extract trace ID: abc123def456

# 3. Search in Zipkin
curl 'http://localhost:9411/api/v2/trace/abc123def456' | jq

# 4. View complete trace to understand context
```

## Debugging with Traces

### Common Debugging Scenarios

#### Scenario 1: Slow Requests

**Problem:** Some requests are slow

**Steps:**
1. Filter traces by duration (e.g., >500ms)
2. Identify common slow spans
3. Analyze span tags and logs
4. Optimize slow operations

**Example:**
```
Slow span: db-insert (450ms)
Tags: db.statement: INSERT INTO telemetry ...
Conclusion: Database write is slow
Solution: Add index, optimize query, scale database
```

#### Scenario 2: Errors

**Problem:** Requests are failing

**Steps:**
1. Filter traces by error tag (`error=true`)
2. View error span details
3. Check error message and stack trace
4. Correlate with logs using trace ID

**Example:**
```
Error span: kafka-publish
Tags: 
  error: true
  error.message: Connection refused
Conclusion: Kafka is unavailable
Solution: Check Kafka health, restart if needed
```

#### Scenario 3: Missing Data

**Problem:** Data not appearing in projection

**Steps:**
1. Find trace for the write request
2. Verify kafka-publish span succeeded
3. Search for corresponding kafka-consume span
4. Check if consumer processed the message

**Example:**
```
Write trace: abc123def456
  ✓ http-post (50ms)
  ✓ command-handler (45ms)
  ✓ db-insert (10ms)
  ✓ kafka-publish (30ms)

Consumer trace: Not found!
Conclusion: Consumer didn't process message
Solution: Check consumer logs, verify consumer is running
```

#### Scenario 4: Out-of-Order Processing

**Problem:** Projection shows old data

**Steps:**
1. Find traces for both messages
2. Compare timestamps
3. Check consumer processing order
4. Verify timestamp comparison logic

**Example:**
```
Message 1 (newer):
  timestamp: 2025-01-31T10:05:00Z
  trace: abc123
  consumer span: 2025-01-31T10:05:01Z

Message 2 (older):
  timestamp: 2025-01-31T10:00:00Z
  trace: def456
  consumer span: 2025-01-31T10:05:02Z (processed after Message 1)

Conclusion: Message 2 processed after Message 1 but has older timestamp
Expected: Projection should keep Message 1 data
```

### Debugging Tips

**1. Use trace IDs from error responses:**
```bash
# Send request and capture trace ID
RESPONSE=$(curl -v -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 25.5, "date": "2025-01-31T10:00:00Z"}' 2>&1)

# Extract trace ID from response headers
TRACE_ID=$(echo "$RESPONSE" | grep -i "x-b3-traceid" | awk '{print $3}')

# Search in Zipkin
open "http://localhost:9411/zipkin/traces/$TRACE_ID"
```

**2. Add custom tags to spans:**
```java
@NewSpan
public void processData(Long deviceId) {
    Span span = tracer.currentSpan();
    if (span != null) {
        span.tag("device.id", deviceId.toString());
        span.tag("operation", "process-data");
    }
    // ... processing logic
}
```

**3. Add logs to spans:**
```java
Span span = tracer.currentSpan();
if (span != null) {
    span.event("Starting database query");
    // ... query logic
    span.event("Database query completed");
}
```

**4. Use span annotations:**
```java
@NewSpan("custom-operation")
public void customOperation() {
    // Creates a new span named "custom-operation"
}

@ContinueSpan
public void continueOperation() {
    // Continues the current span
}
```

## Configuration

### Application Configuration

**application.yml:**
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### Sampling

**Sampling Rate:**
- `1.0` = 100% (all requests traced)
- `0.1` = 10% (1 in 10 requests traced)
- `0.01` = 1% (1 in 100 requests traced)

**Production Recommendation:**
- Start with 10-20% sampling
- Adjust based on traffic volume
- Always trace errors (automatic)

**Configuration:**
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling
```

### Zipkin Configuration

**Docker Compose:**
```yaml
zipkin:
  image: openzipkin/zipkin:latest
  container_name: telemetry-zipkin
  ports:
    - "9411:9411"
  environment:
    - STORAGE_TYPE=mem  # Use in-memory storage (for development)
    # For production, use:
    # - STORAGE_TYPE=elasticsearch
    # - ES_HOSTS=elasticsearch:9200
```

**Storage Options:**
- `mem`: In-memory (lost on restart)
- `elasticsearch`: Persistent, scalable
- `cassandra`: Persistent, scalable
- `mysql`: Persistent

### Custom Tracer Configuration

**TracingConfiguration.java:**
```java
@Configuration
public class TracingConfiguration {
    
    @Bean
    public Tracer tracer() {
        return Tracing.newBuilder()
            .localServiceName("telemetry-service")
            .sampler(Sampler.ALWAYS_SAMPLE)
            .build()
            .tracer();
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory,
            Tracer tracer) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setProducerInterceptor(new TracingProducerInterceptor<>(tracer));
        return template;
    }
}
```

## Best Practices

### Span Naming

**Good:**
- `http-post` - Clear operation
- `command-handler` - Descriptive
- `db-insert` - Specific action
- `kafka-publish` - Clear intent

**Bad:**
- `process` - Too vague
- `doStuff` - Not descriptive
- `method1` - Not meaningful

### Span Tags

**Useful Tags:**
- `device.id`: Device identifier
- `operation`: Operation type
- `status`: Success/failure
- `error`: Error flag
- `db.operation`: Database operation type
- `messaging.destination`: Kafka topic

**Example:**
```java
span.tag("device.id", deviceId.toString());
span.tag("operation", "record-telemetry");
span.tag("status", "success");
```

### Span Logs

**When to Log:**
- Important state changes
- Before/after expensive operations
- Error conditions
- Debugging information

**Example:**
```java
span.event("Starting database transaction");
// ... transaction logic
span.event("Database transaction committed");
```

### Error Handling

**Always tag errors:**
```java
try {
    // ... operation
} catch (Exception e) {
    Span span = tracer.currentSpan();
    if (span != null) {
        span.tag("error", "true");
        span.tag("error.message", e.getMessage());
        span.tag("error.type", e.getClass().getName());
    }
    throw e;
}
```

### Performance

**Minimize overhead:**
- Use appropriate sampling rate
- Avoid creating too many spans
- Don't add excessive tags or logs
- Use async span reporting

**Typical Overhead:**
- 100% sampling: 2-5% CPU
- 10% sampling: <1% CPU

### Retention

**Zipkin Retention:**
- In-memory: Limited by RAM
- Elasticsearch: Configure retention policy
- Typical: 7-30 days

**Configuration:**
```yaml
# Elasticsearch retention
ZIPKIN_STORAGE_TYPE=elasticsearch
ES_INDEX_RETENTION_DAYS=7
```

### Security

**Sensitive Data:**
- Don't include passwords in tags
- Don't include PII in span names
- Sanitize error messages
- Use secure transport (HTTPS)

**Example:**
```java
// Bad
span.tag("password", password);

// Good
span.tag("auth.method", "password");
```

---

**Related Documentation:**
- [README-PRODUCTION.md](../README-PRODUCTION.md) - Production features overview
- [observability.md](observability.md) - Observability and metrics
- [circuit-breaker.md](circuit-breaker.md) - Circuit breaker documentation
- [dead-letter-queue.md](dead-letter-queue.md) - DLQ documentation
