#!/bin/bash

# End-to-End Pipeline Test for Kafka-Flink POC
# Tests the complete data flow from producers to Elasticsearch

set -e

echo "🧪 Testing End-to-End Data Pipeline"
echo "===================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KAFKA_BROKERS="localhost:9092"
ELASTICSEARCH_URL="http://localhost:9200"
FLINK_URL="http://localhost:8081"

# Test configuration
TEST_DURATION=30  # seconds
EVENTS_PER_SECOND=5
VERIFICATION_WAIT=15  # seconds to wait for data processing

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            TEST_DURATION="$2"
            shift 2
            ;;
        --rate)
            EVENTS_PER_SECOND="$2"
            shift 2
            ;;
        --wait)
            VERIFICATION_WAIT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --duration SECONDS    Test duration (default: 30)"
            echo "  --rate RATE          Events per second (default: 5)"
            echo "  --wait SECONDS       Wait time for processing (default: 15)"
            echo "  --help               Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to check service health
check_service() {
    local service_name=$1
    local check_command=$2
    
    echo -n "Checking $service_name... "
    if eval $check_command > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Healthy${NC}"
        return 0
    else
        echo -e "${RED}✗ Unhealthy${NC}"
        return 1
    fi
}

# Function to verify all services
verify_services() {
    echo -e "\n${BLUE}🔍 Verifying Service Health${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local failed_services=0
    
    if ! check_service "Kafka" "docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list"; then
        ((failed_services++))
    fi
    
    if ! check_service "Flink" "curl -s $FLINK_URL/overview"; then
        ((failed_services++))
    fi
    
    if ! check_service "Elasticsearch" "curl -s $ELASTICSEARCH_URL/_cluster/health"; then
        ((failed_services++))
    fi
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [[ $failed_services -eq 0 ]]; then
        echo -e "${GREEN}✅ All services healthy${NC}"
        return 0
    else
        echo -e "${RED}❌ $failed_services service(s) unhealthy${NC}"
        return 1
    fi
}

# Function to check if topics exist
verify_topics() {
    echo -e "\n${BLUE}📋 Verifying Kafka Topics${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local required_topics=("user-events" "transaction-events" "iot-sensor-events")
    local missing_topics=0
    
    for topic in "${required_topics[@]}"; do
        echo -n "Checking topic '$topic'... "
        if docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list | grep -q "^${topic}$"; then
            echo -e "${GREEN}✓ Exists${NC}"
        else
            echo -e "${RED}✗ Missing${NC}"
            ((missing_topics++))
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [[ $missing_topics -eq 0 ]]; then
        echo -e "${GREEN}✅ All topics exist${NC}"
        return 0
    else
        echo -e "${RED}❌ $missing_topics topic(s) missing${NC}"
        echo "Run: ./scripts/setup-topics.sh"
        return 1
    fi
}

# Function to start test producers
start_test_producers() {
    echo -e "\n${BLUE}🚀 Starting Test Data Producers${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Create test logs directory
    mkdir -p "$BASE_DIR/test-logs"
    
    # Start producers with low rates for testing
    echo "Starting user events producer..."
    ./scripts/start-producers.sh \
        --user-rate $EVENTS_PER_SECOND \
        --transaction-rate $((EVENTS_PER_SECOND / 2)) \
        --iot-rate $((EVENTS_PER_SECOND * 2)) \
        --duration $TEST_DURATION > /dev/null 2>&1 &
    
    local producer_pid=$!
    echo "Producer script PID: $producer_pid"
    
    # Wait for producers to start
    sleep 5
    
    echo -e "${GREEN}✅ Test producers started${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    return 0
}

# Function to monitor message production
monitor_production() {
    echo -e "\n${BLUE}📊 Monitoring Message Production${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local topics=("user-events" "transaction-events" "iot-sensor-events")
    local start_time=$(date +%s)
    local end_time=$((start_time + TEST_DURATION))
    
    echo "Monitoring for $TEST_DURATION seconds..."
    echo ""
    
    while [[ $(date +%s) -lt $end_time ]]; do
        echo -n "$(date '+%H:%M:%S') - "
        
        local total_messages=0
        for topic in "${topics[@]}"; do
            local count=$(docker exec kafka-poc-kafka1 kafka-run-class kafka.tools.GetOffsetShell \
                --bootstrap-server $KAFKA_BROKERS \
                --topic $topic 2>/dev/null | \
                awk -F':' '{sum += $3} END {print sum+0}')
            
            echo -n "$topic: $count "
            total_messages=$((total_messages + count))
        done
        
        echo "| Total: $total_messages"
        sleep 5
    done
    
    echo ""
    echo -e "${GREEN}✅ Production monitoring complete${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to verify Flink jobs
verify_flink_jobs() {
    echo -e "\n${BLUE}🎯 Verifying Flink Jobs${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local jobs_response=$(curl -s $FLINK_URL/jobs 2>/dev/null)
    
    if [[ $? -eq 0 && -n "$jobs_response" ]]; then
        if command -v jq >/dev/null 2>&1; then
            local running_count=$(echo "$jobs_response" | jq '[.jobs[] | select(.state == "RUNNING")] | length')
            local failed_count=$(echo "$jobs_response" | jq '[.jobs[] | select(.state == "FAILED")] | length')
            
            echo "Running jobs: $running_count"
            echo "Failed jobs: $failed_count"
            
            if [[ $running_count -gt 0 ]]; then
                echo -e "${GREEN}✅ Flink jobs are running${NC}"
            else
                echo -e "${YELLOW}⚠️  No running Flink jobs found${NC}"
                echo "Deploy jobs with: ./scripts/build-flink-jobs.sh"
            fi
            
            if [[ $failed_count -gt 0 ]]; then
                echo -e "${RED}⚠️  $failed_count job(s) failed${NC}"
            fi
        else
            echo "Flink API accessible (install jq for job details)"
        fi
    else
        echo -e "${RED}✗ Cannot access Flink jobs API${NC}"
        return 1
    fi
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to verify Elasticsearch data
verify_elasticsearch_data() {
    echo -e "\n${BLUE}🔍 Verifying Elasticsearch Data${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    echo "Waiting $VERIFICATION_WAIT seconds for data processing..."
    sleep $VERIFICATION_WAIT
    
    local indices=("user-events" "transaction-events" "iot-sensor-events")
    local total_docs=0
    
    for index in "${indices[@]}"; do
        echo -n "Checking index '$index'... "
        
        local count_response=$(curl -s "$ELASTICSEARCH_URL/${index}/_count" 2>/dev/null)
        
        if [[ $? -eq 0 && -n "$count_response" ]]; then
            if command -v jq >/dev/null 2>&1; then
                local doc_count=$(echo "$count_response" | jq -r '.count // 0')
                echo -e "${GREEN}$doc_count documents${NC}"
                total_docs=$((total_docs + doc_count))
            else
                echo -e "${GREEN}accessible${NC}"
            fi
        else
            echo -e "${YELLOW}not found or empty${NC}"
        fi
    done
    
    echo ""
    echo "Total documents in Elasticsearch: $total_docs"
    
    if [[ $total_docs -gt 0 ]]; then
        echo -e "${GREEN}✅ Data successfully processed to Elasticsearch${NC}"
        
        # Show sample data
        echo -e "\n${BLUE}📄 Sample Documents${NC}"
        for index in "${indices[@]}"; do
            echo "Sample from $index:"
            curl -s "$ELASTICSEARCH_URL/${index}/_search?size=1&pretty" 2>/dev/null | \
                jq -r '.hits.hits[0]._source // "No documents"' 2>/dev/null || \
                echo "Unable to parse (install jq for formatted output)"
            echo ""
        done
    else
        echo -e "${YELLOW}⚠️  No data found in Elasticsearch${NC}"
        echo "This might indicate:"
        echo "• Flink jobs are not running"
        echo "• Data pipeline configuration issues"
        echo "• Processing delays"
    fi
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to stop test producers
stop_test_producers() {
    echo -e "\n${BLUE}🛑 Stopping Test Producers${NC}"
    
    ./scripts/stop-producers.sh > /dev/null 2>&1
    
    echo -e "${GREEN}✅ Test producers stopped${NC}"
}

# Function to show test summary
show_test_summary() {
    echo -e "\n${BLUE}📋 Test Summary${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "• Test Duration: $TEST_DURATION seconds"
    echo "• Events Per Second: $EVENTS_PER_SECOND"
    echo "• Processing Wait: $VERIFICATION_WAIT seconds"
    echo "• Expected Events: ~$((TEST_DURATION * EVENTS_PER_SECOND * 4)) total"
    echo ""
    
    # Get final counts
    echo "Final Kafka Topic Counts:"
    for topic in "user-events" "transaction-events" "iot-sensor-events"; do
        local count=$(docker exec kafka-poc-kafka1 kafka-run-class kafka.tools.GetOffsetShell \
            --bootstrap-server $KAFKA_BROKERS \
            --topic $topic 2>/dev/null | \
            awk -F':' '{sum += $3} END {print sum+0}')
        echo "• $topic: $count messages"
    done
    
    echo ""
    echo "Elasticsearch Document Counts:"
    for index in "user-events" "transaction-events" "iot-sensor-events"; do
        local count_response=$(curl -s "$ELASTICSEARCH_URL/${index}/_count" 2>/dev/null)
        if command -v jq >/dev/null 2>&1; then
            local doc_count=$(echo "$count_response" | jq -r '.count // 0' 2>/dev/null || echo "0")
            echo "• $index: $doc_count documents"
        else
            echo "• $index: unable to count (install jq)"
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to show next steps
show_next_steps() {
    echo -e "\n${YELLOW}🔗 Next Steps${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "1. View data in Kibana: http://localhost:5601"
    echo "2. Monitor Flink jobs: http://localhost:8081"
    echo "3. Check Kafka topics: http://localhost:8080"
    echo "4. Query Elasticsearch: curl $ELASTICSEARCH_URL/_cat/indices"
    echo ""
    echo "Start full producers: ./scripts/start-producers.sh"
    echo "Deploy additional Flink jobs: ./scripts/build-flink-jobs.sh"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Main execution
main() {
    echo -e "\n${YELLOW}⚙️  Test Configuration:${NC}"
    echo "• Test Duration: $TEST_DURATION seconds"
    echo "• Events Per Second: $EVENTS_PER_SECOND"
    echo "• Processing Wait: $VERIFICATION_WAIT seconds"
    echo "• Kafka Brokers: $KAFKA_BROKERS"
    echo "• Elasticsearch: $ELASTICSEARCH_URL"
    echo "• Flink: $FLINK_URL"
    
    # Step 1: Verify all services are healthy
    if ! verify_services; then
        echo -e "\n${RED}❌ Service health check failed${NC}"
        echo "Start all services with: ./scripts/start-environment.sh"
        exit 1
    fi
    
    # Step 2: Verify Kafka topics exist
    if ! verify_topics; then
        echo -e "\n${RED}❌ Topics verification failed${NC}"
        exit 1
    fi
    
    # Step 3: Check Flink jobs
    verify_flink_jobs
    
    # Step 4: Start test data producers
    start_test_producers
    
    # Step 5: Monitor message production
    monitor_production
    
    # Step 6: Stop producers
    stop_test_producers
    
    # Step 7: Verify data reached Elasticsearch
    verify_elasticsearch_data
    
    # Step 8: Show test summary
    show_test_summary
    
    # Step 9: Show next steps
    show_next_steps
    
    echo -e "\n${GREEN}🎉 End-to-End Pipeline Test Complete!${NC}"
}

# Trap Ctrl+C for clean exit
trap 'echo -e "\n${YELLOW}Test interrupted. Cleaning up...${NC}"; stop_test_producers; exit 1' INT

# Run main function
main