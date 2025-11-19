#!/bin/bash

echo "🔄 Dead Letter Queue (DLQ) Demo"
echo "================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if the application is running
echo "Checking if application is running..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Application is not running on port 8080${NC}"
    echo "Please start the application first with: ./start.sh"
    exit 1
fi

echo -e "${GREEN}✅ Application is running${NC}"
echo ""

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}⚠️  Warning: jq is not installed. Output will be less formatted.${NC}"
    echo "Install jq for better output: brew install jq (macOS) or apt-get install jq (Linux)"
    echo ""
    JQ_AVAILABLE=false
else
    JQ_AVAILABLE=true
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Dead Letter Queue (DLQ) Overview${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "The DLQ captures Kafka messages that fail processing after multiple retry attempts."
echo ""
echo "How it works:"
echo "  1. Consumer receives message from Kafka"
echo "  2. If processing fails, retry with exponential backoff (1s, 2s, 4s)"
echo "  3. After 3 failed attempts, message is sent to DLQ topic"
echo "  4. DLQ messages can be inspected and reprocessed via admin endpoints"
echo ""
sleep 2

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Step 1: Checking current DLQ state${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "GET http://localhost:8080/api/v1/admin/dlq"
if [ "$JQ_AVAILABLE" = true ]; then
    INITIAL_DLQ=$(curl -s http://localhost:8080/api/v1/admin/dlq)
    echo "$INITIAL_DLQ" | jq '.'
    INITIAL_COUNT=$(echo "$INITIAL_DLQ" | jq 'length')
    echo ""
    echo -e "Current DLQ message count: ${YELLOW}$INITIAL_COUNT${NC}"
else
    curl -s http://localhost:8080/api/v1/admin/dlq
    echo ""
fi
echo ""

if [ "$INITIAL_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}⚠️  DLQ already contains messages from previous runs${NC}"
    echo ""
fi

sleep 2

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Step 2: Understanding DLQ Triggers${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "Messages go to DLQ when the consumer fails to process them."
echo "Common failure scenarios:"
echo "  - Database connection errors"
echo "  - Data validation failures in consumer"
echo "  - Unexpected exceptions during processing"
echo "  - Transient infrastructure issues"
echo ""

echo -e "${YELLOW}Note: This demo shows the DLQ endpoints and functionality.${NC}"
echo -e "${YELLOW}To trigger actual DLQ messages, you would need to:${NC}"
echo "  1. Stop PostgreSQL: docker stop \$(docker ps -q -f name=postgres)"
echo "  2. Send telemetry: curl -X POST http://localhost:8080/api/v1/telemetry ..."
echo "  3. Wait for retries (~10 seconds)"
echo "  4. Restart PostgreSQL: docker start \$(docker ps -aq -f name=postgres)"
echo "  5. Check DLQ: curl http://localhost:8080/api/v1/admin/dlq | jq"
echo ""

sleep 2

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Step 3: DLQ Management Endpoints${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "📋 List all DLQ messages:"
echo "   GET /api/v1/admin/dlq"
echo ""
echo "Example:"
echo "   curl http://localhost:8080/api/v1/admin/dlq | jq"
echo ""

if [ "$JQ_AVAILABLE" = true ]; then
    echo "Current DLQ contents:"
    DLQ_MESSAGES=$(curl -s http://localhost:8080/api/v1/admin/dlq)
    echo "$DLQ_MESSAGES" | jq '.'
    
    DLQ_COUNT=$(echo "$DLQ_MESSAGES" | jq 'length')
    
    if [ "$DLQ_COUNT" -gt 0 ]; then
        echo ""
        echo "Sample DLQ message structure:"
        echo "$DLQ_MESSAGES" | jq '.[0] | {
            deviceId: .event.deviceId,
            measurement: .event.measurement,
            errorMessage: .errorMessage,
            retryCount: .retryCount,
            timestamp: .timestamp
        }'
    fi
fi
echo ""
sleep 2

echo "🔄 Reprocess all DLQ messages:"
echo "   POST /api/v1/admin/dlq/reprocess"
echo ""
echo "Example:"
echo "   curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess | jq"
echo ""

if [ "$DLQ_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}Would you like to reprocess the existing DLQ messages? (y/n)${NC}"
    read -t 10 -n 1 -r REPLY
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "Reprocessing DLQ messages..."
        
        if [ "$JQ_AVAILABLE" = true ]; then
            REPROCESS_RESULT=$(curl -s -X POST http://localhost:8080/api/v1/admin/dlq/reprocess)
            echo "$REPROCESS_RESULT" | jq '.'
            
            REPROCESSED_COUNT=$(echo "$REPROCESS_RESULT" | jq -r '.reprocessedCount')
            echo ""
            echo -e "Reprocessed messages: ${GREEN}$REPROCESSED_COUNT${NC}"
        else
            curl -s -X POST http://localhost:8080/api/v1/admin/dlq/reprocess
        fi
        
        echo ""
        echo "Waiting for messages to be consumed..."
        sleep 5
        
        echo ""
        echo "Checking DLQ after reprocessing:"
        if [ "$JQ_AVAILABLE" = true ]; then
            FINAL_DLQ=$(curl -s http://localhost:8080/api/v1/admin/dlq)
            echo "$FINAL_DLQ" | jq '.'
            
            FINAL_COUNT=$(echo "$FINAL_DLQ" | jq 'length')
            
            if [ "$FINAL_COUNT" -eq 0 ]; then
                echo ""
                echo -e "${GREEN}✅ DLQ is empty - all messages successfully reprocessed!${NC}"
            else
                echo ""
                echo -e "${YELLOW}⚠️  $FINAL_COUNT messages remain in DLQ${NC}"
            fi
        else
            curl -s http://localhost:8080/api/v1/admin/dlq
        fi
    fi
fi

echo ""
sleep 2

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Step 4: Monitoring DLQ Metrics${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "DLQ metrics are exposed via Actuator:"
echo ""

# Check if DLQ metrics exist
if curl -s http://localhost:8080/actuator/metrics | grep -q "telemetry.dlq"; then
    echo "📊 DLQ Messages Total:"
    if [ "$JQ_AVAILABLE" = true ]; then
        curl -s http://localhost:8080/actuator/metrics/telemetry.dlq.messages.total | jq '.measurements[0].value'
    else
        curl -s http://localhost:8080/actuator/metrics/telemetry.dlq.messages.total
    fi
    echo ""
    
    echo "📊 DLQ Reprocessed Total:"
    if [ "$JQ_AVAILABLE" = true ]; then
        curl -s http://localhost:8080/actuator/metrics/telemetry.dlq.reprocessed.total | jq '.measurements[0].value'
    else
        curl -s http://localhost:8080/actuator/metrics/telemetry.dlq.reprocessed.total
    fi
else
    echo -e "${YELLOW}⚠️  DLQ metrics not yet available (no DLQ activity yet)${NC}"
fi

echo ""
sleep 2

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Dead Letter Queue Demo Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo "Summary:"
echo "  ✓ DLQ captures messages that fail after 3 retry attempts"
echo "  ✓ Failed messages include error details and retry count"
echo "  ✓ Messages can be listed via GET /api/v1/admin/dlq"
echo "  ✓ Messages can be reprocessed via POST /api/v1/admin/dlq/reprocess"
echo "  ✓ Successfully reprocessed messages are removed from DLQ"
echo ""

echo "Key DLQ Features:"
echo "  - Exponential backoff: 1s, 2s, 4s between retries"
echo "  - Maximum 3 retry attempts before DLQ"
echo "  - Error details preserved in DLQ message headers"
echo "  - Manual reprocessing via admin endpoint"
echo "  - Automatic removal after successful reprocessing"
echo ""

echo "Manual Testing Steps:"
echo ""
echo "  1. Trigger DLQ messages by stopping PostgreSQL:"
echo "     docker stop \$(docker ps -q -f name=postgres)"
echo ""
echo "  2. Send telemetry requests (they will succeed at API but fail in consumer):"
echo "     curl -X POST http://localhost:8080/api/v1/telemetry \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"deviceId\": 1, \"measurement\": 25.5, \"date\": \"2025-01-31T10:00:00Z\"}'"
echo ""
echo "  3. Wait for retries to complete (~10 seconds)"
echo ""
echo "  4. Restart PostgreSQL:"
echo "     docker start \$(docker ps -aq -f name=postgres)"
echo ""
echo "  5. List DLQ messages:"
echo "     curl http://localhost:8080/api/v1/admin/dlq | jq"
echo ""
echo "  6. Reprocess DLQ messages:"
echo "     curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess | jq"
echo ""
echo "  7. Verify DLQ is empty:"
echo "     curl http://localhost:8080/api/v1/admin/dlq | jq"
echo ""

echo "Useful Commands:"
echo "  List DLQ messages:"
echo "    curl http://localhost:8080/api/v1/admin/dlq | jq"
echo ""
echo "  Reprocess DLQ messages:"
echo "    curl -X POST http://localhost:8080/api/v1/admin/dlq/reprocess | jq"
echo ""
echo "  Check DLQ metrics:"
echo "    curl http://localhost:8080/actuator/metrics | grep dlq"
echo ""

echo "Next steps:"
echo "  - Try the observability demo: ./demo-observability.sh"
echo "  - Try the tracing demo: ./demo-tracing.sh"
echo "  - Try the circuit breaker demo: ./demo-circuit-breaker.sh"
echo ""
