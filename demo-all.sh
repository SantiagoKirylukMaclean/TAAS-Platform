#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Track demo results
OBSERVABILITY_STATUS="⏳"
TRACING_STATUS="⏳"
CIRCUIT_BREAKER_STATUS="⏳"
DLQ_STATUS="⏳"

echo ""
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║                                                            ║${NC}"
echo -e "${MAGENTA}║        🚀 Production Enhancements - Full Demo Suite       ║${NC}"
echo -e "${MAGENTA}║                                                            ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "This script will run all production feature demos in sequence:"
echo ""
echo "  1. 🎯 Observability Demo - Metrics, Prometheus, Grafana"
echo "  2. 🔍 Distributed Tracing Demo - Zipkin traces"
echo "  3. 🛡️  Circuit Breaker Demo - Resilience patterns"
echo "  4. 🔄 Dead Letter Queue Demo - Error recovery"
echo ""
echo "Total estimated time: ~5-10 minutes"
echo ""

# Check if application is running
echo "Checking prerequisites..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Application is not running on port 8080${NC}"
    echo "Please start the application first with: ./start.sh"
    exit 1
fi
echo -e "${GREEN}✅ Application is running${NC}"

# Check if docker is running
if ! docker ps > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Docker is not running${NC}"
    echo "Please start Docker first"
    exit 1
fi
echo -e "${GREEN}✅ Docker is running${NC}"

# Check if required services are running
REQUIRED_SERVICES=("kafka" "postgres" "prometheus" "grafana" "zipkin")
MISSING_SERVICES=()

for service in "${REQUIRED_SERVICES[@]}"; do
    if ! docker ps --format '{{.Names}}' | grep -q "$service"; then
        MISSING_SERVICES+=("$service")
    fi
done

if [ ${#MISSING_SERVICES[@]} -gt 0 ]; then
    echo -e "${YELLOW}⚠️  Warning: Some services are not running: ${MISSING_SERVICES[*]}${NC}"
    echo "Please ensure all services are started with docker-compose"
    echo ""
    echo -e "${YELLOW}Do you want to continue anyway? (y/n)${NC}"
    read -t 10 -n 1 -r REPLY
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Exiting..."
        exit 1
    fi
else
    echo -e "${GREEN}✅ All required services are running${NC}"
fi

echo ""
echo -e "${CYAN}Press Enter to start the demo suite, or Ctrl+C to cancel...${NC}"
read -t 10 -r
echo ""

# Function to print section separator
print_separator() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
    echo ""
}

# Function to pause between demos
pause_between_demos() {
    local seconds=$1
    local next_demo=$2
    
    echo ""
    echo -e "${YELLOW}Pausing for $seconds seconds before next demo...${NC}"
    echo -e "${YELLOW}Next: $next_demo${NC}"
    echo ""
    
    for i in $(seq $seconds -1 1); do
        echo -n "$i "
        sleep 1
    done
    echo ""
    echo ""
}

# =============================================================================
# Demo 1: Observability
# =============================================================================

print_separator
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║  Demo 1/4: 🎯 Observability - Metrics & Monitoring        ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
print_separator

if [ -f "./demo-observability.sh" ]; then
    if bash ./demo-observability.sh; then
        OBSERVABILITY_STATUS="${GREEN}✅${NC}"
        echo -e "${GREEN}✅ Observability demo completed successfully${NC}"
    else
        OBSERVABILITY_STATUS="${RED}❌${NC}"
        echo -e "${RED}❌ Observability demo failed${NC}"
    fi
else
    OBSERVABILITY_STATUS="${RED}❌${NC}"
    echo -e "${RED}❌ demo-observability.sh not found${NC}"
fi

pause_between_demos 5 "🔍 Distributed Tracing Demo"

# =============================================================================
# Demo 2: Distributed Tracing
# =============================================================================

print_separator
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║  Demo 2/4: 🔍 Distributed Tracing - End-to-End Traces    ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
print_separator

if [ -f "./demo-tracing.sh" ]; then
    if bash ./demo-tracing.sh; then
        TRACING_STATUS="${GREEN}✅${NC}"
        echo -e "${GREEN}✅ Distributed tracing demo completed successfully${NC}"
    else
        TRACING_STATUS="${RED}❌${NC}"
        echo -e "${RED}❌ Distributed tracing demo failed${NC}"
    fi
else
    TRACING_STATUS="${RED}❌${NC}"
    echo -e "${RED}❌ demo-tracing.sh not found${NC}"
fi

pause_between_demos 5 "🛡️  Circuit Breaker Demo"

# =============================================================================
# Demo 3: Circuit Breaker
# =============================================================================

print_separator
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║  Demo 3/4: 🛡️  Circuit Breaker - Resilience Patterns      ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
print_separator

echo -e "${YELLOW}⚠️  Note: This demo will temporarily stop and restart Kafka${NC}"
echo -e "${YELLOW}This may take 30-60 seconds to complete${NC}"
echo ""

if [ -f "./demo-circuit-breaker.sh" ]; then
    if bash ./demo-circuit-breaker.sh; then
        CIRCUIT_BREAKER_STATUS="${GREEN}✅${NC}"
        echo -e "${GREEN}✅ Circuit breaker demo completed successfully${NC}"
    else
        CIRCUIT_BREAKER_STATUS="${RED}❌${NC}"
        echo -e "${RED}❌ Circuit breaker demo failed${NC}"
    fi
else
    CIRCUIT_BREAKER_STATUS="${RED}❌${NC}"
    echo -e "${RED}❌ demo-circuit-breaker.sh not found${NC}"
fi

pause_between_demos 5 "🔄 Dead Letter Queue Demo"

# =============================================================================
# Demo 4: Dead Letter Queue
# =============================================================================

print_separator
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║  Demo 4/4: 🔄 Dead Letter Queue - Error Recovery         ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
print_separator

if [ -f "./demo-dlq.sh" ]; then
    if bash ./demo-dlq.sh; then
        DLQ_STATUS="${GREEN}✅${NC}"
        echo -e "${GREEN}✅ Dead Letter Queue demo completed successfully${NC}"
    else
        DLQ_STATUS="${RED}❌${NC}"
        echo -e "${RED}❌ Dead Letter Queue demo failed${NC}"
    fi
else
    DLQ_STATUS="${RED}❌${NC}"
    echo -e "${RED}❌ demo-dlq.sh not found${NC}"
fi

# =============================================================================
# Final Summary
# =============================================================================

print_separator
echo ""
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║                                                            ║${NC}"
echo -e "${MAGENTA}║              🎉 Demo Suite Complete! 🎉                    ║${NC}"
echo -e "${MAGENTA}║                                                            ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${CYAN}Demo Results Summary:${NC}"
echo -e "${CYAN}════════════════════${NC}"
echo ""
echo -e "  1. Observability Demo:        $OBSERVABILITY_STATUS"
echo -e "  2. Distributed Tracing Demo:  $TRACING_STATUS"
echo -e "  3. Circuit Breaker Demo:      $CIRCUIT_BREAKER_STATUS"
echo -e "  4. Dead Letter Queue Demo:    $DLQ_STATUS"
echo ""

# Count successes
SUCCESS_COUNT=0
if [[ "$OBSERVABILITY_STATUS" == *"✅"* ]]; then SUCCESS_COUNT=$((SUCCESS_COUNT + 1)); fi
if [[ "$TRACING_STATUS" == *"✅"* ]]; then SUCCESS_COUNT=$((SUCCESS_COUNT + 1)); fi
if [[ "$CIRCUIT_BREAKER_STATUS" == *"✅"* ]]; then SUCCESS_COUNT=$((SUCCESS_COUNT + 1)); fi
if [[ "$DLQ_STATUS" == *"✅"* ]]; then SUCCESS_COUNT=$((SUCCESS_COUNT + 1)); fi

echo -e "${CYAN}Overall: $SUCCESS_COUNT/4 demos completed successfully${NC}"
echo ""

if [ $SUCCESS_COUNT -eq 4 ]; then
    echo -e "${GREEN}🎊 Perfect! All demos completed successfully! 🎊${NC}"
else
    echo -e "${YELLOW}⚠️  Some demos encountered issues. Check the output above for details.${NC}"
fi

echo ""
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Production Features Overview${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""

echo "📊 Observability & Monitoring:"
echo "   • Metrics: http://localhost:8080/actuator/metrics"
echo "   • Prometheus: http://localhost:9090"
echo "   • Grafana: http://localhost:3000 (admin/admin)"
echo ""

echo "🔍 Distributed Tracing:"
echo "   • Zipkin UI: http://localhost:9411"
echo "   • Trace IDs in logs: [traceId,spanId]"
echo "   • End-to-end request tracking"
echo ""

echo "🛡️  Resilience Patterns:"
echo "   • Circuit Breaker: http://localhost:8080/actuator/circuitbreakers"
echo "   • Fallback Events: SELECT * FROM fallback_events;"
echo "   • Replay: POST /api/v1/admin/fallback/replay"
echo ""

echo "🔄 Error Recovery:"
echo "   • DLQ Messages: GET /api/v1/admin/dlq"
echo "   • Reprocess: POST /api/v1/admin/dlq/reprocess"
echo "   • Exponential backoff with 3 retries"
echo ""

echo "🏥 Health Checks:"
echo "   • Overall Health: http://localhost:8080/actuator/health"
echo "   • Liveness: http://localhost:8080/actuator/health/liveness"
echo "   • Readiness: http://localhost:8080/actuator/health/readiness"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Key Achievements${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""

echo "✅ Comprehensive observability with metrics, logs, and traces"
echo "✅ Production-ready monitoring with Prometheus and Grafana"
echo "✅ Distributed tracing across HTTP, Kafka, and database"
echo "✅ Circuit breaker protection against downstream failures"
echo "✅ Dead Letter Queue for error recovery and replay"
echo "✅ Health checks for Kubernetes readiness and liveness"
echo "✅ Fallback mechanisms to prevent data loss"
echo "✅ Exponential backoff retry strategies"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Next Steps${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""

echo "1. Explore Grafana Dashboards:"
echo "   • Open http://localhost:3000"
echo "   • View real-time metrics and system health"
echo ""

echo "2. Analyze Traces in Zipkin:"
echo "   • Open http://localhost:9411"
echo "   • Search for traces and analyze performance"
echo ""

echo "3. Test Resilience Patterns:"
echo "   • Stop services and observe circuit breaker behavior"
echo "   • Trigger errors and verify DLQ functionality"
echo ""

echo "4. Monitor System Health:"
echo "   • Check health endpoints for service status"
echo "   • View circuit breaker states and metrics"
echo ""

echo "5. Review Documentation:"
echo "   • README-PRODUCTION.md (if available)"
echo "   • API documentation for admin endpoints"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}Thank you for exploring the production enhancements! 🚀${NC}"
echo ""
echo "For questions or issues, please refer to the documentation or"
echo "check the application logs for detailed information."
echo ""

# Exit with appropriate code
if [ $SUCCESS_COUNT -eq 4 ]; then
    exit 0
else
    exit 1
fi
