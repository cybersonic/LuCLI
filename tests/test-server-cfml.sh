#!/bin/bash

# LuCLI Server CFML Test Script
# Tests server startup and .cfs/.cfm file execution via HTTP
# This script is called from the main test.sh

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Test configuration
# Get absolute path to JAR file to avoid path issues when called from different directories
if [ -z "$LUCLI_JAR" ]; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    LUCLI_JAR="$SCRIPT_DIR/../target/lucli.jar"
fi
TEST_SERVER_NAME="test-server-cfml-$(date +%s)"
TEST_PORT=8888
WAIT_TIMEOUT=30
TEST_DIR_NAME="server_test_$(date +%s)"

# Optional: filter tests by name (substring match, case-insensitive)
TEST_FILTER="${TEST_FILTER:-}"

echo -e "${BLUE}🌐 LuCLI Server CFML Test Suite${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Function to wait for server to be ready
wait_for_server() {
    local port=$1
    local timeout=$2
    local count=0
    
    echo -e "${CYAN}⏳ Waiting for server to start on port $port...${NC}"
    
    while [ $count -lt $timeout ]; do
        if curl -s "http://localhost:$port" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Server is ready!${NC}"
            return 0
        fi
        sleep 1
        count=$((count + 1))
        echo -n "."
    done
    
    echo -e "${RED}❌ Server failed to start within $timeout seconds${NC}"
    return 1
}

# Function to test HTTP endpoint
test_http_endpoint() {
    local name="$1"
    local url="$2"
    local expected_pattern="$3"
    local expected_status="${4:-200}"
    
    echo -e "${CYAN}Testing: $name${NC}"
    echo -e "${YELLOW}URL: $url${NC}"
    
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$url" 2>/dev/null || echo "HTTPSTATUS:000")
    local body=$(echo "$response" | sed -E 's/HTTPSTATUS:[0-9]{3}$//')
    local status=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    
    if [ "$status" = "$expected_status" ] && echo "$body" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✅ PASSED${NC}"
        echo -e "${PURPLE}Status: $status, Response: $(echo "$body" | head -c 100)...${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo -e "${RED}Expected status: $expected_status, Got: $status${NC}"
        echo -e "${RED}Expected pattern: $expected_pattern${NC}"
        echo -e "${RED}Response body: $body${NC}"
        return 1
    fi
    echo ""
}

# Helper to run an HTTP test with filtering and counters
run_server_http_test() {
    local name="$1"
    local url="$2"
    local expected_pattern="$3"
    local expected_status="${4:-200}"

    # If TEST_FILTER is set and this test name doesn't match, skip it
    if [[ -n "$TEST_FILTER" ]] && ! echo "$name" | grep -iq -- "$TEST_FILTER"; then
        echo -e "${BLUE}↷ Skipping: ${name} (does not match filter '${TEST_FILTER}')${NC}"
        return
    fi

    if test_http_endpoint "$name" "$url" "$expected_pattern" "$expected_status"; then
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Function to cleanup server
cleanup_server() {
    echo -e "${YELLOW}🧹 Cleaning up test server...${NC}"
    
    # Stop the test server
    java -jar "$LUCLI_JAR" server stop --name "$TEST_SERVER_NAME" > /dev/null 2>&1 || true
    
    # Wait a moment for server to fully stop
    sleep 2
    
    # Clean up test directory
    if [ -d "$TEST_DIR_NAME" ]; then
        rm -rf "$TEST_DIR_NAME"
    fi
    
    echo -e "${GREEN}✅ Cleanup completed${NC}"
}

# Trap to ensure cleanup happens on exit
trap cleanup_server EXIT

# Create test directory and files
echo -e "${CYAN}📁 Creating test directory and CFML files...${NC}"
mkdir -p "$TEST_DIR_NAME"
cd "$TEST_DIR_NAME"

# Create test .cfs file
cat > test.cfs << 'EOF'
// Test CFS script
writeOutput("CFS Test Response");
writeOutput(chr(10));
writeOutput("Timestamp: " & now());
writeOutput(chr(10));
writeOutput("Server Info: " & server.coldfusion.productname & " " & server.coldfusion.productversion);
EOF

# Create test .cfm file
cat > test.cfm << 'EOF'
<cfoutput>
CFM Test Response
Timestamp: #now()#
Server Info: #server.coldfusion.productname# #server.coldfusion.productversion#
Request Method: #cgi.request_method#
</cfoutput>
EOF

# Create index.cfm for root access
cat > index.cfm << 'EOF'
<cfoutput>
<h1>LuCLI Test Server</h1>
<p>Server is running successfully!</p>
<p>Timestamp: #now()#</p>
<p>Server Info: #server.coldfusion.productname# #server.coldfusion.productversion#</p>
<ul>
    <li><a href="/test.cfs">Test CFS File</a></li>
    <li><a href="/test.cfm">Test CFM File</a></li>
</ul>
</cfoutput>
EOF

# Create an API endpoint test
cat > api.cfs << 'EOF'
// API endpoint test
content = {
    "status": "success",
    "timestamp": now(),
    "server": server.coldfusion.productname,
    "message": "API endpoint working"
};

writeOutput(serializeJSON(content));
EOF

# Create lucee.json configuration
cat > lucee.json << EOF
{
    "name": "$TEST_SERVER_NAME",
    "port": $TEST_PORT,
    "version": "6.2.2.91",
    "webroot": "./",
    "jvm": {
        "maxMemory": "512m",
        "minMemory": "128m"
    },
    "monitoring": {
        "enabled": true,
        "jmx": {
            "port": 8999
        }
    }
}
EOF

echo -e "${GREEN}✅ Test files created${NC}"

# Start the server
echo -e "${CYAN}🚀 Starting LuCLI server...${NC}"
echo -e "${YELLOW}Command: java -jar $LUCLI_JAR server start --name $TEST_SERVER_NAME --port $TEST_PORT${NC}"

# Start server in background
java -jar "$LUCLI_JAR" server start --name "$TEST_SERVER_NAME" --port "$TEST_PORT" --open-browser false > server_start.log 2>&1 &
SERVER_PID=$!

# Wait for server to start
if wait_for_server $TEST_PORT $WAIT_TIMEOUT; then
    echo -e "${GREEN}✅ Server started successfully${NC}"
    
    # Test server endpoints
    echo -e "${BLUE}🧪 Testing server endpoints...${NC}"
    echo ""
    
    TESTS_PASSED=0
    TESTS_FAILED=0
    
    # Test 1: Root index.cfm
    run_server_http_test "Root index.cfm access" "http://localhost:$TEST_PORT/" "LuCLI Test Server"
    
    # Test 2: Direct .cfs file access
    run_server_http_test "Direct .cfs file access" "http://localhost:$TEST_PORT/test.cfs" "CFS Test Response"
    
    # Test 3: Direct .cfm file access
    run_server_http_test "Direct .cfm file access" "http://localhost:$TEST_PORT/test.cfm" "CFM Test Response"
    
    # Test 4: API endpoint (.cfs)
    run_server_http_test "API endpoint (.cfs)" "http://localhost:$TEST_PORT/api.cfs" "success"
    
    # Test 5: Server status via LuCLI
    if [[ -z "$TEST_FILTER" ]] || echo "Server status command" | grep -iq -- "$TEST_FILTER"; then
        echo -e "${CYAN}Testing: Server status command${NC}"
        if java -jar "$LUCLI_JAR" server status --name "$TEST_SERVER_NAME" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ PASSED - Server status command works${NC}"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            echo -e "${RED}❌ FAILED - Server status command failed${NC}"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        echo -e "${BLUE}↷ Skipping: Server status command (does not match filter '${TEST_FILTER}')${NC}"
    fi
    
    # Test 6: Server list includes our server
    if [[ -z "$TEST_FILTER" ]] || echo "Server appears in list" | grep -iq -- "$TEST_FILTER"; then
        echo -e "${CYAN}Testing: Server appears in list${NC}"
        if java -jar "$LUCLI_JAR" server list | grep -q "$TEST_SERVER_NAME"; then
            echo -e "${GREEN}✅ PASSED - Server appears in list${NC}"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        else
            echo -e "${RED}❌ FAILED - Server not found in list${NC}"
            TESTS_FAILED=$((TESTS_FAILED + 1))
        fi
    else
        echo -e "${BLUE}↷ Skipping: Server appears in list (does not match filter '${TEST_FILTER}')${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}📊 Server Test Results${NC}"
    echo -e "${BLUE}=====================${NC}"
    echo -e "Tests passed: $TESTS_PASSED"
    echo -e "Tests failed: $TESTS_FAILED"
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}🎉 All server tests passed!${NC}"
        echo -e "${GREEN}✅ .cfs file execution via HTTP: ✓${NC}"
        echo -e "${GREEN}✅ .cfm file execution via HTTP: ✓${NC}"
        echo -e "${GREEN}✅ Server management commands: ✓${NC}"
        echo -e "${GREEN}✅ API endpoint functionality: ✓${NC}"
        exit 0
    else
        echo -e "${RED}⚠️ Some server tests failed${NC}"
        echo -e "${YELLOW}💡 Server logs:${NC}"
        [ -f server_start.log ] && cat server_start.log
        exit 1
    fi
    
else
    echo -e "${RED}❌ Failed to start server${NC}"
    echo -e "${YELLOW}💡 Server start logs:${NC}"
    [ -f server_start.log ] && cat server_start.log
    exit 1
fi