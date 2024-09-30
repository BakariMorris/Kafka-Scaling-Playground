#!/bin/bash

# Build and Deploy Flink Jobs for Kafka-Flink POC
# Compiles Java jobs and deploys them to the Flink cluster

set -e

echo "🔨 Building and Deploying Flink Jobs"
echo "===================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLINK_JOBS_DIR="$BASE_DIR/flink-jobs"
FLINK_URL="http://localhost:8081"

# Parse command line arguments
DEPLOY_JOBS=true
CLEAN_BUILD=false
JOBS_TO_BUILD=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-deploy)
            DEPLOY_JOBS=false
            shift
            ;;
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --job)
            JOBS_TO_BUILD="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --no-deploy           Build only, don't deploy to Flink cluster"
            echo "  --clean              Clean build (mvn clean)"
            echo "  --job JOB_NAME       Build specific job only"
            echo "  --help               Show this help message"
            echo ""
            echo "Available jobs:"
            echo "  UserEventAggregationJob"
            echo "  ElasticsearchSinkJob"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to check if Flink is accessible
check_flink() {
    echo -n "Checking Flink cluster connectivity... "
    if curl -s "$FLINK_URL/overview" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Connected${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

# Function to check Maven installation
check_maven() {
    echo -n "Checking Maven installation... "
    if command -v mvn >/dev/null 2>&1; then
        local maven_version=$(mvn -version | head -1 | awk '{print $3}')
        echo -e "${GREEN}✓ Found (${maven_version})${NC}"
        return 0
    else
        echo -e "${RED}✗ Not found${NC}"
        return 1
    fi
}

# Function to check Java installation
check_java() {
    echo -n "Checking Java installation... "
    if command -v java >/dev/null 2>&1; then
        local java_version=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
        echo -e "${GREEN}✓ Found (${java_version})${NC}"
        return 0
    else
        echo -e "${RED}✗ Not found${NC}"
        return 1
    fi
}

# Function to build Flink jobs
build_jobs() {
    echo -e "\n${BLUE}🔨 Building Flink Jobs${NC}"
    
    cd "$FLINK_JOBS_DIR"
    
    # Clean if requested
    if $CLEAN_BUILD; then
        echo "Performing clean build..."
        mvn clean
    fi
    
    echo "Compiling and packaging jobs..."
    if mvn package -DskipTests; then
        echo -e "${GREEN}✓ Build successful${NC}"
        return 0
    else
        echo -e "${RED}✗ Build failed${NC}"
        return 1
    fi
}

# Function to list built JAR files
list_built_jars() {
    echo -e "\n${BLUE}📦 Built JAR Files${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local target_dir="$FLINK_JOBS_DIR/target"
    if [[ -d "$target_dir" ]]; then
        for jar in "$target_dir"/*.jar; do
            if [[ -f "$jar" && ! "$jar" == *"original-"* ]]; then
                local size=$(du -h "$jar" | cut -f1)
                local name=$(basename "$jar")
                echo "📄 $name ($size)"
            fi
        done
    else
        echo "No target directory found"
        return 1
    fi
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to get Flink cluster info
get_flink_info() {
    echo -e "\n${BLUE}🎯 Flink Cluster Information${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local overview=$(curl -s "$FLINK_URL/overview" 2>/dev/null)
    if [[ $? -eq 0 && -n "$overview" ]]; then
        if command -v jq >/dev/null 2>&1; then
            echo "Flink Version: $(echo "$overview" | jq -r '.["flink-version"] // "unknown"')"
            echo "TaskManagers: $(echo "$overview" | jq -r '.taskmanagers // "unknown"')"
            echo "Total Slots: $(echo "$overview" | jq -r '.["slots-total"] // "unknown"')"
            echo "Available Slots: $(echo "$overview" | jq -r '.["slots-available"] // "unknown"')"
        else
            echo "Flink cluster is accessible (install jq for detailed info)"
        fi
    else
        echo "Unable to get cluster information"
    fi
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to list running jobs
list_running_jobs() {
    echo -e "\n${BLUE}📋 Currently Running Jobs${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    local jobs=$(curl -s "$FLINK_URL/jobs" 2>/dev/null)
    if [[ $? -eq 0 && -n "$jobs" ]]; then
        if command -v jq >/dev/null 2>&1; then
            local running_jobs=$(echo "$jobs" | jq -r '.jobs[] | select(.state == "RUNNING") | "\(.name) (\(.id))"')
            if [[ -n "$running_jobs" ]]; then
                echo "$running_jobs"
            else
                echo "No running jobs"
            fi
        else
            echo "Jobs endpoint accessible (install jq for job details)"
        fi
    else
        echo "Unable to get job information"
    fi
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to deploy a job to Flink
deploy_job() {
    local jar_file=$1
    local job_name=$2
    
    echo -n "Deploying $job_name... "
    
    # Copy JAR to Flink container
    if docker cp "$jar_file" kafka-poc-flink-jobmanager:/opt/flink/jobs/; then
        local jar_name=$(basename "$jar_file")
        
        # Submit job
        if docker exec kafka-poc-flink-jobmanager flink run "/opt/flink/jobs/$jar_name" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Deployed${NC}"
            return 0
        else
            echo -e "${RED}✗ Deployment failed${NC}"
            return 1
        fi
    else
        echo -e "${RED}✗ Failed to copy JAR${NC}"
        return 1
    fi
}

# Function to deploy all jobs
deploy_jobs() {
    if ! $DEPLOY_JOBS; then
        echo -e "\n${YELLOW}⏭️  Skipping deployment (--no-deploy specified)${NC}"
        return 0
    fi
    
    echo -e "\n${BLUE}🚀 Deploying Jobs to Flink Cluster${NC}"
    
    local target_dir="$FLINK_JOBS_DIR/target"
    local main_jar=""
    
    # Find the main JAR file (not original-)
    for jar in "$target_dir"/*.jar; do
        if [[ -f "$jar" && ! "$jar" == *"original-"* ]]; then
            main_jar="$jar"
            break
        fi
    done
    
    if [[ -z "$main_jar" ]]; then
        echo -e "${RED}❌ No deployable JAR found${NC}"
        return 1
    fi
    
    echo "Found JAR: $(basename "$main_jar")"
    
    # Deploy the main JAR (contains all job classes)
    if deploy_job "$main_jar" "Kafka-Flink Jobs"; then
        echo -e "\n${GREEN}✅ Jobs deployed successfully${NC}"
        
        # Wait a moment for jobs to register
        sleep 3
        list_running_jobs
        
        return 0
    else
        echo -e "\n${RED}❌ Deployment failed${NC}"
        return 1
    fi
}

# Function to show deployment instructions
show_manual_deployment() {
    echo -e "\n${YELLOW}📖 Manual Deployment Instructions${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "If automatic deployment failed, you can deploy manually:"
    echo ""
    echo "1. Copy JAR to Flink container:"
    echo "   docker cp flink-jobs/target/kafka-flink-jobs-1.0-SNAPSHOT.jar kafka-poc-flink-jobmanager:/opt/flink/jobs/"
    echo ""
    echo "2. Deploy UserEventAggregationJob:"
    echo "   docker exec kafka-poc-flink-jobmanager flink run -c com.poc.UserEventAggregationJob /opt/flink/jobs/kafka-flink-jobs-1.0-SNAPSHOT.jar"
    echo ""
    echo "3. Deploy ElasticsearchSinkJob:"
    echo "   docker exec kafka-poc-flink-jobmanager flink run -c com.poc.ElasticsearchSinkJob /opt/flink/jobs/kafka-flink-jobs-1.0-SNAPSHOT.jar"
    echo ""
    echo "4. Check job status:"
    echo "   docker exec kafka-poc-flink-jobmanager flink list"
    echo "   curl $FLINK_URL/jobs"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Main execution
main() {
    echo -e "\n${YELLOW}⚙️  Configuration:${NC}"
    echo "• Build directory: $FLINK_JOBS_DIR"
    echo "• Flink URL: $FLINK_URL"
    echo "• Deploy jobs: $DEPLOY_JOBS"
    echo "• Clean build: $CLEAN_BUILD"
    
    # Check prerequisites
    echo -e "\n${BLUE}🔍 Checking Prerequisites${NC}"
    
    local prereq_failed=false
    
    if ! check_java; then
        echo -e "${RED}❌ Java is required but not installed${NC}"
        prereq_failed=true
    fi
    
    if ! check_maven; then
        echo -e "${RED}❌ Maven is required but not installed${NC}"
        prereq_failed=true
    fi
    
    if $DEPLOY_JOBS && ! check_flink; then
        echo -e "${RED}❌ Flink cluster is not accessible${NC}"
        echo "Start the cluster with: ./scripts/start-environment.sh"
        prereq_failed=true
    fi
    
    if $prereq_failed; then
        exit 1
    fi
    
    # Show Flink cluster info if deploying
    if $DEPLOY_JOBS; then
        get_flink_info
        list_running_jobs
    fi
    
    # Build jobs
    if ! build_jobs; then
        echo -e "\n${RED}❌ Build failed${NC}"
        exit 1
    fi
    
    # List built JARs
    list_built_jars
    
    # Deploy jobs
    if $DEPLOY_JOBS; then
        if ! deploy_jobs; then
            show_manual_deployment
            exit 1
        fi
    else
        echo -e "\n${GREEN}✅ Build completed successfully${NC}"
        show_manual_deployment
    fi
    
    echo -e "\n${YELLOW}🔗 Useful Links:${NC}"
    echo "• Flink Dashboard: $FLINK_URL"
    echo "• Job Management: $FLINK_URL/#/job/running"
    echo "• TaskManager Status: $FLINK_URL/#/taskmanager"
}

# Run main function
main