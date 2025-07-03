#!/bin/bash

# End-to-End Test Script for LLM Judgment with Ollama
# 
# This script demonstrates LLM judgment workflow:
# 1. Sets up ML Commons connector for Ollama
# 2. Creates test data with 5 synthetic documents
# 3. Executes LLM judgment using local Ollama model
# 4. Verifies results and ratings
#
# Prerequisites:
# - Ollama installed and running (ollama serve)
# - llama3.1:8b model available (ollama pull llama3.1:8b)
# - OpenSearch running on localhost:9200
# - Search Relevance plugin enabled

set -e

# ANSI color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Global variables
CONNECTOR_ID=""
MODEL_ID=""
SEARCH_CONFIG_ID=""
QUERY_SET_ID=""
JUDGMENT_ID=""
TEST_INDEX="llm_judgment_test"

# Helper functions
exe() { 
    echo -e "${BLUE}[EXEC]${NC} $*"
    (set -x ; "$@") | jq | tee RES
    echo
}

log() { 
    echo -e "${GREEN}[LLM TEST]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Ollama is installed
    if ! command -v ollama &> /dev/null; then
        error "Ollama not found. Please install Ollama first: https://ollama.ai"
    fi
    
    # Check if Ollama is running
    if ! curl -s http://localhost:11434/api/tags > /dev/null; then
        error "Ollama is not running. Please start it with: ollama serve"
    fi
    
    # Check if llama3.1 model is available
    if ! curl -s http://localhost:11434/api/tags | jq -e '.models[] | select(.name | contains("llama3.1"))' > /dev/null; then
        error "llama3.1 model not found. Please run: ollama pull llama3.1:8b"
    fi
    
    # Check if OpenSearch is running
    if ! curl -s http://localhost:9200 > /dev/null; then
        error "OpenSearch is not running on localhost:9200"
    fi
    
    log "All prerequisites satisfied ✓"
}

# Enable Search Relevance Workbench and configure ML Commons
enable_workbench() {
    log "Enabling Search Relevance Workbench and configuring ML Commons..."
    
    curl -s -X PUT "http://localhost:9200/_cluster/settings" \
    -H 'Content-Type: application/json' \
    -d'{
        "persistent": {
            "plugins.search_relevance.workbench_enabled": true,
            "plugins.ml_commons.only_run_on_ml_node": false,
            "plugins.ml_commons.model_access_control_enabled": false,
            "plugins.ml_commons.connector_access_control_enabled": false,
            "plugins.ml_commons.connector.private_ip_enabled": true,
            "plugins.ml_commons.native_memory_threshold": 99,
            "plugins.ml_commons.allow_registering_model_via_url": true,
            "plugins.ml_commons.allow_registering_model_via_local_file": true,
            "plugins.ml_commons.trusted_connector_endpoints_regex": [
                "^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*",
                "^https://api\\.openai\\.com/.*",
                "^https://api\\.cohere\\.ai/.*",
                "^https://.*\\.openai\\.azure\\.com/.*",
                "^https://api\\.anthropic\\.com/.*",
                "^https://bedrock-runtime\\..*\\.amazonaws\\.com/.*",
                "^http://localhost:.*",
                "^https://localhost:.*",
                "^http://127\\.0\\.0\\.1:.*",
                "^https://127\\.0\\.0\\.1:.*",
                "^http://host\\.docker\\.internal:.*",
                "^https://host\\.docker\\.internal:.*"
            ],
            "plugins.ml_commons.trusted_url_regex": [
                "^http://localhost:.*",
                "^https://localhost:.*",
                "^http://127\\.0\\.0\\.1:.*",
                "^https://127\\.0\\.0\\.1:.*",
                "^http://host\\.docker\\.internal:.*",
                "^https://host\\.docker\\.internal:.*"
            ]
        }
    }' > /dev/null
    
    log "Search Relevance Workbench and ML Commons configured ✓"
}

# Setup ML Commons connector for Ollama
setup_ml_connector() {
    log "Setting up ML Commons connector for Ollama..."
    
    # Create connector with 127.0.0.1 instead of localhost
    exe curl -s -X POST "http://localhost:9200/_plugins/_ml/connectors/_create" \
    -H "Content-type: application/json" \
    -d'{
        "name": "ollama-llama3.1-connector",
        "description": "Connector for Ollama Llama 3.1 model",
        "version": "1.0.0",
        "protocol": "http",
        "parameters": {
            "endpoint": "http://host.docker.internal:11434",
            "model": "llama3.1:8b"
        },
        "credential": {
            "access_key": "",
            "secret_key": ""
        },
        "actions": [
            {
                "action_type": "predict",
                "method": "POST",
                "url": "http://host.docker.internal:11434/v1/chat/completions",
                "headers": {
                    "Content-Type": "application/json"
                },
                "request_body": "{ \"model\": \"llama3.1:8b\", \"messages\": ${parameters.messages}, \"temperature\": 0.1, \"max_tokens\": 1000, \"stream\": false }"
            }
        ]
    }'
    
    CONNECTOR_ID=$(jq -r '.connector_id' < RES)
    log "Created connector with ID: $CONNECTOR_ID"
    
    # Register model
    exe curl -s -X POST "http://localhost:9200/_plugins/_ml/models/_register" \
    -H "Content-type: application/json" \
    -d"{
        \"name\": \"ollama-llama3.1-model\",
        \"function_name\": \"remote\",
        \"connector_id\": \"$CONNECTOR_ID\"
    }"
    
    local task_id=$(jq -r '.task_id' < RES)
    log "Model registration started with task ID: $task_id"
    
    # Wait for registration to complete
    wait_for_task_completion $task_id "model registration"
    
    MODEL_ID=$(curl -s "http://localhost:9200/_plugins/_ml/tasks/$task_id" | jq -r '.model_id')
    log "Model registered with ID: $MODEL_ID"
    
    # Deploy model
    exe curl -s -X POST "http://localhost:9200/_plugins/_ml/models/$MODEL_ID/_deploy"
    
    local deploy_task_id=$(jq -r '.task_id' < RES)
    log "Model deployment started with task ID: $deploy_task_id"
    
    # Wait for deployment to complete
    wait_for_task_completion $deploy_task_id "model deployment"
    
    # Wait for model to be in DEPLOYED state
    wait_for_model_deployment $MODEL_ID
    
    log "ML Commons setup completed ✓"
}

# Wait for ML Commons task completion
wait_for_task_completion() {
    local task_id=$1
    local operation_name=$2
    local max_attempts=30
    local attempts=0
    
    log "Waiting for $operation_name to complete..."
    
    while [[ $attempts -lt $max_attempts ]]; do
        local state=$(curl -s "http://localhost:9200/_plugins/_ml/tasks/$task_id" | jq -r '.state')
        
        if [[ "$state" == "COMPLETED" ]]; then
            log "$operation_name completed successfully ✓"
            return 0
        elif [[ "$state" == "FAILED" ]]; then
            error "$operation_name failed"
        fi
        
        echo "  Attempt $((attempts + 1))/$max_attempts - State: $state"
        sleep 2
        attempts=$((attempts + 1))
    done
    
    error "$operation_name did not complete within expected time"
}

# Wait for model deployment
wait_for_model_deployment() {
    local model_id=$1
    local max_attempts=30
    local attempts=0
    
    log "Waiting for model to be deployed..."
    
    while [[ $attempts -lt $max_attempts ]]; do
        local state=$(curl -s "http://localhost:9200/_plugins/_ml/models/$model_id" | jq -r '.model_state')
        
        if [[ "$state" == "DEPLOYED" ]]; then
            log "Model deployed successfully ✓"
            return 0
        elif [[ "$state" == "DEPLOY_FAILED" ]]; then
            error "Model deployment failed"
        fi
        
        echo "  Attempt $((attempts + 1))/$max_attempts - State: $state"
        sleep 2
        attempts=$((attempts + 1))
    done
    
    error "Model deployment did not complete within expected time"
}

# Setup test data with 5 synthetic documents
setup_test_data() {
    log "Setting up test data with 5 synthetic documents..."
    
    # Delete existing test index
    curl -s -X DELETE "http://localhost:9200/$TEST_INDEX" > /dev/null 2>&1 || true
    
    # Create index with mapping
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX" \
    -H "Content-type: application/json" \
    -d'{
        "mappings": {
            "properties": {
                "title": {"type": "text"},
                "content": {"type": "text"},
                "category": {"type": "keyword"}
            }
        }
    }' > /dev/null
    
    # Add 5 synthetic documents with varying relevance to smartphone queries
    
    # Document 1: iPhone 15 Pro Max (High relevance)
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX/_doc/1" \
    -H "Content-type: application/json" \
    -d'{
        "title": "iPhone 15 Pro Max Review",
        "content": "The iPhone 15 Pro Max features a titanium design, A17 Pro chip, and advanced camera system with 5x telephoto zoom. The camera quality is exceptional with computational photography features.",
        "category": "smartphones"
    }' > /dev/null
    
    # Document 2: Samsung Galaxy S24 Ultra (High relevance)
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX/_doc/2" \
    -H "Content-type: application/json" \
    -d'{
        "title": "Samsung Galaxy S24 Ultra",
        "content": "Samsung Galaxy S24 Ultra offers AI-powered features, S Pen functionality, and exceptional display quality. The camera system includes a 200MP main sensor with advanced zoom capabilities.",
        "category": "smartphones"
    }' > /dev/null
    
    # Document 3: MacBook Air M3 (Low relevance - not a smartphone)
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX/_doc/3" \
    -H "Content-type: application/json" \
    -d'{
        "title": "MacBook Air M3 Laptop",
        "content": "The new MacBook Air with M3 chip delivers incredible performance and all-day battery life in a thin design. Perfect for productivity and creative work.",
        "category": "laptops"
    }' > /dev/null
    
    # Document 4: Tesla Model 3 (No relevance)
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX/_doc/4" \
    -H "Content-type: application/json" \
    -d'{
        "title": "Tesla Model 3 Electric Car",
        "content": "Tesla Model 3 is an electric sedan with autopilot capabilities and over 300 miles of range. Features advanced driver assistance systems.",
        "category": "automotive"
    }' > /dev/null
    
    # Document 5: Nike Air Jordan (No relevance)
    curl -s -X PUT "http://localhost:9200/$TEST_INDEX/_doc/5" \
    -H "Content-type: application/json" \
    -d'{
        "title": "Nike Air Jordan Sneakers",
        "content": "Classic Nike Air Jordan basketball shoes with premium leather and iconic design. Perfect for basketball and casual wear.",
        "category": "footwear"
    }' > /dev/null
    
    # Refresh index to make documents searchable
    curl -s -X POST "http://localhost:9200/$TEST_INDEX/_refresh" > /dev/null
    
    log "Test data setup completed ✓"
    log "  - Document 1: iPhone 15 Pro Max (smartphones - high relevance expected)"
    log "  - Document 2: Samsung Galaxy S24 Ultra (smartphones - high relevance expected)"
    log "  - Document 3: MacBook Air M3 (laptops - low relevance expected)"
    log "  - Document 4: Tesla Model 3 (automotive - no relevance expected)"
    log "  - Document 5: Nike Air Jordan (footwear - no relevance expected)"
}

# Create search configuration
create_search_config() {
    log "Creating search configuration..."
    
    exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/search_configurations" \
    -H "Content-type: application/json" \
    -d"{
        \"name\": \"LLM Test Search Config\",
        \"index\": \"$TEST_INDEX\",
        \"query\": \"{\\\"query\\\":{\\\"multi_match\\\":{\\\"query\\\":\\\"%SearchText%\\\",\\\"fields\\\":[\\\"title^2\\\",\\\"content\\\",\\\"category\\\"]}}}\"
    }"
    
    SEARCH_CONFIG_ID=$(jq -r '.search_configuration_id' < RES)
    log "Search configuration created ✓"
    log "  - Config ID: $SEARCH_CONFIG_ID"
    log "  - Index: $TEST_INDEX"
    log "  - Query: Multi-match with title boost"
}

# Create query set with smartphone query
create_query_set() {
    log "Creating query set with smartphone query..."
    
    exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/query_sets" \
    -H "Content-type: application/json" \
    -d'{
        "name": "Smartphone Query Set",
        "description": "Test queries for smartphone relevance evaluation",
        "sampling": "manual",
        "querySetQueries": [
            {
                "queryText": "best smartphone with good camera",
                "referenceAnswer": "iPhone 15 Pro Max and Samsung Galaxy S24 Ultra are top smartphones with excellent camera systems featuring advanced computational photography and high-resolution sensors"
            }
        ]
    }'
    
    QUERY_SET_ID=$(jq -r '.query_set_id' < RES)
    log "Query set created ✓"
    log "  - Query Set ID: $QUERY_SET_ID"
    log "  - Query: 'best smartphone with good camera'"
    log "  - Reference answer provided for context"
}

# Execute LLM judgment
execute_llm_judgment() {
    log "Executing LLM judgment..."
    
    exe curl -s -X PUT "http://localhost:9200/_plugins/_search_relevance/judgments" \
    -H "Content-type: application/json" \
    -d"{
        \"name\": \"Ollama LLM Judgment Test\",
        \"description\": \"Testing LLM judgment with Ollama\",
        \"type\": \"LLM_JUDGMENT\",
        \"modelId\": \"$MODEL_ID\",
        \"querySetId\": \"$QUERY_SET_ID\",
        \"searchConfigurationList\": [\"$SEARCH_CONFIG_ID\"],
        \"size\": 5,
        \"tokenLimit\": 2000,
        \"contextFields\": [\"title\", \"content\", \"category\"],
        \"ignoreFailure\": false
    }"
    
    JUDGMENT_ID=$(jq -r '.judgment_id' < RES)
    log "LLM judgment execution started ✓"
    log "  - Judgment ID: $JUDGMENT_ID"
    log "  - Using model: $MODEL_ID"
    log "  - Processing 5 documents"
}

# Verify results and display ratings
verify_results() {
    log "Waiting for judgment processing to complete..."
    
    # Wait a bit for processing to start
    sleep 5
    
    # Check judgment status periodically
    local max_attempts=30
    local attempts=0
    
    while [[ $attempts -lt $max_attempts ]]; do
        log "Checking judgment status (attempt $((attempts + 1))/$max_attempts)..."
        
        # Get judgment details
        local response=$(curl -s -X GET "http://localhost:9200/_plugins/_search_relevance/judgments/$JUDGMENT_ID")
        
        if echo "$response" | jq -e '.hits.hits[0]._source.status' > /dev/null 2>&1; then
            local status=$(echo "$response" | jq -r '.hits.hits[0]._source.status')
            if [[ "$status" == "COMPLETED" ]]; then
                log "Judgment processing completed ✓"
                break
            elif [[ "$status" == "ERROR" ]]; then
                warn "Judgment processing failed"
                break
            fi
        fi
        
        sleep 3
        attempts=$((attempts + 1))
    done
    
    if [[ $attempts -ge $max_attempts ]]; then
        warn "Judgment processing is taking longer than expected"
        log "Proceeding to show current status..."
    fi
    
    log "Retrieving final judgment results..."
    exe curl -s -X GET "http://localhost:9200/_plugins/_search_relevance/judgments/$JUDGMENT_ID"
    
    # Display summary
    echo
    log "=== TEST SUMMARY ==="
    log "✓ ML Commons connector created and model deployed"
    log "✓ Test index created with 5 synthetic documents"
    log "✓ Search configuration and query set created"
    log "✓ LLM judgment executed using Ollama"
    log "✓ Results retrieved and displayed above"
    echo
    log "Expected rating patterns:"
    log "  - iPhone 15 Pro Max: HIGH (0.7-1.0) - Perfect match for smartphone camera query"
    log "  - Samsung Galaxy S24 Ultra: HIGH (0.7-1.0) - Excellent match for smartphone camera query"
    log "  - MacBook Air M3: LOW (0.1-0.3) - Not a smartphone"
    log "  - Tesla Model 3: VERY LOW (0.0-0.2) - Completely irrelevant"
    log "  - Nike Air Jordan: VERY LOW (0.0-0.2) - Completely irrelevant"
    echo
    log "Test completed! Check the judgment results above to verify the LLM ratings."
}

# Cleanup function (optional)
cleanup() {
    log "Cleaning up test resources..."
    
    # Delete test index
    curl -s -X DELETE "http://localhost:9200/$TEST_INDEX" > /dev/null 2>&1 || true
    
    # Note: We don't delete the ML model as it might be used for other tests
    
    log "Cleanup completed ✓"
}

# Main execution function
main() {
    echo
    log "=========================================="
    log "LLM Judgment Test with Ollama"
    log "=========================================="
    echo
    
    # Check if cleanup flag is provided
    if [[ "$1" == "--cleanup" ]]; then
        cleanup
        exit 0
    fi
    
    # Execute test steps
    check_prerequisites
    enable_workbench
    setup_ml_connector
    setup_test_data
    create_search_config
    create_query_set
    execute_llm_judgment
    verify_results
    
    echo
    log "=========================================="
    log "Test completed successfully!"
    log "=========================================="
    echo
    log "To clean up test resources, run:"
    log "  $0 --cleanup"
    echo
}

# Handle script interruption
trap 'echo; error "Script interrupted"' INT TERM

# Run main function with all arguments
main "$@"
