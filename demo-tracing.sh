#!/bin/bash

echo "🔍 Distributed Tracing Demo"
echo "=========================="
echo ""

# Check if the application is running
echo "Checking if application is running..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "❌ Error: Application is not running on port 8080"
    echo "Please start the application first with: ./start.sh"
    exit 1
fi

echo "✅ Application is running"
echo ""

# Check if Zipkin is running
echo "Checking if Zipkin is running..."
if ! curl -s http://localhost:9411/api/v2/services > /dev/null 2>&1; then
    echo "❌ Error: Zipkin is not running on port 9411"
    echo "Please ensure Zipkin is started with docker-compose"
    exit 1
fi

echo "✅ Zipkin is running"
echo ""

# Send sample telemetry requests with tracing
echo "1. Sending sample telemetry requests with distributed tracing..."
echo "   This will generate traces that flow through the entire system:"
echo "   HTTP → Command Handler → Repository → Kafka → Consumer → Projection"
echo ""

DEVICE_ID=42
MEASUREMENT=23.5
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo "   Sending request for Device $DEVICE_ID with measurement $MEASUREMENT°C..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8080/api/v1/telemetry \
    -H "Content-Type: application/json" \
    -d "{\"deviceId\": $DEVICE_ID, \"measurement\": $MEASUREMENT, \"date\": \"$TIMESTAMP\"}" 2>/dev/null)

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ Request successful (HTTP $HTTP_CODE)"
else
    echo "   ❌ Request failed (HTTP $HTTP_CODE)"
    echo "   Response: $BODY"
fi
echo ""

# Send a few more requests to generate more traces
echo "   Sending additional requests to generate more trace data..."
for i in {1..5}; do
    DEVICE_ID=$((RANDOM % 10 + 1))
    MEASUREMENT=$((RANDOM % 50 + 10))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    curl -s -X POST http://localhost:8080/api/v1/telemetry \
        -H "Content-Type: application/json" \
        -d "{\"deviceId\": $DEVICE_ID, \"measurement\": $MEASUREMENT, \"date\": \"$TIMESTAMP\"}" > /dev/null 2>&1
    
    echo "   Request $i/5 sent (Device $DEVICE_ID)..."
done

echo ""
echo "✅ Sample requests sent successfully!"
echo ""

# Wait for traces to be collected
echo "Waiting for traces to be collected by Zipkin..."
sleep 3
echo ""

# Display trace information
echo "2. 🔎 Viewing Trace Information"
echo "==============================="
echo ""

# Get service names from Zipkin
echo "Services registered in Zipkin:"
if command -v jq &> /dev/null; then
    SERVICES=$(curl -s http://localhost:9411/api/v2/services | jq -r '.[]')
    echo "$SERVICES" | while read -r service; do
        echo "  - $service"
    done
else
    curl -s http://localhost:9411/api/v2/services
fi
echo ""

# Get recent traces
echo "Recent traces (last 10):"
if command -v jq &> /dev/null; then
    TRACES=$(curl -s "http://localhost:9411/api/v2/traces?limit=10" | jq -r '.[] | .[0] | .traceId')
    echo "$TRACES" | head -5 | while read -r trace; do
        echo "  - Trace ID: $trace"
    done
else
    curl -s "http://localhost:9411/api/v2/traces?limit=10"
fi
echo ""

# Instructions for viewing traces in Zipkin UI
echo "3. 🌐 View Traces in Zipkin UI"
echo "=============================="
echo ""
echo "Zipkin UI is available at: http://localhost:9411"
echo ""
echo "To view and analyze traces:"
echo "  1. Open http://localhost:9411 in your browser"
echo "  2. Click the 'Run Query' button to see recent traces"
echo "  3. Click on any trace to see detailed span information"
echo ""
echo "What you'll see in each trace:"
echo "  📍 HTTP POST /api/v1/telemetry - Initial HTTP request"
echo "  📍 Command Handler - RecordTelemetryCommandHandler processing"
echo "  📍 Database Insert - Saving telemetry to PostgreSQL"
echo "  📍 Kafka Publish - Publishing event to Kafka topic"
echo "  📍 Kafka Consume - Consumer receiving the event"
echo "  📍 Database Update - Updating device projection"
echo ""

echo "4. 🔍 How to Search for Specific Traces"
echo "========================================"
echo ""
echo "In the Zipkin UI, you can filter traces by:"
echo ""
echo "  Service Name:"
echo "    - Select 'telemetry-service' from the dropdown"
echo ""
echo "  Span Name:"
echo "    - Filter by specific operations like:"
echo "      • 'POST /api/v1/telemetry' - HTTP requests"
echo "      • 'RecordTelemetryCommandHandler.handle' - Command processing"
echo "      • 'TelemetryEventConsumer.consume' - Event consumption"
echo ""
echo "  Tags:"
echo "    - Search by custom tags like:"
echo "      • http.method=POST"
echo "      • http.status_code=201"
echo "      • error=true (for failed requests)"
echo ""
echo "  Time Range:"
echo "    - Adjust the lookback period (default: 15 minutes)"
echo ""
echo "  Min/Max Duration:"
echo "    - Find slow requests by setting minimum duration"
echo ""

echo "5. 📊 Understanding Trace Details"
echo "=================================="
echo ""
echo "When viewing a trace, you'll see:"
echo ""
echo "  Timeline View:"
echo "    - Visual representation of spans and their duration"
echo "    - Parent-child relationships between spans"
echo "    - Total request duration"
echo ""
echo "  Span Details:"
echo "    - Service name and operation"
echo "    - Start time and duration"
echo "    - Tags (metadata like HTTP method, status code)"
echo "    - Logs (events that occurred during the span)"
echo ""
echo "  Trace Context Propagation:"
echo "    - Trace ID: Unique identifier for the entire request"
echo "    - Span ID: Unique identifier for each operation"
echo "    - Parent Span ID: Links child spans to their parent"
echo ""

echo "6. 🐛 Debugging with Traces"
echo "==========================="
echo ""
echo "Use traces to debug issues:"
echo ""
echo "  Find slow requests:"
echo "    - Sort traces by duration"
echo "    - Identify which span is taking the most time"
echo ""
echo "  Find errors:"
echo "    - Filter by 'error=true' tag"
echo "    - View error details in span annotations"
echo ""
echo "  Trace request flow:"
echo "    - Follow a request from HTTP → Kafka → Database"
echo "    - Verify all expected spans are present"
echo ""
echo "  Correlate with logs:"
echo "    - Copy the Trace ID from Zipkin"
echo "    - Search application logs for the same Trace ID"
echo "    - Example: grep '<trace-id>' logs/application.log"
echo ""

echo "7. 📋 Useful Zipkin API Endpoints"
echo "=================================="
echo ""
echo "Query traces programmatically:"
echo ""
echo "  Get all services:"
echo "    curl http://localhost:9411/api/v2/services | jq"
echo ""
echo "  Get recent traces:"
echo "    curl 'http://localhost:9411/api/v2/traces?limit=10' | jq"
echo ""
echo "  Get traces for a specific service:"
echo "    curl 'http://localhost:9411/api/v2/traces?serviceName=telemetry-service' | jq"
echo ""
echo "  Get a specific trace by ID:"
echo "    curl 'http://localhost:9411/api/v2/trace/<trace-id>' | jq"
echo ""
echo "  Get span names for a service:"
echo "    curl 'http://localhost:9411/api/v2/spans?serviceName=telemetry-service' | jq"
echo ""

echo "✅ Distributed Tracing demo complete!"
echo ""
echo "Next steps:"
echo "  - Open http://localhost:9411 to explore traces visually"
echo "  - Send more requests to generate additional trace data"
echo "  - Try the circuit breaker demo: ./demo-circuit-breaker.sh"
echo "  - Try the DLQ demo: ./demo-dlq.sh"
echo ""
