#!/bin/bash

echo "🛡️  Circuit Breaker Demo"
echo "======================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Step 1: Checking initial circuit state...${NC}"
echo "GET http://localhost:8080/actuator/circuitbreakers"
INITIAL_STATE=$(curl -s http://localhost:8080/actuator/circuitbreakers)
echo "$INITIAL_STATE" | jq '.'
echo ""

# Extract circuit breaker state
CB_STATE=$(echo "$INITIAL_STATE" | jq -r '.circuitBreakers.kafka.state // "UNKNOWN"')
echo -e "Current circuit breaker state: ${GREEN}$CB_STATE${NC}"
echo ""
sleep 2

echo -e "${YELLOW}Step 2: Stopping Kafka container...${NC}"
KAFKA_CONTAINER=$(docker ps -q -f name=kafka)
if [ -z "$KAFKA_CONTAINER" ]; then
    echo -e "${RED}Error: Kafka container not found. Is docker-compose running?${NC}"
    exit 1
fi
docker stop $KAFKA_CONTAINER
echo -e "${GREEN}Kafka stopped${NC}"
echo ""
sleep 2

echo -e "${YELLOW}Step 3: Sending requests to trigger circuit opening...${NC}"
echo "Sending 15 requests (circuit will open after 50% failure rate in 10 requests)..."
echo "Note: Each request may take up to 5 seconds to timeout..."
for i in {1..15}; do
    echo -n "Request $i: "
    RESPONSE=$(curl -s --max-time 7 -w "\n%{http_code}" -X POST http://localhost:8080/api/v1/telemetry \
      -H "Content-Type: application/json" \
      -d "{\"deviceId\": 1, \"measurement\": 25.5, \"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}" 2>&1)
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo -e "${GREEN}Success${NC}"
    else
        echo -e "${RED}Failed (circuit breaker or timeout)${NC}"
    fi
done
echo ""
echo -e "${GREEN}Requests completed${NC}"
echo ""
sleep 2

echo -e "${YELLOW}Step 4: Checking circuit state (should be OPEN)...${NC}"
echo "GET http://localhost:8080/actuator/circuitbreakers"
OPEN_STATE=$(curl -s http://localhost:8080/actuator/circuitbreakers)
echo "$OPEN_STATE" | jq '.'
echo ""

CB_STATE=$(echo "$OPEN_STATE" | jq -r '.circuitBreakers.kafka.state // "UNKNOWN"')
if [ "$CB_STATE" = "OPEN" ]; then
    echo -e "Circuit breaker state: ${RED}$CB_STATE${NC} ✓"
else
    echo -e "Circuit breaker state: ${YELLOW}$CB_STATE${NC} (expected OPEN)"
fi
echo ""
sleep 2

echo -e "${YELLOW}Step 5: Restarting Kafka container...${NC}"
docker start $KAFKA_CONTAINER
echo -e "${GREEN}Kafka restarted${NC}"
echo ""
sleep 5

echo -e "${YELLOW}Step 6: Waiting for circuit to recover...${NC}"
echo "Circuit breaker will transition to HALF_OPEN after 10 seconds..."
echo "Waiting 15 seconds for automatic transition and recovery..."
for i in {15..1}; do
    echo -n "$i "
    sleep 1
done
echo ""
echo ""

# Send a few successful requests to help close the circuit
echo "Sending test requests to help circuit recovery..."
for i in {1..5}; do
    curl -s --max-time 3 -X POST http://localhost:8080/api/v1/telemetry \
      -H "Content-Type: application/json" \
      -d "{\"deviceId\": 1, \"measurement\": 25.5, \"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}" > /dev/null
    echo -n "."
    sleep 1
done
echo ""
echo ""

echo -e "${YELLOW}Step 7: Checking circuit state (should be CLOSED)...${NC}"
echo "GET http://localhost:8080/actuator/circuitbreakers"
CLOSED_STATE=$(curl -s http://localhost:8080/actuator/circuitbreakers)
echo "$CLOSED_STATE" | jq '.'
echo ""

CB_STATE=$(echo "$CLOSED_STATE" | jq -r '.circuitBreakers.kafka.state // "UNKNOWN"')
if [ "$CB_STATE" = "CLOSED" ]; then
    echo -e "Circuit breaker state: ${GREEN}$CB_STATE${NC} ✓"
else
    echo -e "Circuit breaker state: ${YELLOW}$CB_STATE${NC} (expected CLOSED, may need more time)"
fi
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Circuit Breaker Demo Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Summary:"
echo "  - Circuit breaker protects against Kafka failures"
echo "  - Opens after 50% failure rate in sliding window"
echo "  - Fails fast when open (no thread blocking)"
echo "  - Automatically transitions to HALF_OPEN after 10s"
echo "  - Closes after successful requests in HALF_OPEN state"
echo ""
echo "Check fallback events that were stored during circuit open:"
echo "  SELECT * FROM fallback_events;"
echo ""
echo "Replay fallback events:"
echo "  curl -X POST http://localhost:8080/api/v1/admin/fallback/replay"
echo ""
