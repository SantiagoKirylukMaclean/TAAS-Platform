# Production Features

Enterprise-grade features for observability, resilience, and error recovery.

**📖 For setup instructions, see main [README.md](README.md)**

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://localhost:8080 | - |
| Grafana | http://localhost:3000 | admin/admin |
| Prometheus | http://localhost:9090 | - |
| Zipkin | http://localhost:9411 | - |
| Actuator | http://localhost:8080/actuator | - |

## Features Overview

### 1. Observability & Metrics

**What:** Real-time monitoring with Prometheus and Grafana

**Custom Metrics:**
- `telemetry_received_total` - Total messages received
- `telemetry_duplicates_total` - Duplicates detected
- `telemetry_out_of_order_total` - Out-of-order events
- `telemetry_processing_time` - Processing time (P50, P95, P99)
- `telemetry_dlq_messages_total` - Messages sent to DLQ

**Standard Metrics:** JVM, HTTP, Kafka, Database, Circuit Breaker

**View Metrics:**
```bash
# List all metrics
curl http://localhost:8080/actuator/metrics | jq '.names'

# Specific metric
curl http://localhost:8080/actuator/metrics/telemetry.received.total | jq

# Prometheus format
curl http://localhost:8080/actuator/prometheus
```

**Grafana Dashboard:** Pre-configured "Telemetry System Overview" dashboard

**Demo:** `./demo-observability.sh`

### 2. Distributed Tracing

**What:** End-to-end request tracing with Zipkin

**Trace Flow:**
```
HTTP → Command Handler → DB → Kafka → Consumer → Projection
  └─────────────── All linked by traceId ──────────────┘
```

**Features:**
- Unique trace ID per request
- Trace propagation through Kafka headers
- Trace IDs in all log messages: `[traceId,spanId]`
- Complete span hierarchy in Zipkin UI

**View Traces:**
1. Open http://localhost:9411
2. Click "Run Query"
3. Select a trace to see span hierarchy

**Demo:** `./demo-tracing.sh`

### 3. Circuit Breaker

**What:** Resilience against Kafka failures with Resilience4j

**Configuration:**
- Sliding window: 10 requests
- Failure threshold: 50%
- Wait in open state: 10 seconds
- Half-open permitted calls: 3

**States:**
- **CLOSED** - Normal operation
- **OPEN** - Kafka failing, using fallback
- **HALF_OPEN** - Testing recovery

**Fallback Mechanism:**
- Events stored in `fallback_events` table when circuit is open
- Replay via: `POST /api/v1/admin/fallback/replay`

**Monitor State:**
```bash
# Check circuit state
curl http://localhost:8080/actuator/circuitbreakers | jq

# Check events
curl http://localhost:8080/actuator/circuitbreakerevents | jq
```

**Demo:** `./demo-circuit-breaker.sh`

### 4. Dead Letter Queue

**What:** Error recovery for failed message processing

**Configuration:**
- Retry attempts: 3
- Backoff: 1s → 2s → 4s (exponential)
- DLQ topic: `telemetry.recorded.dlq`

**Management:**
```bash
# List DLQ messages
curl http://localhost:8080/api/v1/admin/dlq | jq

# Reprocess all DLQ messages
curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess
```

**Demo:** `./demo-dlq.sh`

### 5. Health Checks

**What:** Kubernetes-ready health probes

**Endpoints:**
```bash
# Overall health
curl http://localhost:8080/actuator/health | jq

# Liveness probe
curl http://localhost:8080/actuator/health/liveness | jq

# Readiness probe
curl http://localhost:8080/actuator/health/readiness | jq
```

**Health Indicators:**
- PostgreSQL connectivity
- Kafka connectivity
- Circuit breaker state
- Disk space

## Demo Scripts

```bash
./demo-observability.sh           # Generate load, view metrics in Grafana
./demo-tracing.sh                 # Send request, view trace in Zipkin
./demo-circuit-breaker.sh         # Stop Kafka, trigger circuit, restart (detailed)
./demo-circuit-breaker-quick.sh   # Same as above but faster, auto-stops when circuit opens
./demo-dlq.sh                     # Trigger error, view DLQ, reprocess
./demo-all.sh                     # Run all demos (~5 minutes)
```

## Monitoring Dashboards

### Grafana (http://localhost:3000)

**Pre-configured panels:**
1. Telemetry ingestion rate
2. Duplicate detection rate
3. Out-of-order events
4. HTTP request latency (P50, P95, P99)
5. Circuit breaker state
6. JVM memory usage
7. Kafka consumer lag
8. DLQ messages

### Prometheus (http://localhost:9090)

**Useful queries:**
```promql
# Ingestion rate
rate(telemetry_received_total[1m])

# Duplicate rate
rate(telemetry_duplicates_total[1m])

# HTTP latency (95th percentile)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# Circuit breaker state
resilience4j_circuitbreaker_state{name="kafka"}

# JVM memory
jvm_memory_used_bytes{area="heap"}
```

### Zipkin (http://localhost:9411)

**Features:**
- View recent traces
- Filter by service, span name, duration
- See complete span hierarchy
- Identify bottlenecks

## Admin Endpoints

### Actuator
```bash
GET  /actuator/health                    # Overall health
GET  /actuator/health/liveness           # Liveness probe
GET  /actuator/health/readiness          # Readiness probe
GET  /actuator/metrics                   # List all metrics
GET  /actuator/metrics/{metric.name}     # Specific metric
GET  /actuator/prometheus                # Prometheus format
GET  /actuator/circuitbreakers           # Circuit breaker state
GET  /actuator/circuitbreakerevents      # Circuit breaker events
```

### DLQ Management
```bash
GET  /api/v1/admin/dlq                   # List DLQ messages
POST /api/v1/admin/dlq/reprocess         # Reprocess DLQ messages
```

### Fallback Events
```bash
POST /api/v1/admin/fallback/replay       # Replay fallback events
```

## Troubleshooting

### Metrics not appearing in Prometheus
```bash
# Check Prometheus is scraping
curl http://localhost:9090/api/v1/targets | jq

# Verify app exposes metrics
curl http://localhost:8080/actuator/prometheus | head -20

# Restart Prometheus
docker restart $(docker ps -q -f name=prometheus)
```

### Traces not appearing in Zipkin
```bash
# Verify Zipkin is running
curl http://localhost:9411/api/v2/services

# Check trace IDs in logs
./gradlew bootRun | grep "traceId"

# Restart Zipkin
docker restart $(docker ps -q -f name=zipkin)
```

### Circuit breaker not opening
```bash
# Send enough requests to trigger threshold (10+ with 50% failure)
for i in {1..20}; do
  curl -X POST http://localhost:8080/api/v1/telemetry \
    -H "Content-Type: application/json" \
    -d '{"deviceId": 1, "measurement": 25, "date": "2025-01-31T10:00:00Z"}'
done

# Check state
curl http://localhost:8080/actuator/circuitbreakers | jq
```

### Grafana dashboard not loading
```bash
# Check datasource
curl -u admin:admin http://localhost:3000/api/datasources | jq

# Restart Grafana
docker restart $(docker ps -q -f name=grafana)
```

## Performance Impact

| Feature | CPU Impact | Memory Impact | Network Impact |
|---------|------------|---------------|----------------|
| Metrics | <1% | ~10MB | Minimal (15s scrape) |
| Tracing | 2-5% | ~20MB | Async to Zipkin |
| Circuit Breaker | <1ms latency | Minimal | None |
| DLQ | None (only on failures) | None | None |

**Optimization for production:**
- Reduce tracing sampling to 10-20%
- Adjust Prometheus scrape interval
- Configure metric retention policies

## Additional Resources

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/docs)
- [Prometheus](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana](https://grafana.com/docs/grafana/latest/dashboards/)
- [Zipkin](https://zipkin.io/pages/quickstart.html)
- [Resilience4j](https://resilience4j.readme.io/)
- [Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)

---

**For base system documentation, see [README.md](README.md)**
