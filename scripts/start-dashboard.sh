#!/bin/bash

# Start Dashboard and WebSocket Server for Kafka-Flink POC
# Manages both the React frontend and Node.js WebSocket server

set -e

echo "🎯 Starting Kafka-Flink Dashboard"
echo "================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DASHBOARD_DIR="$BASE_DIR/dashboard"
WEBSOCKET_DIR="$BASE_DIR/websocket-server"
PID_DIR="$BASE_DIR/.pids"

# Parse command line arguments
DEV_MODE=true
INSTALL_DEPS=false
BACKGROUND=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --production)
            DEV_MODE=false
            shift
            ;;
        --install)
            INSTALL_DEPS=true
            shift
            ;;
        --background)
            BACKGROUND=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --production     Run in production mode"
            echo "  --install        Install dependencies before starting"
            echo "  --background     Run in background"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to check if Node.js is installed
check_nodejs() {
    echo -n "Checking Node.js installation... "
    if command -v node >/dev/null 2>&1; then
        local node_version=$(node --version)
        echo -e "${GREEN}✓ Found ($node_version)${NC}"
        return 0
    else
        echo -e "${RED}✗ Not found${NC}"
        return 1
    fi
}

# Function to check if npm is installed
check_npm() {
    echo -n "Checking npm installation... "
    if command -v npm >/dev/null 2>&1; then
        local npm_version=$(npm --version)
        echo -e "${GREEN}✓ Found ($npm_version)${NC}"
        return 0
    else
        echo -e "${RED}✗ Not found${NC}"
        return 1
    fi
}

# Function to check Kafka connectivity
check_kafka() {
    echo -n "Checking Kafka connectivity... "
    if docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Connected${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

# Function to install dependencies
install_dependencies() {
    echo -e "\n${BLUE}📦 Installing Dependencies${NC}"
    
    # Install WebSocket server dependencies
    echo "Installing WebSocket server dependencies..."
    cd "$WEBSOCKET_DIR"
    npm install
    
    # Install dashboard dependencies
    echo "Installing dashboard dependencies..."
    cd "$DASHBOARD_DIR"
    npm install
    
    echo -e "${GREEN}✅ Dependencies installed${NC}"
}

# Function to stop existing processes
stop_existing_processes() {
    echo -e "\n${YELLOW}🔄 Stopping existing dashboard processes${NC}"
    
    if [[ -d "$PID_DIR" ]]; then
        for pidfile in "$PID_DIR"/dashboard-*.pid; do
            if [[ -f "$pidfile" ]]; then
                local pid=$(cat "$pidfile")
                local process_name=$(basename "$pidfile" .pid)
                
                if kill -0 "$pid" 2>/dev/null; then
                    echo "Stopping $process_name (PID: $pid)..."
                    kill "$pid"
                    sleep 2
                    
                    # Force kill if still running
                    if kill -0 "$pid" 2>/dev/null; then
                        echo "Force killing $process_name..."
                        kill -9 "$pid" 2>/dev/null || true
                    fi
                fi
                
                rm -f "$pidfile"
            fi
        done
    fi
    
    echo -e "${GREEN}✅ Existing processes stopped${NC}"
}

# Function to start WebSocket server
start_websocket_server() {
    echo -e "\n${BLUE}🔌 Starting WebSocket Server${NC}"
    
    cd "$WEBSOCKET_DIR"
    
    # Create logs directory
    mkdir -p logs
    
    local start_command
    if $DEV_MODE; then
        start_command="npm run dev"
    else
        start_command="npm start"
    fi
    
    if $BACKGROUND; then
        nohup $start_command > logs/websocket-server.log 2>&1 &
        local websocket_pid=$!
        echo "$websocket_pid" > "$PID_DIR/dashboard-websocket.pid"
        
        # Wait and verify the process started
        sleep 3
        if kill -0 "$websocket_pid" 2>/dev/null; then
            echo -e "${GREEN}✓ WebSocket server started (PID: $websocket_pid)${NC}"
        else
            echo -e "${RED}✗ WebSocket server failed to start${NC}"
            return 1
        fi
    else
        echo "Starting WebSocket server in foreground..."
        echo "Press Ctrl+C to stop"
        exec $start_command
    fi
}

# Function to start dashboard
start_dashboard() {
    echo -e "\n${BLUE}🎨 Starting React Dashboard${NC}"
    
    cd "$DASHBOARD_DIR"
    
    # Create logs directory
    mkdir -p logs
    
    local start_command
    if $DEV_MODE; then
        start_command="npm run dev"
    else
        # Build for production first
        echo "Building dashboard for production..."
        npm run build
        start_command="npm run preview"
    fi
    
    if $BACKGROUND; then
        nohup $start_command > logs/dashboard.log 2>&1 &
        local dashboard_pid=$!
        echo "$dashboard_pid" > "$PID_DIR/dashboard-frontend.pid"
        
        # Wait and verify the process started
        sleep 5
        if kill -0 "$dashboard_pid" 2>/dev/null; then
            echo -e "${GREEN}✓ Dashboard started (PID: $dashboard_pid)${NC}"
        else
            echo -e "${RED}✗ Dashboard failed to start${NC}"
            return 1
        fi
    else
        echo "Starting dashboard in foreground..."
        echo "Press Ctrl+C to stop"
        exec $start_command
    fi
}

# Function to show status
show_status() {
    echo -e "\n${BLUE}📊 Dashboard Status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local services=("websocket" "frontend")
    local all_running=true
    
    for service in "${services[@]}"; do
        local pidfile="$PID_DIR/dashboard-${service}.pid"
        
        if [[ -f "$pidfile" ]]; then
            local pid=$(cat "$pidfile")
            if kill -0 "$pid" 2>/dev/null; then
                echo -e "🟢 ${service}: Running (PID: $pid)"
            else
                echo -e "🔴 ${service}: Stopped"
                all_running=false
                rm -f "$pidfile"
            fi
        else
            echo -e "🔴 ${service}: Not started"
            all_running=false
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if $all_running; then
        echo -e "${GREEN}✅ All dashboard services are running${NC}"
    else
        echo -e "${YELLOW}⚠️  Some dashboard services are not running${NC}"
    fi
}

# Function to wait for services
wait_for_services() {
    echo -e "\n${BLUE}⏳ Waiting for services to be ready${NC}"
    
    # Wait for WebSocket server
    local websocket_ready=false
    for i in {1..30}; do
        if curl -s http://localhost:3001/health > /dev/null 2>&1; then
            websocket_ready=true
            break
        fi
        sleep 1
        echo -n "."
    done
    
    echo ""
    
    if $websocket_ready; then
        echo -e "${GREEN}✓ WebSocket server is ready${NC}"
    else
        echo -e "${YELLOW}⚠️  WebSocket server may not be ready${NC}"
    fi
    
    # Wait for dashboard
    local dashboard_ready=false
    for i in {1..30}; do
        if curl -s http://localhost:3000 > /dev/null 2>&1; then
            dashboard_ready=true
            break
        fi
        sleep 1
        echo -n "."
    done
    
    echo ""
    
    if $dashboard_ready; then
        echo -e "${GREEN}✓ Dashboard is ready${NC}"
    else
        echo -e "${YELLOW}⚠️  Dashboard may not be ready${NC}"
    fi
}

# Main execution
main() {
    echo -e "\n${YELLOW}⚙️  Configuration:${NC}"
    echo "• Mode: $(if $DEV_MODE; then echo "Development"; else echo "Production"; fi)"
    echo "• Install dependencies: $INSTALL_DEPS"
    echo "• Background mode: $BACKGROUND"
    echo "• Dashboard: http://localhost:3000"
    echo "• WebSocket: http://localhost:3001"
    
    # Check prerequisites
    echo -e "\n${BLUE}🔍 Checking Prerequisites${NC}"
    
    local prereq_failed=false
    
    if ! check_nodejs; then
        echo -e "${RED}❌ Node.js is required but not installed${NC}"
        prereq_failed=true
    fi
    
    if ! check_npm; then
        echo -e "${RED}❌ npm is required but not installed${NC}"
        prereq_failed=true
    fi
    
    if ! check_kafka; then
        echo -e "${YELLOW}⚠️  Kafka is not accessible${NC}"
        echo "Start Kafka with: ./scripts/start-environment.sh"
        echo "Dashboard will work with limited functionality"
    fi
    
    if $prereq_failed; then
        exit 1
    fi
    
    # Install dependencies if requested
    if $INSTALL_DEPS; then
        install_dependencies
    fi
    
    # Create PID directory
    mkdir -p "$PID_DIR"
    mkdir -p "$BASE_DIR/logs"
    
    # Stop existing processes
    stop_existing_processes
    
    if $BACKGROUND; then
        # Start both services in background
        start_websocket_server
        start_dashboard
        
        # Wait for services to be ready
        wait_for_services
        
        # Show status
        show_status
        
        echo -e "\n${GREEN}🎉 Dashboard started successfully!${NC}"
        echo -e "\n${YELLOW}📖 Access URLs:${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "🎨 Dashboard:       http://localhost:3000"
        echo "🔌 WebSocket API:   http://localhost:3001"
        echo "📊 Health Check:    http://localhost:3001/health"
        echo "📈 Metrics:         http://localhost:3001/metrics"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        echo -e "\n${YELLOW}🛠️  Management Commands:${NC}"
        echo "• Check status:     ./scripts/dashboard-status.sh"
        echo "• Stop dashboard:   ./scripts/stop-dashboard.sh"
        echo "• View logs:        tail -f logs/dashboard.log"
        echo "• View WS logs:     tail -f websocket-server/logs/websocket-server.log"
        
    else
        # Interactive mode - start WebSocket server first, then dashboard
        echo -e "\n${YELLOW}Starting in interactive mode...${NC}"
        echo "This will start the WebSocket server first, then the dashboard"
        echo ""
        
        # Start WebSocket server in background for interactive mode
        start_websocket_server &
        local websocket_pid=$!
        
        # Wait a moment, then start dashboard in foreground
        sleep 3
        start_dashboard
    fi
}

# Trap Ctrl+C for clean exit
trap 'echo -e "\n${YELLOW}Stopping dashboard...${NC}"; ./scripts/stop-dashboard.sh 2>/dev/null || true; exit 0' INT

# Run main function
main