# Observability Features

This document provides detailed information about the observability features in the telemetry system, including metrics collection, Prometheus integration, and Grafana dashboards.

## Table of Contents

- [Overview](#overview)
- [Metrics](#metrics)
- [Prometheus Integration](#prometheus-integration)
- [Grafana Dashboards](#grafana-dashboards)
- [Querying Metrics](#querying-metrics)
- [Alerting](#alerting)
- [Best Practices](#best-practices)

## Overview

The telemetry system uses Spring Boot Actuator and Micrometer to expose comprehensive metrics about application behavior, performance, and health. These metrics are scraped by Prometheus and visualized in Grafana dashboards.

**Architecture:**
```
Application (Micrometer)
    ↓ exposes metrics
Actuator Endpoints (:8080/actuator/prometheus)
    ↓ scraped every 15s
Prometheus (:9090)
    ↓ queries
Grafana (:3000)
    ↓ displays
Dashboards & Alerts
```

## Metrics

### Custom Application Metrics

The system exposes custom metrics specific to telemetry processing:

#### telemetry.received.total

**Type:** Counter  
**Description:** Total number of telemetry messages received via HTTP POST  
**Tags:** 
- `application`: telemetry-service
- `type`: temperature

**Usage:**
```bash
curl http://localhost:8080/actuator/metrics/telemetry.received.total | jq
```

**Example Response:**
```json
{
  "name": "telemetry.received.total",
  "description": "Total telemetry messages received",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1523.0
    }
  ],
  "availableTags": [
    {
      "tag": "application",
      "values": ["telemetry-service"]
    }
  ]
}
```

#### telemetry.duplicates.total

**Type:** Counter  
**Description:** Total number of duplicate telemetry messages detected  
**Tags:** 
- `application`: telemetry-service

**Interpretation:**
- High duplicate rate may indicate client retry logic issues
- Should be monitored relative to total received messages
- Typical rate: <5% of total messages

#### telemetry.out_of_order.total

**Type:** Counter  
**Description:** Total number of out-of-order events detected (older timestamp than current latest)  
**Tags:** 
- `application`: telemetry-service

**Interpretation:**
- Indicates messages arriving with older timestamps
- These are stored but don't update the projection
- High rate may indicate network delays or clock skew

#### telemetry.processing.time

**Type:** Timer  
**Description:** Time taken to process telemetry messages  
**Percentiles:** P50, P95, P99  
**Tags:** 
- `application`: telemetry-service
- `operation`: command-handler, consumer, etc.

**Usage:**
```bash
curl http://localhost:8080/actuator/metrics/telemetry.processing.time | jq
```

**Example Response:**
```json
{
  "name": "telemetry.processing.time",
  "description": "Time to process telemetry",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1000.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 45.5
    },
    {
      "statistic": "MAX",
      "value": 0.250
    }
  ],
  "availableTags": []
}
```

**Percentiles:**
- P50 (median): Typical processing time
- P95: 95% of requests complete within this time
- P99: 99% of requests complete within this time

#### telemetry.dlq.messages.total

**Type:** Counter  
**Description:** Total number of messages sent to Dead Letter Queue  
**Tags:** 
- `application`: telemetry-service

**Interpretation:**
- Should be near zero in healthy system
- Spikes indicate processing failures
- Requires investigation when non-zero

#### telemetry.dlq.reprocessed.total

**Type:** Counter  
**Description:** Total number of messages successfully reprocessed from DLQ  
**Tags:** 
- `application`: telemetry-service

### Standard Spring Boot Metrics

#### JVM Metrics

**Memory:**
- `jvm.memory.used` - Current memory usage (heap and non-heap)
- `jvm.memory.max` - Maximum memory available
- `jvm.memory.committed` - Memory committed by JVM

**Garbage Collection:**
- `jvm.gc.pause` - GC pause duration
- `jvm.gc.memory.allocated` - Memory allocated during GC
- `jvm.gc.memory.promoted` - Memory promoted to old generation

**Threads:**
- `jvm.threads.live` - Current live threads
- `jvm.threads.daemon` - Current daemon threads
- `jvm.threads.peak` - Peak thread count

**Example Query:**
```bash
# View heap memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap | jq

# View GC pause time
curl http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
```

#### HTTP Metrics

**Request Metrics:**
- `http.server.requests` - HTTP request count and duration
- Tags: `method`, `status`, `uri`, `exception`

**Example Query:**
```bash
# View HTTP request metrics
curl 'http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/v1/telemetry' | jq
```

**Percentiles:**
- Automatically calculated: P50, P95, P99
- Available in Prometheus format

#### Database Metrics

**Connection Pool:**
- `hikaricp.connections.active` - Active database connections
- `hikaricp.connections.idle` - Idle connections
- `hikaricp.connections.pending` - Pending connection requests
- `hikaricp.connections.max` - Maximum pool size
- `hikaricp.connections.min` - Minimum pool size

**Example Query:**
```bash
# View connection pool status
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq
```

#### Kafka Metrics

**Producer:**
- `kafka.producer.record.send.total` - Total records sent
- `kafka.producer.record.error.total` - Failed sends
- `kafka.producer.request.latency.avg` - Average send latency

**Consumer:**
- `kafka.consumer.records.consumed.total` - Total records consumed
- `kafka.consumer.fetch.manager.records.lag` - Consumer lag
- `kafka.consumer.coordinator.commit.latency.avg` - Commit latency

#### Circuit Breaker Metrics

**State:**
- `resilience4j.circuitbreaker.state` - Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `resilience4j.circuitbreaker.failure.rate` - Current failure rate
- `resilience4j.circuitbreaker.slow.call.rate` - Slow call rate

**Calls:**
- `resilience4j.circuitbreaker.calls` - Total calls (successful, failed, not permitted)
- Tags: `kind` (successful, failed, not_permitted)

**Example Query:**
```bash
# View circuit breaker state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:kafka | jq
```

## Prometheus Integration

### Configuration

Prometheus is configured to scrape metrics from the application every 15 seconds.

**Prometheus Configuration** (`monitoring/prometheus.yml`):
```yaml
global:
  scrape_interval: 15s
  scrape_timeout: 10s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'telemetry-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'telemetry-service'
```

### Accessing Prometheus

**URL:** http://localhost:9090

**Useful Pages:**
- **Graph:** Query and visualize metrics
- **Targets:** View scrape status
- **Alerts:** View active alerts
- **Status → Configuration:** View Prometheus config
- **Status → Targets:** Check if application is being scraped

### Verifying Scraping

```bash
# Check if Prometheus is scraping the application
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job == "telemetry-service")'

# Expected output:
# {
#   "discoveredLabels": {...},
#   "labels": {
#     "application": "telemetry-service",
#     "instance": "host.docker.internal:8080",
#     "job": "telemetry-service"
#   },
#   "scrapePool": "telemetry-service",
#   "scrapeUrl": "http://host.docker.internal:8080/actuator/prometheus",
#   "health": "up",
#   "lastError": "",
#   "lastScrape": "2025-01-31T10:00:00.123Z",
#   "lastScrapeDuration": 0.045,
#   "scrapeInterval": "15s",
#   "scrapeTimeout": "10s"
# }
```

### Prometheus Query Language (PromQL)

#### Basic Queries

```promql
# Current value of a counter
telemetry_received_total

# Rate of change (per second)
rate(telemetry_received_total[1m])

# Rate over 5 minutes
rate(telemetry_received_total[5m])

# Sum across all instances
sum(rate(telemetry_received_total[1m]))
```

#### Advanced Queries

```promql
# Duplicate rate as percentage of total
(rate(telemetry_duplicates_total[1m]) / rate(telemetry_received_total[1m])) * 100

# HTTP request rate by status code
sum by (status) (rate(http_server_requests_seconds_count[1m]))

# 95th percentile latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# 99th percentile latency
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))

# Circuit breaker failure rate
resilience4j_circuitbreaker_failure_rate{name="kafka"}

# JVM heap usage percentage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# Kafka consumer lag
kafka_consumer_fetch_manager_records_lag_max

# Active database connections
hikaricp_connections_active
```

#### Aggregation Functions

```promql
# Sum
sum(rate(telemetry_received_total[1m]))

# Average
avg(rate(http_server_requests_seconds_sum[1m]))

# Maximum
max(jvm_memory_used_bytes{area="heap"})

# Minimum
min(hikaricp_connections_idle)

# Count
count(up{job="telemetry-service"})
```

## Grafana Dashboards

### Accessing Grafana

**URL:** http://localhost:3000  
**Username:** admin  
**Password:** admin

### Pre-configured Dashboard

The system includes a pre-configured dashboard: **"Telemetry System Overview"**

**Location:** Dashboards → Browse → Telemetry System Overview

### Dashboard Panels

#### 1. Telemetry Ingestion Rate

**Query:**
```promql
rate(telemetry_received_total[1m])
```

**Visualization:** Time series graph  
**Description:** Shows messages received per second over time  
**Y-axis:** Messages/second  
**Interpretation:**
- Steady rate indicates consistent load
- Spikes indicate burst traffic
- Drops may indicate client issues

#### 2. Duplicate Detection Rate

**Query:**
```promql
rate(telemetry_duplicates_total[1m])
```

**Visualization:** Time series graph  
**Description:** Shows duplicate messages detected per second  
**Y-axis:** Duplicates/second  
**Interpretation:**
- Should be low relative to ingestion rate
- High rate indicates client retry issues
- Sudden spikes warrant investigation

#### 3. Out-of-Order Events

**Query:**
```promql
rate(telemetry_out_of_order_total[1m])
```

**Visualization:** Time series graph  
**Description:** Shows messages arriving with older timestamps  
**Y-axis:** Events/second  
**Interpretation:**
- Indicates network delays or clock skew
- High rate may affect projection accuracy
- Monitor for patterns (time of day, specific devices)

#### 4. HTTP Request Latency

**Queries:**
```promql
# P50 (median)
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket{uri="/api/v1/telemetry"}[1m]))

# P95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/telemetry"}[1m]))

# P99
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{uri="/api/v1/telemetry"}[1m]))
```

**Visualization:** Time series graph with multiple series  
**Description:** Shows request latency percentiles over time  
**Y-axis:** Seconds  
**Interpretation:**
- P50 shows typical performance
- P95/P99 show tail latency
- Increasing percentiles indicate performance degradation

#### 5. Circuit Breaker State

**Query:**
```promql
resilience4j_circuitbreaker_state{name="kafka"}
```

**Visualization:** Stat panel or time series  
**Description:** Shows current circuit breaker state  
**Values:**
- 0 = CLOSED (normal)
- 1 = OPEN (failing fast)
- 2 = HALF_OPEN (testing recovery)

**Interpretation:**
- Should be 0 (CLOSED) in healthy system
- 1 (OPEN) indicates Kafka issues
- 2 (HALF_OPEN) indicates recovery in progress

#### 6. JVM Memory Usage

**Query:**
```promql
jvm_memory_used_bytes{area="heap"}
```

**Visualization:** Time series graph  
**Description:** Shows heap memory usage over time  
**Y-axis:** Bytes  
**Interpretation:**
- Sawtooth pattern is normal (GC cycles)
- Steadily increasing indicates memory leak
- Flat at max indicates memory pressure

#### 7. Kafka Consumer Lag

**Query:**
```promql
kafka_consumer_fetch_manager_records_lag_max
```

**Visualization:** Time series graph  
**Description:** Shows consumer lag (messages behind)  
**Y-axis:** Messages  
**Interpretation:**
- Should be near zero
- Increasing lag indicates consumer can't keep up
- Sustained lag requires scaling

#### 8. DLQ Messages

**Query:**
```promql
rate(telemetry_dlq_messages_total[1m])
```

**Visualization:** Time series graph  
**Description:** Shows messages sent to DLQ per second  
**Y-axis:** Messages/second  
**Interpretation:**
- Should be zero in healthy system
- Non-zero indicates processing failures
- Requires immediate investigation

### Creating Custom Dashboards

#### Step 1: Create Dashboard

1. Click "+" in left sidebar
2. Select "Dashboard"
3. Click "Add visualization"
4. Select "Prometheus" datasource

#### Step 2: Configure Panel

1. Enter PromQL query
2. Select visualization type (Time series, Gauge, Stat, etc.)
3. Configure panel options:
   - Title
   - Description
   - Y-axis label
   - Unit (seconds, bytes, percent, etc.)
   - Thresholds
   - Colors

#### Step 3: Save Dashboard

1. Click "Save" icon (top right)
2. Enter dashboard name
3. Select folder
4. Click "Save"

### Dashboard Best Practices

**Layout:**
- Most important metrics at top
- Related metrics grouped together
- Use rows to organize panels
- Consistent time range across panels

**Queries:**
- Use rate() for counters
- Use histogram_quantile() for percentiles
- Add labels for filtering
- Use aggregation functions appropriately

**Visualization:**
- Time series for trends
- Gauges for current values
- Stats for single numbers
- Tables for detailed breakdowns

**Colors:**
- Green for good/normal
- Yellow for warning
- Red for critical
- Consistent across dashboards

## Querying Metrics

### Via Actuator

```bash
# List all metrics
curl http://localhost:8080/actuator/metrics | jq '.names'

# Get specific metric
curl http://localhost:8080/actuator/metrics/telemetry.received.total | jq

# Get metric with tag filter
curl 'http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/v1/telemetry' | jq

# Get Prometheus format (all metrics)
curl http://localhost:8080/actuator/prometheus
```

### Via Prometheus API

```bash
# Query current value
curl 'http://localhost:9090/api/v1/query?query=telemetry_received_total' | jq

# Query range (last hour)
curl 'http://localhost:9090/api/v1/query_range?query=rate(telemetry_received_total[1m])&start=2025-01-31T09:00:00Z&end=2025-01-31T10:00:00Z&step=15s' | jq

# Query labels
curl 'http://localhost:9090/api/v1/labels' | jq

# Query label values
curl 'http://localhost:9090/api/v1/label/job/values' | jq
```

### Via Grafana API

```bash
# List dashboards
curl -u admin:admin http://localhost:3000/api/search | jq

# Get dashboard by UID
curl -u admin:admin http://localhost:3000/api/dashboards/uid/<dashboard-uid> | jq

# Query datasource
curl -u admin:admin http://localhost:3000/api/datasources/proxy/1/api/v1/query?query=up | jq
```

## Alerting

### Prometheus Alerting Rules

Create alerting rules in Prometheus to notify on critical conditions.

**Example Alert Rules** (`monitoring/alerts.yml`):
```yaml
groups:
  - name: telemetry_alerts
    interval: 30s
    rules:
      - alert: HighDuplicateRate
        expr: (rate(telemetry_duplicates_total[5m]) / rate(telemetry_received_total[5m])) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High duplicate rate detected"
          description: "Duplicate rate is {{ $value | humanizePercentage }} (threshold: 10%)"

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{name="kafka"} == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker is open"
          description: "Kafka circuit breaker has been open for more than 1 minute"

      - alert: HighConsumerLag
        expr: kafka_consumer_fetch_manager_records_lag_max > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High Kafka consumer lag"
          description: "Consumer lag is {{ $value }} messages (threshold: 1000)"

      - alert: DLQMessagesDetected
        expr: rate(telemetry_dlq_messages_total[5m]) > 0
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Messages being sent to DLQ"
          description: "{{ $value }} messages/sec are failing and being sent to DLQ"

      - alert: HighMemoryUsage
        expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High JVM heap usage"
          description: "Heap usage is {{ $value | humanizePercentage }} (threshold: 90%)"

      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/telemetry"}[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High request latency"
          description: "P95 latency is {{ $value }}s (threshold: 0.5s)"
```

### Grafana Alerting

Configure alerts directly in Grafana dashboards:

1. Edit panel
2. Click "Alert" tab
3. Create alert rule
4. Configure conditions
5. Set notification channel
6. Save

**Example Alert:**
- **Condition:** `avg() OF query(A, 5m, now) IS ABOVE 100`
- **Notification:** Email, Slack, PagerDuty
- **Message:** Custom message with variables

## Best Practices

### Metric Naming

- Use dots for hierarchy: `telemetry.received.total`
- Use descriptive names: `processing.time` not `pt`
- Include units in name when ambiguous: `memory.bytes`
- Use consistent naming across metrics

### Metric Types

- **Counter:** Monotonically increasing values (use rate())
- **Gauge:** Current value that can go up or down
- **Timer:** Duration measurements with percentiles
- **Distribution Summary:** Distribution of values

### Cardinality

- Avoid high-cardinality tags (e.g., user IDs, trace IDs)
- Use bounded tag values (status codes, not error messages)
- Limit number of unique tag combinations
- Monitor metric cardinality in Prometheus

### Performance

- Use appropriate scrape intervals (15s is typical)
- Configure metric retention based on needs
- Use recording rules for expensive queries
- Aggregate metrics at collection time when possible

### Monitoring

- Monitor the monitoring system itself
- Set up alerts for Prometheus/Grafana downtime
- Track scrape duration and failures
- Monitor metric cardinality growth

### Documentation

- Document custom metrics in code
- Include metric descriptions
- Maintain dashboard documentation
- Document alert thresholds and rationale

---

**Related Documentation:**
- [README-PRODUCTION.md](../README-PRODUCTION.md) - Production features overview
- [distributed-tracing.md](distributed-tracing.md) - Distributed tracing guide
- [circuit-breaker.md](circuit-breaker.md) - Circuit breaker documentation
- [dead-letter-queue.md](dead-letter-queue.md) - DLQ documentation
