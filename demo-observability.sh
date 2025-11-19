#!/bin/bash

echo "🎯 Observability Demo"
echo "===================="
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

# Generate load with 100 requests
echo "1. Generating load (100 requests with random data)..."
echo "   This will take a few seconds..."
echo ""

SUCCESS_COUNT=0
FAIL_COUNT=0

for i in {1..100}; do
    DEVICE_ID=$((RANDOM % 10 + 1))
    MEASUREMENT=$((RANDOM % 50 + 10))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8080/api/v1/telemetry \
        -H "Content-Type: application/json" \
        -d "{\"deviceId\": $DEVICE_ID, \"measurement\": $MEASUREMENT, \"date\": \"$TIMESTAMP\"}" 2>/dev/null)
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    
    # Show progress every 20 requests
    if [ $((i % 20)) -eq 0 ]; then
        echo "   Progress: $i/100 requests sent..."
    fi
done

echo ""
echo "✅ Load generation complete!"
echo "   Successful requests: $SUCCESS_COUNT"
echo "   Failed requests: $FAIL_COUNT"
echo ""

# Wait a moment for metrics to be collected
sleep 2

# Display key metrics
echo "2. Viewing key metrics from /actuator/metrics..."
echo ""

echo "📊 Telemetry Received Total:"
if command -v jq &> /dev/null; then
    curl -s http://localhost:8080/actuator/metrics/telemetry.received.total | jq '.measurements[0].value'
else
    curl -s http://localhost:8080/actuator/metrics/telemetry.received.total
fi
echo ""

echo "📊 Telemetry Duplicates Total:"
if command -v jq &> /dev/null; then
    curl -s http://localhost:8080/actuator/metrics/telemetry.duplicates.total | jq '.measurements[0].value'
else
    curl -s http://localhost:8080/actuator/metrics/telemetry.duplicates.total
fi
echo ""

echo "📊 Telemetry Out-of-Order Total:"
if command -v jq &> /dev/null; then
    curl -s http://localhost:8080/actuator/metrics/telemetry.out_of_order.total | jq '.measurements[0].value'
else
    curl -s http://localhost:8080/actuator/metrics/telemetry.out_of_order.total
fi
echo ""

echo "📊 HTTP Server Requests:"
if command -v jq &> /dev/null; then
    curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic == "COUNT") | .value'
else
    curl -s http://localhost:8080/actuator/metrics/http.server.requests
fi
echo ""

echo "📊 JVM Memory Used:"
if command -v jq &> /dev/null; then
    curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq '.measurements[0].value'
else
    curl -s http://localhost:8080/actuator/metrics/jvm.memory.used
fi
echo ""

# Instructions for Grafana
echo "3. 📈 View metrics in Grafana Dashboard"
echo "========================================"
echo ""
echo "Grafana is available at: http://localhost:3000"
echo ""
echo "Login credentials:"
echo "  Username: admin"
echo "  Password: admin"
echo ""
echo "To view the Telemetry Overview dashboard:"
echo "  1. Open http://localhost:3000 in your browser"
echo "  2. Login with the credentials above"
echo "  3. Navigate to Dashboards → Telemetry System Overview"
echo "  4. You should see real-time metrics including:"
echo "     - Telemetry Ingestion Rate"
echo "     - Duplicate Detection Rate"
echo "     - Out-of-Order Events"
echo "     - HTTP Request Latency (P50, P95, P99)"
echo "     - Circuit Breaker State"
echo ""

# Additional useful endpoints
echo "4. 📋 Additional Useful Endpoints"
echo "=================================="
echo ""
echo "View all available metrics:"
echo "  curl http://localhost:8080/actuator/metrics | jq '.names'"
echo ""
echo "View Prometheus metrics (raw format):"
echo "  curl http://localhost:8080/actuator/prometheus"
echo ""
echo "View application health:"
echo "  curl http://localhost:8080/actuator/health | jq"
echo ""
echo "View circuit breaker status:"
echo "  curl http://localhost:8080/actuator/circuitbreakers | jq"
echo ""

echo "✅ Observability demo complete!"
