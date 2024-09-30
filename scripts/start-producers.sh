#!/bin/bash

# Start All Data Producers for Kafka-Flink POC
# Manages Python virtual environments and producer processes

set -e

echo "🚀 Starting Data Producers for Kafka-Flink POC"
echo "=============================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
KAFKA_BROKERS="localhost:9092"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRODUCERS_DIR="$BASE_DIR/data-producers"
VENV_DIR="$BASE_DIR/.venv"
PID_DIR="$BASE_DIR/.pids"

# Default production rates (events per second)
USER_EVENTS_RATE=10
TRANSACTION_EVENTS_RATE=5
IOT_SENSOR_EVENTS_RATE=20

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --user-rate)
            USER_EVENTS_RATE="$2"
            shift 2
            ;;
        --transaction-rate)
            TRANSACTION_EVENTS_RATE="$2"
            shift 2
            ;;
        --iot-rate)
            IOT_SENSOR_EVENTS_RATE="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --user-rate RATE           Events per second for user events (default: 10)"
            echo "  --transaction-rate RATE    Events per second for transactions (default: 5)"
            echo "  --iot-rate RATE           Events per second for IoT sensors (default: 20)"
            echo "  --duration SECONDS        Duration to run (default: continuous)"
            echo "  --help                    Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to check if Kafka is ready
check_kafka() {
    echo -n "Checking Kafka connectivity... "
    if docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Connected${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

# Function to setup Python virtual environment
setup_venv() {
    echo -e "\n${BLUE}📦 Setting up Python environment${NC}"
    
    if [[ ! -d "$VENV_DIR" ]]; then
        echo "Creating virtual environment..."
        python3 -m venv "$VENV_DIR"
    fi
    
    echo "Activating virtual environment..."
    source "$VENV_DIR/bin/activate"
    
    echo "Installing/updating dependencies..."
    pip install --upgrade pip > /dev/null 2>&1
    
    # Install common dependencies
    pip install kafka-python==2.0.2 numpy==1.24.3 faker==19.6.2 > /dev/null 2>&1
    
    echo -e "${GREEN}✓ Python environment ready${NC}"
}

# Function to create PID directory
setup_pid_dir() {
    mkdir -p "$PID_DIR"
}

# Function to kill existing producers
kill_existing_producers() {
    echo -e "\n${YELLOW}🔄 Stopping existing producers${NC}"
    
    if [[ -d "$PID_DIR" ]]; then
        for pidfile in "$PID_DIR"/*.pid; do
            if [[ -f "$pidfile" ]]; then
                pid=$(cat "$pidfile")
                producer_name=$(basename "$pidfile" .pid)
                
                if kill -0 "$pid" 2>/dev/null; then
                    echo "Stopping $producer_name (PID: $pid)..."
                    kill "$pid"
                    sleep 2
                    
                    # Force kill if still running
                    if kill -0 "$pid" 2>/dev/null; then
                        echo "Force killing $producer_name..."
                        kill -9 "$pid" 2>/dev/null || true
                    fi
                fi
                
                rm -f "$pidfile"
            fi
        done
    fi
    
    echo -e "${GREEN}✓ Existing producers stopped${NC}"
}

# Function to start a producer
start_producer() {
    local producer_name=$1
    local producer_script=$2
    local rate=$3
    local duration_arg=$4
    
    echo -n "Starting $producer_name (${rate} events/sec)... "
    
    cd "$PRODUCERS_DIR/$producer_name"
    
    if [[ -n "$duration_arg" ]]; then
        nohup "$VENV_DIR/bin/python" "$producer_script" \
            --bootstrap-servers "$KAFKA_BROKERS" \
            --rate "$rate" \
            --duration "$DURATION" \
            > "$BASE_DIR/logs/${producer_name}.log" 2>&1 &
    else
        nohup "$VENV_DIR/bin/python" "$producer_script" \
            --bootstrap-servers "$KAFKA_BROKERS" \
            --rate "$rate" \
            > "$BASE_DIR/logs/${producer_name}.log" 2>&1 &
    fi
    
    local pid=$!
    echo "$pid" > "$PID_DIR/${producer_name}.pid"
    
    # Wait a moment and check if process is still running
    sleep 2
    if kill -0 "$pid" 2>/dev/null; then
        echo -e "${GREEN}✓ Started (PID: $pid)${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to start${NC}"
        rm -f "$PID_DIR/${producer_name}.pid"
        return 1
    fi
}

# Function to show producer status
show_status() {
    echo -e "\n${BLUE}📊 Producer Status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local all_running=true
    
    for producer in "user-events" "transactions" "iot-sensors"; do
        local pidfile="$PID_DIR/${producer}.pid"
        
        if [[ -f "$pidfile" ]]; then
            local pid=$(cat "$pidfile")
            if kill -0 "$pid" 2>/dev/null; then
                echo -e "🟢 $producer: Running (PID: $pid)"
            else
                echo -e "🔴 $producer: Stopped"
                all_running=false
                rm -f "$pidfile"
            fi
        else
            echo -e "🔴 $producer: Not started"
            all_running=false
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if $all_running; then
        echo -e "${GREEN}✅ All producers are running${NC}"
    else
        echo -e "${YELLOW}⚠️  Some producers are not running${NC}"
    fi
}

# Function to monitor producers
monitor_producers() {
    echo -e "\n${BLUE}📈 Monitoring Producer Output${NC}"
    echo "Press Ctrl+C to stop monitoring (producers will continue running)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Show tail of all log files
    if command -v multitail >/dev/null 2>&1; then
        multitail \
            -cT ansi \
            -l "tail -f $BASE_DIR/logs/user-events.log" \
            -l "tail -f $BASE_DIR/logs/transactions.log" \
            -l "tail -f $BASE_DIR/logs/iot-sensors.log"
    else
        echo "Installing multitail would improve monitoring experience"
        echo "For now, showing combined output:"
        tail -f "$BASE_DIR/logs/"*.log 2>/dev/null || echo "No log files found yet"
    fi
}

# Main execution
main() {
    echo -e "\n${YELLOW}⚙️  Configuration:${NC}"
    echo "• User Events Rate: $USER_EVENTS_RATE events/sec"
    echo "• Transaction Events Rate: $TRANSACTION_EVENTS_RATE events/sec"
    echo "• IoT Sensor Events Rate: $IOT_SENSOR_EVENTS_RATE events/sec"
    echo "• Kafka Brokers: $KAFKA_BROKERS"
    if [[ -n "$DURATION" ]]; then
        echo "• Duration: $DURATION seconds"
    else
        echo "• Duration: Continuous (until stopped)"
    fi
    
    # Check prerequisites
    if ! check_kafka; then
        echo -e "${RED}❌ Kafka is not accessible. Please start the Kafka cluster first.${NC}"
        echo "Run: ./scripts/start-environment.sh"
        exit 1
    fi
    
    # Setup environment
    setup_venv
    setup_pid_dir
    
    # Create logs directory
    mkdir -p "$BASE_DIR/logs"
    
    # Stop existing producers
    kill_existing_producers
    
    echo -e "\n${BLUE}🚀 Starting Producers${NC}"
    
    # Set duration argument if specified
    local duration_arg=""
    if [[ -n "$DURATION" ]]; then
        duration_arg="--duration $DURATION"
    fi
    
    # Start all producers
    local failed_count=0
    
    if ! start_producer "user-events" "user_event_producer.py" "$USER_EVENTS_RATE" "$duration_arg"; then
        ((failed_count++))
    fi
    
    if ! start_producer "transactions" "transaction_producer.py" "$TRANSACTION_EVENTS_RATE" "$duration_arg"; then
        ((failed_count++))
    fi
    
    if ! start_producer "iot-sensors" "iot_sensor_producer.py" "$IOT_SENSOR_EVENTS_RATE" "$duration_arg"; then
        ((failed_count++))
    fi
    
    # Show status
    show_status
    
    if [[ $failed_count -eq 0 ]]; then
        echo -e "\n${GREEN}🎉 All producers started successfully!${NC}"
        
        echo -e "\n${YELLOW}📖 Monitoring Commands:${NC}"
        echo "• Check status: ./scripts/producer-status.sh"
        echo "• Stop all: ./scripts/stop-producers.sh"
        echo "• View logs: tail -f logs/[producer-name].log"
        echo "• Monitor Kafka: docker exec -it kafka-poc-kafka1 kafka-console-consumer --bootstrap-server localhost:9092 --topic [topic-name]"
        
        # Offer to monitor
        echo -e "\n${BLUE}Would you like to monitor producer output? (y/n)${NC}"
        read -r response
        if [[ "$response" =~ ^[Yy]$ ]]; then
            monitor_producers
        fi
        
    else
        echo -e "\n${RED}❌ $failed_count producer(s) failed to start${NC}"
        echo "Check the logs in $BASE_DIR/logs/ for details"
        exit 1
    fi
}

# Trap Ctrl+C to clean exit from monitoring
trap 'echo -e "\n${YELLOW}Monitoring stopped. Producers are still running.${NC}"; exit 0' INT

# Run main function
main