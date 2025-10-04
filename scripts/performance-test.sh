#!/bin/bash

# Kafka-Flink POC Performance Testing Script
# This script runs comprehensive performance tests on the system

set -e

# Default configuration
KAFKA_BROKERS="localhost:9092,localhost:9093,localhost:9094"
TEST_DURATION=300  # 5 minutes
EVENTS_PER_SECOND=1000
CONCURRENT_PRODUCERS=5
TEST_TYPE="mixed"
OUTPUT_DIR="./performance-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --kafka-brokers)
            KAFKA_BROKERS="$2"
            shift 2
            ;;
        --duration)
            TEST_DURATION="$2"
            shift 2
            ;;
        --rate)
            EVENTS_PER_SECOND="$2"
            shift 2
            ;;
        --producers)
            CONCURRENT_PRODUCERS="$2"
            shift 2
            ;;
        --type)
            TEST_TYPE="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --kafka-brokers BROKERS  Kafka broker list (default: $KAFKA_BROKERS)"
            echo "  --duration SECONDS       Test duration in seconds (default: $TEST_DURATION)"
            echo "  --rate EVENTS/SEC        Events per second per producer (default: $EVENTS_PER_SECOND)"
            echo "  --producers COUNT        Number of concurrent producers (default: $CONCURRENT_PRODUCERS)"
            echo "  --type TYPE              Test type: user, transaction, iot, mixed (default: $TEST_TYPE)"
            echo "  --output-dir DIR         Output directory for results (default: $OUTPUT_DIR)"
            echo "  --help                   Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create output directory
mkdir -p "$OUTPUT_DIR"
RESULT_FILE="$OUTPUT_DIR/performance_test_$TIMESTAMP.log"
METRICS_FILE="$OUTPUT_DIR/metrics_$TIMESTAMP.csv"

# Logging function
log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$RESULT_FILE"
}

log_warn() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1" | tee -a "$RESULT_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$RESULT_FILE"
}

# Check dependencies
check_dependencies() {
    log "Checking dependencies..."
    
    local deps=("kafka-console-producer.sh" "kafka-console-consumer.sh" "kafka-topics.sh" "curl" "jq")
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            log_error "$dep is not available in PATH"
            exit 1
        fi
    done
    
    log "All dependencies are available"
}

# Check Kafka connectivity
check_kafka_connectivity() {
    log "Checking Kafka connectivity..."
    
    if kafka-topics.sh --bootstrap-server "$KAFKA_BROKERS" --list &>/dev/null; then
        log "Kafka connectivity confirmed"
    else
        log_error "Cannot connect to Kafka brokers: $KAFKA_BROKERS"
        exit 1
    fi
}

# Create test topics
create_test_topics() {
    log "Creating test topics..."
    
    local topics=("perf-test-user-events" "perf-test-transaction-events" "perf-test-iot-sensor-events")
    
    for topic in "${topics[@]}"; do
        if ! kafka-topics.sh --bootstrap-server "$KAFKA_BROKERS" --describe --topic "$topic" &>/dev/null; then
            log "Creating topic: $topic"
            kafka-topics.sh --bootstrap-server "$KAFKA_BROKERS" \
                --create \
                --topic "$topic" \
                --partitions 6 \
                --replication-factor 3 \
                --config compression.type=lz4 \
                --config min.insync.replicas=2
        else
            log "Topic $topic already exists"
        fi
    done
}

# Generate test data
generate_user_event() {
    local user_id=$1
    local timestamp=$(date +%s%3N)
    
    cat <<EOF
{
    "userId": "user_$user_id",
    "action": "$(shuf -e login logout page_view purchase search -n 1)",
    "timestamp": $timestamp,
    "sessionId": "session_$(shuf -i 1000-9999 -n 1)",
    "userAgent": "TestAgent/1.0",
    "ipAddress": "192.168.$(shuf -i 1-255 -n 1).$(shuf -i 1-255 -n 1)",
    "location": "$(shuf -e 'New York' 'San Francisco' 'Chicago' 'Boston' 'Seattle' -n 1)",
    "testId": "perf_test_$TIMESTAMP"
}
EOF
}

generate_transaction_event() {
    local user_id=$1
    local timestamp=$(date +%s%3N)
    local amount=$(echo "scale=2; $(shuf -i 10-10000 -n 1) / 100" | bc)
    
    cat <<EOF
{
    "transactionId": "tx_$(uuidgen)",
    "userId": "user_$user_id",
    "amount": $amount,
    "currency": "USD",
    "timestamp": $timestamp,
    "location": "$(shuf -e 'New York' 'San Francisco' 'Chicago' -n 1)",
    "merchantId": "merchant_$(shuf -i 1-1000 -n 1)",
    "category": "$(shuf -e groceries electronics gas dining shopping -n 1)",
    "testId": "perf_test_$TIMESTAMP"
}
EOF
}

generate_iot_event() {
    local sensor_id=$1
    local timestamp=$(date +%s%3N)
    local sensor_type=$(shuf -e temperature humidity pressure light motion -n 1)
    
    local value
    case $sensor_type in
        temperature) value=$(shuf -i 15-35 -n 1) ;;
        humidity) value=$(shuf -i 30-90 -n 1) ;;
        pressure) value=$(shuf -i 1000-1100 -n 1) ;;
        light) value=$(shuf -i 0-1000 -n 1) ;;
        motion) value=$(shuf -i 0-1 -n 1) ;;
    esac
    
    cat <<EOF
{
    "sensorId": "sensor_$sensor_id",
    "sensorType": "$sensor_type",
    "value": $value,
    "unit": "$(case $sensor_type in temperature) echo celsius ;; humidity) echo percent ;; pressure) echo hPa ;; light) echo lux ;; motion) echo boolean ;; esac)",
    "timestamp": $timestamp,
    "location": "Building_$(shuf -e A B C -n 1)_Floor_$(shuf -i 1-5 -n 1)",
    "deviceId": "device_$(shuf -i 1-100 -n 1)",
    "testId": "perf_test_$TIMESTAMP"
}
EOF
}

# Producer function
run_producer() {
    local producer_id=$1
    local topic=$2
    local event_type=$3
    local events_per_sec=$4
    local duration=$5
    
    local output_file="$OUTPUT_DIR/producer_${producer_id}_$TIMESTAMP.log"
    local interval=$(echo "scale=3; 1.0 / $events_per_sec" | bc)
    
    log "Starting producer $producer_id for $event_type events on topic $topic"
    
    {
        local start_time=$(date +%s)
        local end_time=$((start_time + duration))
        local event_count=0
        
        while [ $(date +%s) -lt $end_time ]; do
            local user_id=$(shuf -i 1-10000 -n 1)
            
            case $event_type in
                user)
                    generate_user_event $user_id
                    ;;
                transaction)
                    generate_transaction_event $user_id
                    ;;
                iot)
                    generate_iot_event $user_id
                    ;;
            esac
            
            event_count=$((event_count + 1))
            
            # Rate limiting
            sleep "$interval"
        done
        
        echo "Producer $producer_id completed. Events sent: $event_count"
    } | kafka-console-producer.sh \
        --bootstrap-server "$KAFKA_BROKERS" \
        --topic "$topic" \
        --property parse.key=false \
        --property key.separator=: \
        2>&1 | tee "$output_file"
}

# Consumer function for measuring latency
run_consumer() {
    local topic=$1
    local duration=$2
    local output_file="$OUTPUT_DIR/consumer_${topic}_$TIMESTAMP.log"
    
    log "Starting consumer for topic $topic"
    
    timeout "$duration" kafka-console-consumer.sh \
        --bootstrap-server "$KAFKA_BROKERS" \
        --topic "$topic" \
        --from-beginning \
        --property print.timestamp=true \
        --property print.key=false \
        2>&1 | tee "$output_file" &
}

# System metrics collection
collect_system_metrics() {
    local duration=$1
    local metrics_file="$OUTPUT_DIR/system_metrics_$TIMESTAMP.csv"
    
    log "Collecting system metrics for $duration seconds"
    
    # CSV header
    echo "timestamp,cpu_usage,memory_usage,disk_io,network_rx,network_tx" > "$metrics_file"
    
    local start_time=$(date +%s)
    local end_time=$((start_time + duration))
    
    while [ $(date +%s) -lt $end_time ]; do
        local timestamp=$(date +%s)
        local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')
        local memory_usage=$(free | grep Mem | awk '{printf "%.1f", $3/$2 * 100.0}')
        local disk_io=$(iostat -d 1 1 | tail -n +4 | awk '{sum+=$4} END {print sum}')
        local network_rx=$(cat /proc/net/dev | grep eth0 | awk '{print $2}')
        local network_tx=$(cat /proc/net/dev | grep eth0 | awk '{print $10}')
        
        echo "$timestamp,$cpu_usage,$memory_usage,$disk_io,$network_rx,$network_tx" >> "$metrics_file"
        
        sleep 5
    done
}

# Kafka metrics collection
collect_kafka_metrics() {
    local duration=$1
    local kafka_metrics_file="$OUTPUT_DIR/kafka_metrics_$TIMESTAMP.csv"
    
    log "Collecting Kafka metrics for $duration seconds"
    
    local start_time=$(date +%s)
    local end_time=$((start_time + duration))
    
    echo "timestamp,topic,partition,offset,lag" > "$kafka_metrics_file"
    
    while [ $(date +%s) -lt $end_time ]; do
        local timestamp=$(date +%s)
        
        # Get consumer group information if available
        kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BROKERS" --list 2>/dev/null | while read group; do
            if [ ! -z "$group" ]; then
                kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BROKERS" --describe --group "$group" 2>/dev/null | \
                grep -v "GROUP\|^$" | while read line; do
                    if [ ! -z "$line" ]; then
                        echo "$timestamp,$line" >> "$kafka_metrics_file"
                    fi
                done
            fi
        done
        
        sleep 10
    done
}

# Performance test execution
run_performance_test() {
    log "Starting performance test..."
    log "Configuration:"
    log "  Test type: $TEST_TYPE"
    log "  Duration: $TEST_DURATION seconds"
    log "  Events per second per producer: $EVENTS_PER_SECOND"
    log "  Concurrent producers: $CONCURRENT_PRODUCERS"
    log "  Kafka brokers: $KAFKA_BROKERS"
    
    # Start background processes
    collect_system_metrics "$TEST_DURATION" &
    local system_metrics_pid=$!
    
    collect_kafka_metrics "$TEST_DURATION" &
    local kafka_metrics_pid=$!
    
    # Start consumers
    local consumer_pids=()
    if [ "$TEST_TYPE" = "mixed" ] || [ "$TEST_TYPE" = "user" ]; then
        run_consumer "perf-test-user-events" "$TEST_DURATION"
        consumer_pids+=($!)
    fi
    
    if [ "$TEST_TYPE" = "mixed" ] || [ "$TEST_TYPE" = "transaction" ]; then
        run_consumer "perf-test-transaction-events" "$TEST_DURATION"
        consumer_pids+=($!)
    fi
    
    if [ "$TEST_TYPE" = "mixed" ] || [ "$TEST_TYPE" = "iot" ]; then
        run_consumer "perf-test-iot-sensor-events" "$TEST_DURATION"
        consumer_pids+=($!)
    fi
    
    # Wait a moment for consumers to start
    sleep 5
    
    # Start producers
    local producer_pids=()
    for i in $(seq 1 "$CONCURRENT_PRODUCERS"); do
        case $TEST_TYPE in
            "user")
                run_producer "$i" "perf-test-user-events" "user" "$EVENTS_PER_SECOND" "$TEST_DURATION" &
                ;;
            "transaction")
                run_producer "$i" "perf-test-transaction-events" "transaction" "$EVENTS_PER_SECOND" "$TEST_DURATION" &
                ;;
            "iot")
                run_producer "$i" "perf-test-iot-sensor-events" "iot" "$EVENTS_PER_SECOND" "$TEST_DURATION" &
                ;;
            "mixed")
                local event_types=("user" "transaction" "iot")
                local topics=("perf-test-user-events" "perf-test-transaction-events" "perf-test-iot-sensor-events")
                local type_index=$((i % 3))
                run_producer "$i" "${topics[$type_index]}" "${event_types[$type_index]}" "$EVENTS_PER_SECOND" "$TEST_DURATION" &
                ;;
        esac
        producer_pids+=($!)
    done
    
    log "Test is running... Please wait $TEST_DURATION seconds"
    
    # Wait for all producers to complete
    for pid in "${producer_pids[@]}"; do
        wait "$pid"
    done
    
    log "All producers completed. Stopping consumers and metrics collection..."
    
    # Stop consumers
    for pid in "${consumer_pids[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    
    # Stop metrics collection
    kill "$system_metrics_pid" "$kafka_metrics_pid" 2>/dev/null || true
    
    log "Performance test completed"
}

# Analyze results
analyze_results() {
    log "Analyzing performance test results..."
    
    local total_events=0
    local error_count=0
    
    # Count events from producer logs
    for log_file in "$OUTPUT_DIR"/producer_*_"$TIMESTAMP".log; do
        if [ -f "$log_file" ]; then
            local events=$(grep -c "Events sent:" "$log_file" 2>/dev/null || echo "0")
            total_events=$((total_events + events))
            
            local errors=$(grep -c "ERROR\|Exception" "$log_file" 2>/dev/null || echo "0")
            error_count=$((error_count + errors))
        fi
    done
    
    # Calculate throughput
    local actual_throughput=$((total_events / TEST_DURATION))
    local expected_throughput=$((EVENTS_PER_SECOND * CONCURRENT_PRODUCERS))
    local efficiency=$(echo "scale=2; $actual_throughput * 100 / $expected_throughput" | bc)
    
    # Generate summary report
    local summary_file="$OUTPUT_DIR/summary_$TIMESTAMP.txt"
    
    cat <<EOF > "$summary_file"
Kafka-Flink POC Performance Test Summary
========================================

Test Configuration:
- Test Type: $TEST_TYPE
- Duration: $TEST_DURATION seconds
- Events per second per producer: $EVENTS_PER_SECOND
- Concurrent producers: $CONCURRENT_PRODUCERS
- Kafka brokers: $KAFKA_BROKERS

Results:
- Total events sent: $total_events
- Expected throughput: $expected_throughput events/sec
- Actual throughput: $actual_throughput events/sec
- Efficiency: $efficiency%
- Error count: $error_count

Files Generated:
- Test log: $RESULT_FILE
- System metrics: $OUTPUT_DIR/system_metrics_$TIMESTAMP.csv
- Kafka metrics: $OUTPUT_DIR/kafka_metrics_$TIMESTAMP.csv
- Summary: $summary_file
EOF

    log "Performance test summary:"
    cat "$summary_file" | tail -n +4 | tee -a "$RESULT_FILE"
    
    # Check if test was successful
    if (( $(echo "$efficiency >= 80" | bc -l) )); then
        log "✅ Performance test PASSED (efficiency: $efficiency%)"
    else
        log_warn "⚠️  Performance test shows low efficiency: $efficiency%"
    fi
    
    if [ "$error_count" -eq 0 ]; then
        log "✅ No errors detected during test"
    else
        log_error "❌ $error_count errors detected during test"
    fi
}

# Cleanup function
cleanup() {
    log "Cleaning up test resources..."
    
    # Kill any remaining background processes
    jobs -p | xargs -r kill 2>/dev/null || true
    
    # Optionally clean up test topics (commented out to preserve data)
    # kafka-topics.sh --bootstrap-server "$KAFKA_BROKERS" --delete --topic "perf-test-.*" 2>/dev/null || true
    
    log "Cleanup completed"
}

# Main execution
main() {
    log "=== Kafka-Flink POC Performance Test ==="
    
    check_dependencies
    check_kafka_connectivity
    create_test_topics
    
    # Set up cleanup trap
    trap cleanup EXIT
    
    run_performance_test
    analyze_results
    
    log "Performance test completed successfully!"
    log "Results are available in: $OUTPUT_DIR"
}

# Execute main function
main "$@"