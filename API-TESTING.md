# API Testing Guide

This guide explains how to test the Telemetry API using the provided HTTP request files.

## Available Files

1. **`api-requests.http`** - HTTP file for IntelliJ IDEA and VS Code REST Client
2. **`Telemetry-API.postman_collection.json`** - Postman collection (importable)

## Option 1: Using IntelliJ IDEA (Recommended)

IntelliJ IDEA has built-in support for `.http` files.

### Steps:

1. Open the project in IntelliJ IDEA
2. Open the file `api-requests.http`
3. You'll see green "play" buttons (▶) next to each request
4. Click any play button to execute the request
5. Results appear in a panel at the bottom

### Features:

- ✅ Syntax highlighting
- ✅ Variable substitution (`{{baseUrl}}`)
- ✅ Response formatting (JSON, XML, etc.)
- ✅ Response history
- ✅ No plugin required

### Tips:

- Use `Ctrl+Enter` (Windows/Linux) or `Cmd+Enter` (Mac) to run the request under cursor
- Use `###` to separate requests
- Variables are defined at the top: `@baseUrl = http://localhost:8080`

## Option 2: Using VS Code with REST Client Extension

### Installation:

1. Install the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension
2. Open `api-requests.http` in VS Code
3. Click "Send Request" link above each request

### Features:

- ✅ Similar to IntelliJ IDEA
- ✅ Variable support
- ✅ Response preview
- ✅ Request history

## Option 3: Using Postman

### Import Collection:

1. Open Postman
2. Click "Import" button
3. Select `Telemetry-API.postman_collection.json`
4. The collection will appear in your sidebar

### Features:

- ✅ Organized folders
- ✅ Pre-configured requests
- ✅ Environment variables
- ✅ Test scripts (can be added)
- ✅ Collection runner for batch testing

### Configure Environment:

1. Create a new environment in Postman
2. Add variable: `baseUrl` = `http://localhost:8080`
3. Select the environment before running requests

## Request Categories

### 1. Telemetry Endpoints
- **POST /api/v1/telemetry** - Submit temperature measurements
- **GET /api/v1/devices** - Get all devices with latest temperature

### 2. Edge Cases Testing
- Duplicate telemetry (idempotency)
- Out-of-order events
- Invalid requests (missing fields, future dates)

### 3. Health & Metrics
- Health checks (overall, liveness, readiness)
- Custom metrics (telemetry received, duplicates, out-of-order)
- JVM metrics (memory, GC, threads)
- HTTP metrics (latency, error rate)
- Prometheus format metrics

### 4. Circuit Breaker
- Circuit breaker state (CLOSED, OPEN, HALF_OPEN)
- Circuit breaker events
- Circuit breaker metrics

### 5. Admin - DLQ Management
- List messages in Dead Letter Queue
- Reprocess failed messages

### 6. Admin - Fallback Events
- Replay events stored when circuit breaker was open

### 7. Challenge Example Sequence
- Exact sequence from the challenge document
- Run all 5 POST requests, then GET to verify result

## Quick Start Testing

### 1. Start the Application

```bash
# Start infrastructure
./start.sh

# Start application
./gradlew bootRun
```

### 2. Run Basic Tests

Execute these requests in order:

1. **Record Telemetry - Device 1** → Should return `202 Accepted`
2. **Record Telemetry - Device 2** → Should return `202 Accepted`
3. **Get All Devices** → Should return array with 2 devices

### 3. Test Edge Cases

**Idempotency Test:**
1. Run "Test Duplicate Telemetry" twice
2. Both should succeed (200 or 202)
3. Check "Get All Devices" - should show only one entry for device 99

**Out-of-Order Test:**
1. Run "Test Out-of-Order - Newer First"
2. Run "Test Out-of-Order - Older Second"
3. Check "Get All Devices" - device 100 should show 30.0 (newer measurement)

**Validation Test:**
1. Run any "Invalid" request
2. Should return `400 Bad Request` with error message

### 4. Test Observability

**Metrics:**
1. Generate some load (run telemetry requests multiple times)
2. Check "Metric - Telemetry Received" → Should show count > 0
3. Check "Metric - Processing Time" → Should show percentiles

**Health:**
1. Run "Health Check - Overall" → Should return `UP`
2. Run "Health Check - Liveness" → Should return `UP`
3. Run "Health Check - Readiness" → Should return `UP`

### 5. Test Circuit Breaker

**Manual Test:**
1. Check "Circuit Breaker State" → Should be `CLOSED`
2. Stop Kafka: `docker stop $(docker ps -q -f name=kafka)`
3. Send 20 telemetry requests (circuit will open)
4. Check "Circuit Breaker State" → Should be `OPEN`
5. Restart Kafka: `docker start $(docker ps -aq -f name=kafka)`
6. Wait 15 seconds
7. Check "Circuit Breaker State" → Should be `CLOSED`

**Or use the demo scripts:**
```bash
./demo-circuit-breaker.sh         # Detailed version (~3 min)
./demo-circuit-breaker-quick.sh   # Fast version (~2 min)
```

### 6. Test Dead Letter Queue

**Manual Test:**
1. Modify consumer to throw exception (or use invalid data)
2. Send telemetry request
3. Wait 10 seconds (for retries)
4. Run "List DLQ Messages" → Should show failed message
5. Run "Reprocess DLQ Messages" → Should return `202 Accepted`
6. Run "List DLQ Messages" again → Should be empty

**Or use the demo script:**
```bash
./demo-dlq.sh
```

## Challenge Verification

To verify the exact challenge requirements, run the "Challenge Example Sequence" folder in order:

1. Device 1 - 10°C at 13:00:00
2. Device 2 - 8°C at 13:00:01
3. Device 1 - 12°C at 13:00:05
4. Device 2 - 19°C at 13:00:06
5. Device 2 - 10°C at 13:00:11
6. Get Devices

**Expected Result:**
```json
[
  {
    "deviceId": 1,
    "measurement": 12,
    "date": "2025-01-31T13:00:05Z"
  },
  {
    "deviceId": 2,
    "measurement": 10,
    "date": "2025-01-31T13:00:11Z"
  }
]
```

## Load Testing

### Using HTTP File

Run the "Generate Load" requests multiple times:
- IntelliJ: Click play button repeatedly
- VS Code: Click "Send Request" repeatedly

### Using Postman Collection Runner

1. Select the "Telemetry Endpoints" folder
2. Click "Run" button
3. Set iterations (e.g., 100)
4. Click "Run Telemetry Endpoints"

### Using Demo Script

```bash
./demo-observability.sh
```

This generates 100 requests and shows metrics.

## Monitoring While Testing

### Grafana Dashboard
- URL: http://localhost:3000
- Username: `admin`
- Password: `admin`
- Dashboard: "Telemetry System Overview"

**What to watch:**
- Telemetry ingestion rate
- Duplicate detection rate
- HTTP request latency
- Circuit breaker state

### Prometheus
- URL: http://localhost:9090
- Query examples:
  - `rate(telemetry_received_total[1m])` - Ingestion rate
  - `telemetry_duplicates_total` - Total duplicates
  - `resilience4j_circuitbreaker_state` - Circuit breaker state

### Zipkin
- URL: http://localhost:9411
- Click "Run Query" to see traces
- Filter by service: `telemetry-service`
- Click on a trace to see span hierarchy

## Troubleshooting

### Connection Refused

**Problem:** Cannot connect to http://localhost:8080

**Solution:**
```bash
# Check if application is running
curl http://localhost:8080/actuator/health

# If not, start it
./gradlew bootRun
```

### 503 Service Unavailable

**Problem:** Requests return 503

**Solution:**
```bash
# Check if infrastructure is running
docker ps

# If not, start it
./start.sh

# Wait for services to be ready (30 seconds)
sleep 30
```

### Empty Response from GET /devices

**Problem:** GET /devices returns empty array

**Solution:**
- Send some POST requests first
- Wait a few seconds for async processing
- Check Kafka consumer is running (look for logs)

### Metrics Show Zero

**Problem:** Metrics endpoints return 0 for all counters

**Solution:**
- Send some requests first
- Metrics are incremented on each request
- Check application logs for errors

## Tips and Best Practices

### IntelliJ IDEA Tips

1. **Run All Requests in Sequence:**
   - Select multiple requests (Shift+Click)
   - Right-click → "Run All Requests in File"

2. **Save Responses:**
   - Responses are automatically saved in `.idea/httpRequests/`
   - View history: Tools → HTTP Client → Show HTTP Requests History

3. **Environment Variables:**
   - Create `http-client.env.json` for different environments
   - Switch between dev, staging, prod

### Postman Tips

1. **Use Collection Runner:**
   - Run entire folders at once
   - Set delays between requests
   - Export results

2. **Add Tests:**
   - Add test scripts to verify responses
   - Example: `pm.test("Status is 202", () => pm.response.to.have.status(202));`

3. **Use Pre-request Scripts:**
   - Generate dynamic data
   - Set timestamps automatically

### General Tips

1. **Wait for Async Processing:**
   - After POST, wait 1-2 seconds before GET
   - CQRS introduces eventual consistency

2. **Check Logs:**
   - Application logs show trace IDs
   - Use trace ID to find request in Zipkin

3. **Monitor Metrics:**
   - Keep Grafana open while testing
   - Watch metrics update in real-time

4. **Use Demo Scripts:**
   - Scripts automate complex scenarios
   - Good for presentations and demos

## Additional Resources

- [Main README](README.md) - System overview and architecture
- [Production Features](README-PRODUCTION.md) - Observability, tracing, circuit breaker, DLQ
- [Observability Documentation](docs/observability.md) - Metrics and monitoring
- [Distributed Tracing Documentation](docs/distributed-tracing.md) - Tracing details
- [Circuit Breaker Documentation](docs/circuit-breaker.md) - Resilience patterns
- [Dead Letter Queue Documentation](docs/dead-letter-queue.md) - Error recovery

---

**Happy Testing! 🚀**
