#!/bin/bash

# Stop Dashboard and WebSocket Server for Kafka-Flink POC
# Gracefully terminates both React frontend and Node.js WebSocket server

set -e

echo "🛑 Stopping Kafka-Flink Dashboard"
echo "================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$BASE_DIR/.pids"

# Function to stop a specific service
stop_service() {
    local service_name=$1
    local pidfile="$PID_DIR/dashboard-${service_name}.pid"
    
    if [[ ! -f "$pidfile" ]]; then
        echo -e "⚪ $service_name: No PID file found"
        return 0
    fi
    
    local pid=$(cat "$pidfile")
    
    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "⚪ $service_name: Not running (stale PID file)"
        rm -f "$pidfile"
        return 0
    fi
    
    echo -n "Stopping $service_name (PID: $pid)... "
    
    # Send SIGTERM for graceful shutdown
    if kill -TERM "$pid" 2>/dev/null; then
        # Wait up to 10 seconds for graceful shutdown
        local count=0
        while kill -0 "$pid" 2>/dev/null && [[ $count -lt 10 ]]; do
            sleep 1
            ((count++))
        done
        
        # Check if process is still running
        if kill -0 "$pid" 2>/dev/null; then
            echo -n "force killing... "
            kill -KILL "$pid" 2>/dev/null || true
            sleep 1
        fi
        
        # Final check
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "${RED}✗ Failed to stop${NC}"
            return 1
        else
            echo -e "${GREEN}✓ Stopped${NC}"
            rm -f "$pidfile"
            return 0
        fi
    else
        echo -e "${RED}✗ Failed to send signal${NC}"
        return 1
    fi
}

# Function to show initial status
show_initial_status() {
    echo -e "\n${BLUE}📊 Current Dashboard Status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local running_count=0
    local services=("websocket" "frontend")
    
    for service in "${services[@]}"; do
        local pidfile="$PID_DIR/dashboard-${service}.pid"
        
        if [[ -f "$pidfile" ]]; then
            local pid=$(cat "$pidfile")
            if kill -0 "$pid" 2>/dev/null; then
                echo -e "🟢 $service: Running (PID: $pid)"
                ((running_count++))
            else
                echo -e "🔴 $service: Stopped (stale PID file)"
                rm -f "$pidfile"
            fi
        else
            echo -e "⚪ $service: Not started"
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [[ $running_count -eq 0 ]]; then
        echo -e "${YELLOW}ℹ️  No dashboard services are currently running${NC}"
        return 1
    else
        echo -e "${YELLOW}ℹ️  Found $running_count running service(s)${NC}"
        return 0
    fi
}

# Function to stop all dashboard services
stop_all_services() {
    echo -e "\n${BLUE}🛑 Stopping Dashboard Services${NC}"
    
    local failed_count=0
    local stopped_count=0
    local services=("websocket" "frontend")
    
    for service in "${services[@]}"; do
        if stop_service "$service"; then
            ((stopped_count++))
        else
            ((failed_count++))
        fi
    done
    
    echo -e "\n${BLUE}📊 Stop Summary${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "• Stopped: $stopped_count"
    echo "• Failed: $failed_count"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    return $failed_count
}

# Function to clean up orphaned processes
cleanup_orphaned_processes() {
    echo -e "\n${BLUE}🧹 Cleaning up orphaned processes${NC}"
    
    # Look for Node.js processes that might be our dashboard services
    local orphaned_pids=$(pgrep -f "websocket-server\|vite\|node.*dashboard" 2>/dev/null || true)
    
    if [[ -n "$orphaned_pids" ]]; then
        echo "Found potentially orphaned dashboard processes:"
        for pid in $orphaned_pids; do
            local cmd=$(ps -p "$pid" -o command= 2>/dev/null || echo "unknown")
            echo "• PID $pid: $cmd"
        done
        
        echo -e "\n${YELLOW}Do you want to terminate these processes? (y/n)${NC}"
        read -r response
        if [[ "$response" =~ ^[Yy]$ ]]; then
            for pid in $orphaned_pids; do
                echo -n "Killing PID $pid... "
                if kill -TERM "$pid" 2>/dev/null; then
                    sleep 2
                    if kill -0 "$pid" 2>/dev/null; then
                        kill -KILL "$pid" 2>/dev/null || true
                    fi
                    echo -e "${GREEN}✓ Killed${NC}"
                else
                    echo -e "${RED}✗ Failed${NC}"
                fi
            done
        fi
    else
        echo "No orphaned dashboard processes found"
    fi
}

# Function to show final status
show_final_status() {
    echo -e "\n${BLUE}📊 Final Status${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local still_running=false
    local services=("websocket" "frontend")
    
    for service in "${services[@]}"; do
        local pidfile="$PID_DIR/dashboard-${service}.pid"
        
        if [[ -f "$pidfile" ]]; then
            local pid=$(cat "$pidfile")
            if kill -0 "$pid" 2>/dev/null; then
                echo -e "🔴 $service: Still running (PID: $pid)"
                still_running=true
            else
                echo -e "🟢 $service: Stopped"
                rm -f "$pidfile"
            fi
        else
            echo -e "🟢 $service: Stopped"
        fi
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if $still_running; then
        echo -e "${RED}⚠️  Some dashboard services are still running${NC}"
        return 1
    else
        echo -e "${GREEN}✅ All dashboard services stopped successfully${NC}"
        return 0
    fi
}

# Function to clean up PID directory
cleanup_pid_dir() {
    if [[ -d "$PID_DIR" ]]; then
        # Remove any remaining dashboard PID files
        rm -f "$PID_DIR"/dashboard-*.pid
        
        # Remove PID directory if empty
        if [[ -z "$(ls -A "$PID_DIR" 2>/dev/null)" ]]; then
            rmdir "$PID_DIR" 2>/dev/null || true
        fi
    fi
}

# Main execution
main() {
    # Show current status
    if ! show_initial_status; then
        echo -e "\n${GREEN}✅ No dashboard services to stop${NC}"
        exit 0
    fi
    
    # Stop all services
    if stop_all_services; then
        echo -e "\n${GREEN}🎉 All dashboard services stopped successfully!${NC}"
    else
        echo -e "\n${YELLOW}⚠️  Some services failed to stop gracefully${NC}"
        
        # Offer to clean up orphaned processes
        cleanup_orphaned_processes
    fi
    
    # Show final status
    show_final_status
    
    # Clean up PID directory
    cleanup_pid_dir
    
    echo -e "\n${YELLOW}📖 Useful Commands:${NC}"
    echo "• Start dashboard again: ./scripts/start-dashboard.sh"
    echo "• Start with dependencies: ./scripts/start-dashboard.sh --install"
    echo "• Check main services: ./scripts/health-check.sh"
    echo "• View logs: ls -la logs/ websocket-server/logs/"
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Stop the Kafka-Flink dashboard and WebSocket server."
        echo ""
        echo "Options:"
        echo "  --help, -h    Show this help message"
        echo ""
        echo "Examples:"
        echo "  $0                    # Stop all dashboard services"
        echo "  $0 --help           # Show help"
        exit 0
        ;;
    "")
        # No arguments, proceed normally
        ;;
    *)
        echo "Unknown option: $1"
        echo "Use --help for usage information"
        exit 1
        ;;
esac

# Run main function
main