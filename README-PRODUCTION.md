# Production-Ready Features

This document describes the production-ready enhancements added to the Telemetry Challenge system. These features make the system enterprise-grade with comprehensive observability, distributed tracing, resilience patterns, and error recovery mechanisms.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Service URLs](#service-urls)
- [Features](#features)
  - [Observability & Metrics](#observability--metrics)
  - [Distributed Tracing](#distributed-tracing)
  - [Circuit Breaker](#circuit-breaker)
  - [Dead Letter Queue](#dead-letter-queue)
  - [Health Checks](#health-checks)
- [Demo Scripts](#demo-scripts)
- [API Endpoints](#api-endpoints)
- [Monitoring & Dashboards](#monitoring--dashboards)
- [Troubleshooting](#troubleshooting)

## Overview

The production enhancements add four key capabilities to the telemetry system:

1. **Observability** - Comprehensive metrics, monitoring, and dashboards via Prometheus and Grafana
2. **Distributed Tracing** - End-to-end request tracing across all components via Zipkin
3. **Circuit Breaker** - Resilience against downstream failures with automatic fallback
4. **Dead Letter Queue** - Error recovery and message replay for failed processing

These features follow industry best practices for production-ready distributed systems and are fully demonstrable locally.

## Quick Start

### 1. Start All Services

```bash
./start.sh
```

This starts:
- PostgreSQL (port 5432)
- Kafka (port 9092)
- Zookeeper (port 2181)
- Prometheus (port 9090)
- Grafana (port 3000)
- Zipkin (port 9411)

### 2. Run the Application

```bash
./gradlew bootRun
```

The application starts on `http://localhost:8080` with all production features enabled.

### 3. Run Demo Scripts

```bash
# Run all demos in sequence
./demo-all.sh

# Or run individual demos
./demo-observability.sh    # Metrics and Grafana
./demo-tracing.sh          # Distributed tracing
./demo-circuit-breaker.sh  # Circuit breaker and fallback
./demo-dlq.sh              # Dead Letter Queue
```

### 4. Access Dashboards

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Zipkin**: http://localhost:9411
- **Actuator**: http://localhost:8080/actuator

## Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| Application | http://localhost:8080 | Main telemetry API |
| Actuator | http://localhost:8080/actuator | Health checks and metrics |
| Prometheus | http://localhost:9090 | Metrics storage and queries |
| Grafana | http://localhost:3000 | Metrics visualization (admin/admin) |
| Zipkin | http://localhost:9411 | Distributed tracing UI |
| PostgreSQL | localhost:5432 | Database (postgres/postgres) |
| Kafka | localhost:9092 | Event streaming |

## Features

### Observability & Metrics

The system exposes comprehensive metrics via Spring Boot Actuator and Micrometer, scraped by Prometheus and visualized in Grafana.

**Custom Metrics:**
- `telemetry_received_total` - Total telemetry messages received
- `telemetry_duplicates_total` - Duplicate messages detected
- `telemetry_out_of_order_total` - Out-of-order events detected
- `telemetry_processing_time` - Processing time with percentiles (P50, P95, P99)
- `telemetry_dlq_messages_total` - Messages sent to Dead Letter Queue
- `telemetry_dlq_reprocessed_total` - Messages reprocessed from DLQ

**Standard Metrics:**
- JVM metrics (memory, GC, threads)
- HTTP metrics (request count, latency, error rate)
- Kafka metrics (messages published, consumer lag)
- Database metrics (connection pool, query time)
- Circuit breaker metrics (state, failure rate)

**Viewing Metrics:**

```bash
# List all available metrics
curl http://localhost:8080/actuator/metrics | jq '.names'

# View specific metric
curl http://localhost:8080/actuator/metrics/telemetry.received.total | jq

# View Prometheus format (for scraping)
curl http://localhost:8080/actuator/prometheus
```

**Grafana Dashboard:**

The pre-configured dashboard shows:
- Telemetry ingestion rate over time
- Duplicate detection rate
- Out-of-order event rate
- HTTP request latency percentiles (P50, P95, P99)
- Circuit breaker state
- JVM memory usage
- Kafka consumer lag

Access at: http://localhost:3000 (login: admin/admin)

### Distributed Tracing

Every request is traced end-to-end across all components with a unique trace ID that propagates through HTTP, Kafka, and database operations.

**Trace Flow:**
1. HTTP request arrives → Trace ID generated
2. Command handler processes → Span created
3. Database write → Span created
4. Kafka publish → Trace ID added to message headers
5. Consumer receives → Trace ID extracted and continued
6. Projection update → Span created
7. All spans sent to Zipkin with same trace ID

**Trace IDs in Logs:**

All log messages include trace and span IDs:
```
2025-01-31 10:00:00.123 INFO [telemetry-service,abc123def456,789ghi012jkl] ...
                                                  ↑ traceId    ↑ spanId
```

**Viewing Traces:**

1. Open Zipkin UI: http://localhost:9411
2. Click "Run Query" to see recent traces
3. Click on a trace to see the complete span hierarchy
4. Filter by service name, span name, or trace ID

**Example Trace:**
```
http-post (50ms)
  └─ command-handler (45ms)
      ├─ db-insert (10ms)
      └─ kafka-publish (30ms)
          └─ kafka-consume (20ms)
              ├─ db-select (5ms)
              └─ db-update (10ms)
```

### Circuit Breaker

The circuit breaker protects against Kafka failures by failing fast and using a fallback mechanism when Kafka is unavailable.

**Circuit States:**
- **CLOSED** - Normal operation, requests pass through
- **OPEN** - Kafka is failing, requests fail fast without attempting Kafka
- **HALF_OPEN** - Testing recovery, allowing limited requests through

**Configuration:**
- Sliding window: 10 requests
- Failure threshold: 50%
- Wait duration in open state: 10 seconds
- Permitted calls in half-open: 3

**Fallback Mechanism:**

When the circuit is open:
1. Event is stored in `fallback_events` database table
2. Warning is logged with event details
3. Request still returns success to client
4. Events can be replayed when Kafka recovers

**Monitoring Circuit State:**

```bash
# Check circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers | jq

# Check circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents | jq

# View circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq
```

**Replaying Fallback Events:**

```bash
# Replay all events from fallback repository
curl -X POST http://localhost:8080/api/v1/admin/fallback/replay

# Check fallback events table
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db \
  -c "SELECT * FROM fallback_events;"
```

### Dead Letter Queue

Failed Kafka messages are automatically sent to a Dead Letter Queue after 3 retry attempts with exponential backoff.

**Retry Configuration:**
- Initial backoff: 1 second
- Backoff multiplier: 2.0
- Max attempts: 3
- Retry sequence: 1s → 2s → 4s → DLQ

**DLQ Topic:**
- Name: `telemetry.recorded.dlq`
- Contains: Original message + error details + retry count

**Viewing DLQ Messages:**

```bash
# List all messages in DLQ
curl http://localhost:8080/api/v1/admin/dlq | jq

# Example response:
# [
#   {
#     "event": {
#       "eventId": "123e4567-e89b-12d3-a456-426614174000",
#       "deviceId": 1,
#       "measurement": 25.5,
#       "date": "2025-01-31T10:00:00Z"
#     },
#     "errorMessage": "Validation failed: measurement cannot be null",
#     "retryCount": 3,
#     "timestamp": "2025-01-31T10:00:10Z"
#   }
# ]
```

**Reprocessing DLQ Messages:**

```bash
# Reprocess all messages from DLQ
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess

# Response: 202 Accepted
```

**DLQ Metrics:**

```bash
# View DLQ metrics
curl http://localhost:8080/actuator/metrics/telemetry.dlq.messages.total | jq
curl http://localhost:8080/actuator/metrics/telemetry.dlq.reprocessed.total | jq
```

### Health Checks

Comprehensive health checks verify all system dependencies and provide liveness/readiness probes for Kubernetes.

**Health Endpoints:**

```bash
# Overall health status
curl http://localhost:8080/actuator/health | jq

# Liveness probe (for Kubernetes)
curl http://localhost:8080/actuator/health/liveness | jq

# Readiness probe (for Kubernetes)
curl http://localhost:8080/actuator/health/readiness | jq
```

**Health Indicators:**
- PostgreSQL connectivity
- Kafka connectivity
- Circuit breaker state
- Disk space
- Application status

**Example Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "kafka-cluster"
      }
    },
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "kafka": "CLOSED"
      }
    }
  }
}
```

## Demo Scripts

All features can be demonstrated using the provided scripts.

### demo-observability.sh

Demonstrates metrics collection and Grafana dashboards.

```bash
./demo-observability.sh
```

**What it does:**
1. Generates 100 telemetry requests with random data
2. Displays metrics from Actuator endpoints
3. Shows instructions to view Grafana dashboard

**Expected output:**
- Metrics showing 100+ requests received
- Grafana dashboard showing ingestion rate spike
- Processing time percentiles

### demo-tracing.sh

Demonstrates distributed tracing with Zipkin.

```bash
./demo-tracing.sh
```

**What it does:**
1. Sends sample telemetry request
2. Shows instructions to view trace in Zipkin
3. Explains how to search for traces

**Expected output:**
- Trace ID in response
- Complete span hierarchy in Zipkin UI
- Trace propagation through Kafka visible

### demo-circuit-breaker.sh

Demonstrates circuit breaker opening, fallback, and recovery.

```bash
./demo-circuit-breaker.sh
```

**What it does:**
1. Checks initial circuit state (CLOSED)
2. Stops Kafka container
3. Sends requests to trigger circuit opening
4. Verifies circuit is OPEN
5. Restarts Kafka
6. Waits for circuit recovery
7. Verifies circuit is CLOSED again

**Expected output:**
- Circuit transitions: CLOSED → OPEN → HALF_OPEN → CLOSED
- Fallback events stored in database
- Successful recovery after Kafka restart

### demo-dlq.sh

Demonstrates Dead Letter Queue and message reprocessing.

```bash
./demo-dlq.sh
```

**What it does:**
1. Sends invalid telemetry to trigger error
2. Waits for retries and DLQ
3. Lists messages in DLQ
4. Reprocesses DLQ messages
5. Verifies DLQ is empty

**Expected output:**
- Message appears in DLQ after 3 retries
- Error details and retry count visible
- Successful reprocessing
- DLQ empty after reprocessing

### demo-all.sh

Runs all demos in sequence with pauses between each.

```bash
./demo-all.sh
```

**Duration:** ~5 minutes

## API Endpoints

### Production Endpoints

#### Actuator Endpoints

```bash
# Health check
GET http://localhost:8080/actuator/health

# Liveness probe
GET http://localhost:8080/actuator/health/liveness

# Readiness probe
GET http://localhost:8080/actuator/health/readiness

# All metrics
GET http://localhost:8080/actuator/metrics

# Specific metric
GET http://localhost:8080/actuator/metrics/{metric.name}

# Prometheus format
GET http://localhost:8080/actuator/prometheus

# Circuit breaker state
GET http://localhost:8080/actuator/circuitbreakers

# Circuit breaker events
GET http://localhost:8080/actuator/circuitbreakerevents
```

#### Admin Endpoints

```bash
# List DLQ messages
GET http://localhost:8080/api/v1/admin/dlq

# Reprocess DLQ messages
POST http://localhost:8080/api/v1/admin/dlq/reprocess

# Replay fallback events
POST http://localhost:8080/api/v1/admin/fallback/replay
```

### Example cURL Commands

```bash
# Check overall health
curl http://localhost:8080/actuator/health | jq

# View telemetry metrics
curl http://localhost:8080/actuator/metrics/telemetry.received.total | jq

# Check circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka.state'

# List DLQ messages
curl http://localhost:8080/api/v1/admin/dlq | jq

# Reprocess DLQ
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess

# Replay fallback events
curl -X POST http://localhost:8080/api/v1/admin/fallback/replay

# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep telemetry
```

## Monitoring & Dashboards

### Prometheus

**Access:** http://localhost:9090

**Useful Queries:**

```promql
# Telemetry ingestion rate (per second)
rate(telemetry_received_total[1m])

# Duplicate detection rate
rate(telemetry_duplicates_total[1m])

# Out-of-order events
rate(telemetry_out_of_order_total[1m])

# HTTP request latency (95th percentile)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="kafka"}

# DLQ message rate
rate(telemetry_dlq_messages_total[1m])

# JVM memory usage
jvm_memory_used_bytes{area="heap"}

# Kafka consumer lag
kafka_consumer_lag
```

### Grafana

**Access:** http://localhost:3000 (admin/admin)

**Pre-configured Dashboard:** "Telemetry System Overview"

**Panels:**
1. **Telemetry Ingestion Rate** - Messages per second over time
2. **Duplicate Detection Rate** - Duplicates detected per second
3. **Out-of-Order Events** - Late arrivals per second
4. **HTTP Request Latency** - P50, P95, P99 percentiles
5. **Circuit Breaker State** - Current state visualization
6. **JVM Memory Usage** - Heap and non-heap memory
7. **Kafka Consumer Lag** - Processing delay
8. **DLQ Messages** - Failed messages over time

**Creating Custom Dashboards:**
1. Click "+" → "Dashboard"
2. Add panel
3. Select Prometheus datasource
4. Enter PromQL query
5. Configure visualization
6. Save dashboard

### Zipkin

**Access:** http://localhost:9411

**Viewing Traces:**
1. Click "Run Query" to see recent traces
2. Filter by:
   - Service name: `telemetry-service`
   - Span name: `http-post`, `command-handler`, `kafka-publish`, etc.
   - Min/max duration
   - Tags
3. Click trace to see span hierarchy
4. View timing breakdown and dependencies

**Trace Analysis:**
- Identify slow operations
- Find bottlenecks in request flow
- Debug cross-service issues
- Verify trace propagation through Kafka

## Troubleshooting

### Metrics Not Appearing in Prometheus

**Problem:** Prometheus shows no data or "No data" in Grafana.

**Solution:**
```bash
# Check Prometheus is scraping the application
curl http://localhost:9090/api/v1/targets | jq

# Verify application exposes metrics
curl http://localhost:8080/actuator/prometheus | head -20

# Check Prometheus configuration
docker exec -it $(docker ps -q -f name=prometheus) cat /etc/prometheus/prometheus.yml

# Restart Prometheus
docker restart $(docker ps -q -f name=prometheus)
```

### Traces Not Appearing in Zipkin

**Problem:** Zipkin shows no traces.

**Solution:**
```bash
# Verify Zipkin is running
curl http://localhost:9411/api/v2/services

# Check application tracing configuration
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("tracing"))'

# Verify trace IDs in logs
./gradlew bootRun | grep "traceId"

# Send test request and check logs
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 25.5, "date": "2025-01-31T10:00:00Z"}'

# Restart Zipkin
docker restart $(docker ps -q -f name=zipkin)
```

### Circuit Breaker Not Opening

**Problem:** Circuit stays CLOSED even when Kafka is down.

**Solution:**
```bash
# Check circuit breaker configuration
curl http://localhost:8080/actuator/configprops | jq '.["resilience4j"]'

# Verify Kafka is actually down
docker ps | grep kafka

# Send enough requests to trigger threshold (need 10 requests with 50% failure)
for i in {1..20}; do
  curl -X POST http://localhost:8080/api/v1/telemetry \
    -H "Content-Type: application/json" \
    -d '{"deviceId": 1, "measurement": 25, "date": "2025-01-31T10:00:00Z"}'
done

# Check circuit state
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka'

# Check circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents | jq
```

### DLQ Messages Not Appearing

**Problem:** Failed messages don't appear in DLQ.

**Solution:**
```bash
# Check Kafka error handler configuration
# Verify DLQ topic exists
docker exec -it $(docker ps -q -f name=kafka) kafka-topics \
  --list --bootstrap-server localhost:9092 | grep dlq

# Create DLQ topic if missing
docker exec -it $(docker ps -q -f name=kafka) kafka-topics \
  --create --topic telemetry.recorded.dlq \
  --bootstrap-server localhost:9092 \
  --partitions 1 --replication-factor 1

# Check consumer logs for errors
./gradlew bootRun | grep "DLQ\|Dead Letter"

# Manually check DLQ topic
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic telemetry.recorded.dlq \
  --from-beginning
```

### Grafana Dashboard Not Loading

**Problem:** Grafana shows "No data" or dashboard doesn't load.

**Solution:**
```bash
# Check Grafana datasource
curl -u admin:admin http://localhost:3000/api/datasources | jq

# Test Prometheus connection from Grafana
curl -u admin:admin http://localhost:3000/api/datasources/proxy/1/api/v1/query?query=up

# Verify dashboard provisioning
docker exec -it $(docker ps -q -f name=grafana) ls -la /var/lib/grafana/dashboards

# Restart Grafana
docker restart $(docker ps -q -f name=grafana)

# Re-provision dashboard
docker restart $(docker ps -q -f name=grafana)
sleep 10
open http://localhost:3000
```

### High Memory Usage

**Problem:** Application consumes excessive memory.

**Solution:**
```bash
# Check JVM memory metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# View heap dump
curl http://localhost:8080/actuator/heapdump -o heapdump.hprof

# Adjust JVM settings
export JAVA_OPTS="-Xmx512m -Xms256m"
./gradlew bootRun

# Monitor GC activity
curl http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
```

## Performance Considerations

### Metrics Overhead

- **CPU Impact:** <1% with current metric set
- **Memory Impact:** ~10MB for metric storage
- **Network Impact:** Minimal (Prometheus scrapes every 15s)

**Optimization:**
- Reduce scrape interval if needed
- Disable unused metrics
- Use metric sampling for high-cardinality metrics

### Tracing Overhead

- **CPU Impact:** 2-5% with 100% sampling
- **Memory Impact:** ~20MB for span buffering
- **Network Impact:** Spans sent asynchronously to Zipkin

**Optimization:**
```yaml
# Reduce sampling rate in production
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests
```

### Circuit Breaker Overhead

- **Latency Impact:** <1ms per request
- **Memory Impact:** Minimal (sliding window of 10 requests)

### DLQ Overhead

- **Impact:** Only on failures (no overhead for successful processing)
- **Storage:** DLQ messages stored in Kafka topic

## Production Deployment Checklist

Before deploying to production:

- [ ] Reduce tracing sampling rate (10-20%)
- [ ] Configure metric retention in Prometheus
- [ ] Set up Grafana alerting rules
- [ ] Secure Actuator endpoints (Spring Security)
- [ ] Change Grafana admin password
- [ ] Configure persistent storage for Grafana dashboards
- [ ] Set up log aggregation (ELK, Splunk)
- [ ] Configure circuit breaker thresholds for production load
- [ ] Set up DLQ monitoring and alerting
- [ ] Configure backup and retention for DLQ messages
- [ ] Enable TLS for all services
- [ ] Set up authentication for admin endpoints
- [ ] Configure resource limits (CPU, memory)
- [ ] Set up horizontal pod autoscaling
- [ ] Configure liveness and readiness probes in Kubernetes
- [ ] Set up disaster recovery procedures

## Additional Resources

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/)
- [Zipkin Documentation](https://zipkin.io/pages/quickstart.html)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)

---

**For the base telemetry system documentation, see [README.md](README.md)**
