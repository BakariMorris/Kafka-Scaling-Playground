#!/bin/bash

# Kafka-Flink POC Test Runner
# This script runs different types of tests for the system

set -e

echo "=== Kafka-Flink POC Test Suite ==="

# Default values
TEST_TYPE="all"
CLEANUP=true
BUILD_IMAGES=false
VERBOSE=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --type)
            TEST_TYPE="$2"
            shift 2
            ;;
        --no-cleanup)
            CLEANUP=false
            shift
            ;;
        --build-images)
            BUILD_IMAGES=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --type TYPE       Test type: unit, integration, e2e, all (default: all)"
            echo "  --no-cleanup      Don't cleanup test environment after tests"
            echo "  --build-images    Build Docker images before running tests"
            echo "  --verbose         Verbose output"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Set verbose flag for commands
if [ "$VERBOSE" = true ]; then
    set -x
fi

echo "Test configuration:"
echo "  Test type: $TEST_TYPE"
echo "  Cleanup after tests: $CLEANUP"
echo "  Build images: $BUILD_IMAGES"
echo "  Verbose: $VERBOSE"
echo

# Function to cleanup test environment
cleanup_test_env() {
    echo "Cleaning up test environment..."
    
    # Stop test containers
    docker-compose -f docker-compose.test.yml down -v 2>/dev/null || true
    
    # Remove test containers if they exist
    docker rm -f $(docker ps -aq --filter "name=test-") 2>/dev/null || true
    
    # Remove test networks
    docker network rm $(docker network ls -q --filter "name=test") 2>/dev/null || true
    
    # Remove test volumes
    docker volume rm $(docker volume ls -q --filter "name=test") 2>/dev/null || true
    
    echo "Test environment cleaned up."
}

# Function to setup test environment
setup_test_env() {
    echo "Setting up test environment..."
    
    # Cleanup any existing test environment
    cleanup_test_env
    
    # Build images if requested
    if [ "$BUILD_IMAGES" = true ]; then
        echo "Building test images..."
        docker-compose -f docker-compose.test.yml build
    fi
    
    # Start test infrastructure
    echo "Starting test infrastructure..."
    docker-compose -f docker-compose.test.yml up -d zookeeper-test kafka-test elasticsearch-test flink-jobmanager-test flink-taskmanager-test
    
    # Wait for services to be ready
    echo "Waiting for services to be ready..."
    
    # Wait for Kafka
    echo "Waiting for Kafka..."
    for i in {1..30}; do
        if docker exec test-kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
            echo "Kafka is ready"
            break
        fi
        echo "Waiting for Kafka... ($i/30)"
        sleep 2
    done
    
    # Wait for Elasticsearch
    echo "Waiting for Elasticsearch..."
    for i in {1..30}; do
        if curl -s http://localhost:9201/_cluster/health >/dev/null 2>&1; then
            echo "Elasticsearch is ready"
            break
        fi
        echo "Waiting for Elasticsearch... ($i/30)"
        sleep 2
    done
    
    # Wait for Flink
    echo "Waiting for Flink..."
    for i in {1..30}; do
        if curl -s http://localhost:8082/overview >/dev/null 2>&1; then
            echo "Flink is ready"
            break
        fi
        echo "Waiting for Flink... ($i/30)"
        sleep 2
    done
    
    echo "Test environment is ready!"
}

# Function to run unit tests
run_unit_tests() {
    echo "=== Running Unit Tests ==="
    
    if [ -d "unit" ]; then
        echo "Running unit tests with pytest..."
        python -m pytest unit/ -v --tb=short
    else
        echo "No unit tests found, skipping..."
    fi
}

# Function to run integration tests
run_integration_tests() {
    echo "=== Running Integration Tests ==="
    
    # Start test environment
    setup_test_env
    
    echo "Running integration tests..."
    
    # Run integration tests in container
    docker-compose -f docker-compose.test.yml run --rm integration-tests python -m pytest /app/integration/ -v --tb=short
    
    echo "Integration tests completed."
}

# Function to run end-to-end tests
run_e2e_tests() {
    echo "=== Running End-to-End Tests ==="
    
    # Start test environment (if not already running)
    if ! docker ps | grep -q "test-kafka"; then
        setup_test_env
    fi
    
    # Start additional services needed for E2E tests
    echo "Starting additional services for E2E tests..."
    docker-compose -f docker-compose.test.yml up -d test-data-generator
    
    echo "Running end-to-end tests..."
    
    # Run E2E tests with longer timeout
    docker-compose -f docker-compose.test.yml run --rm integration-tests python -m pytest /app/e2e/ -v --tb=short -m "e2e" --timeout=300
    
    echo "End-to-end tests completed."
}

# Function to run performance tests
run_performance_tests() {
    echo "=== Running Performance Tests ==="
    
    # Start test environment (if not already running)
    if ! docker ps | grep -q "test-kafka"; then
        setup_test_env
    fi
    
    echo "Running performance tests..."
    
    # Run performance-specific tests
    docker-compose -f docker-compose.test.yml run --rm integration-tests python -m pytest /app/ -v --tb=short -m "performance" --timeout=600
    
    echo "Performance tests completed."
}

# Function to generate test report
generate_test_report() {
    echo "=== Generating Test Report ==="
    
    # Create reports directory
    mkdir -p reports
    
    # Run tests with coverage and HTML report
    docker-compose -f docker-compose.test.yml run --rm integration-tests bash -c "
        python -m pytest /app/ --html=/app/reports/test-report.html --self-contained-html --cov=/app --cov-report=html --cov-report=term-missing
    "
    
    echo "Test report generated in reports/"
}

# Trap to cleanup on exit
if [ "$CLEANUP" = true ]; then
    trap cleanup_test_env EXIT
fi

# Main test execution
case $TEST_TYPE in
    "unit")
        run_unit_tests
        ;;
    "integration")
        run_integration_tests
        ;;
    "e2e")
        run_e2e_tests
        ;;
    "performance")
        run_performance_tests
        ;;
    "all")
        echo "Running complete test suite..."
        run_unit_tests
        run_integration_tests
        run_e2e_tests
        run_performance_tests
        generate_test_report
        ;;
    *)
        echo "Unknown test type: $TEST_TYPE"
        echo "Available types: unit, integration, e2e, performance, all"
        exit 1
        ;;
esac

echo
echo "=== Test Suite Completed ==="

if [ "$CLEANUP" = false ]; then
    echo "Test environment is still running. Use 'docker-compose -f docker-compose.test.yml down -v' to cleanup."
fi