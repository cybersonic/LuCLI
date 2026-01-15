#!/bin/bash

# URL Rewrite Integration Test for LuCLI Test Suite
# Tests framework-style URL routing functionality as part of the main test suite

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
TEST_SERVER_NAME="urlrewrite-integration-$(date +%s)"
TEST_PORT=$((8800 + RANDOM % 100))  # Use random port between 8800-8899
TIMEOUT_SECONDS=30
MAX_WAIT_TIME=45

# Optional: filter tests by name (substring match, case-insensitive)
TEST_FILTER="${TEST_FILTER:-}"

echo -e "${BLUE}🌐 URL Rewrite Integration Test${NC}"
echo -e "${BLUE}==============================${NC}"
echo ""

# Function to print status messages
print_status() {
    echo -e "${CYAN}$1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

# Function to clean up test resources
cleanup() {
    print_status "Cleaning up URL rewrite test resources..."
    
    # Stop test server if running
    if [ -n "$LUCLI_JAR" ] && java -jar $LUCLI_JAR server status --name "$TEST_SERVER_NAME" 2>/dev/null | grep -q "RUNNING"; then
        java -jar $LUCLI_JAR server stop --name "$TEST_SERVER_NAME" > /dev/null 2>&1 || true
        echo "  Stopped test server"
    fi
    
    # Remove test files
    rm -rf urlrewrite_test_files 2>/dev/null || true
    echo "  Removed test files"
}

# Set up cleanup on exit (but not on normal completion)
trap 'cleanup' INT TERM

# Check prerequisites
print_status "Checking URL rewrite test prerequisites..."

# Check if curl is available for HTTP testing
if ! command -v curl &> /dev/null; then
    print_warning "curl not available - skipping HTTP URL rewrite tests"
    exit 0
fi

# Check if LuCLI JAR exists and determine path (consistent with main test suite)
if [ -f "../target/lucli.jar" ]; then
    LUCLI_JAR="$(pwd)/../target/lucli.jar"  # Running from tests/ directory - make absolute
    print_status "Using LuCLI JAR: $LUCLI_JAR"
elif [ -f "./target/lucli.jar" ]; then
    LUCLI_JAR="$(pwd)/target/lucli.jar"  # Running from project root - make absolute
    print_status "Using LuCLI JAR: $LUCLI_JAR"
else
    print_error "LuCLI JAR not found in expected locations"
    echo "  Checked: ../target/lucli.jar, ./target/lucli.jar"
    echo "  Current directory: $(pwd)"
    exit 1
fi

print_success "Prerequisites satisfied"

# Create test directory and files
print_status "Setting up URL rewrite test files..."

mkdir -p urlrewrite_test_files
cd urlrewrite_test_files

# Create lucee.json with URL rewrite configuration
cat > lucee.json << EOF
{
  "name": "urlrewrite-integration-test",
  "version": "6.2.2.91",
  "port": $TEST_PORT,
  "webroot": "./",
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  },
  "jvm": {
    "maxMemory": "256m",
    "minMemory": "64m"
  }
}
EOF

# Create main router file (index.cfm) for framework-style routing
cat > index.cfm << 'EOF'
<cfscript>
    // Simple router for testing URL rewrite functionality
    pathInfo = cgi.path_info ?: "";
    
    // Remove leading slash if present
    if (left(pathInfo, 1) == "/") {
        pathInfo = right(pathInfo, len(pathInfo) - 1);
    }
    
    // Default to "home" if path is empty
    route = len(pathInfo) > 0 ? pathInfo : "home";
    
    // Set content type to JSON for API routes
    if (left(route, 4) == "api/") {
        cfheader(name="Content-Type", value="application/json");
        response = {
            "success": true,
            "route": route,
            "pathInfo": pathInfo,
            "message": "URL rewrite working",
            "timestamp": now()
        };
        writeOutput(serializeJSON(response));
        return;
    }
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>URL Rewrite Test</title>
</head>
<body>
    <h1>URL Rewrite Test Page</h1>
    <cfoutput>
        <p><strong>Status:</strong> URL rewrite is working!</p>
        <p><strong>Route:</strong> #route#</p>
        <p><strong>PATH_INFO:</strong> #pathInfo#</p>
        <p><strong>Request URI:</strong> #cgi.request_uri#</p>
        <p><strong>Timestamp:</strong> #now()#</p>
    </cfoutput>
    
    <cfswitch expression="#route#">
        <cfcase value="home">
            <h2>Home Route</h2>
            <p>Welcome to the URL rewrite test home page.</p>
        </cfcase>
        <cfcase value="hello">
            <h2>Hello Route</h2>
            <p>Hello from the framework router!</p>
        </cfcase>
        <cfcase value="about">
            <h2>About Route</h2>
            <p>This is the about page via URL rewriting.</p>
        </cfcase>
        <cfdefaultcase>
            <h2>Dynamic Route: <cfoutput>#route#</cfoutput></h2>
            <p>This is a dynamically handled route.</p>
        </cfdefaultcase>
    </cfswitch>
</body>
</html>
EOF

# Create a direct .cfm file to test bypass functionality  
cat > direct.cfm << 'EOF'
<cfoutput>
<h1>Direct CFML Access Test</h1>
<p>This file was accessed directly, bypassing the URL router.</p>
<p>Timestamp: #now()#</p>
</cfoutput>
EOF

print_success "Test files created"

# Start test server
print_status "Starting URL rewrite test server..."

# Try to start the server
print_status "Executing: java -jar $LUCLI_JAR server start --name $TEST_SERVER_NAME --force"

# Temporarily disable exit on error for server start
set +e
server_output=$(java -jar $LUCLI_JAR server start --name "$TEST_SERVER_NAME" --open-browser false --force 2>&1)
server_exit_code=$?
set -e

if [ $server_exit_code -eq 0 ]; then
    print_success "Server started successfully"
else
    print_error "Failed to start test server (exit code: $server_exit_code)"
    echo "Server output:"
    echo "$server_output"
    exit 1
fi

# Wait for server to be ready
print_status "Waiting for server to be ready..."
wait_count=0
server_ready=false

while [ $wait_count -lt $MAX_WAIT_TIME ]; do
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$TEST_PORT/" 2>/dev/null | grep -q "200"; then
        server_ready=true
        break
    fi
    echo -n "."
    sleep 1
    wait_count=$((wait_count + 1))
done

echo ""

if [ "$server_ready" = false ]; then
    print_error "Server did not become ready in time"
    exit 1
fi

print_success "Server is ready and responding"

# Test URL rewrite functionality
print_status "Testing URL rewrite functionality..."

test_count=0
test_passed=0

# Test 1: Root route should work
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 1: Root route (/)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 1: Root route (/) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 1: Root route (/)"
    if curl -s "http://localhost:$TEST_PORT/" | grep -q "URL rewrite is working"; then
        print_success "Root route test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "Root route test failed"
    fi
    test_count=$((test_count + 1))
fi

# Test 2: Named route should work
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 2: Named route (/hello)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 2: Named route (/hello) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 2: Named route (/hello)"
    if curl -s "http://localhost:$TEST_PORT/hello" | grep -q "Hello from the framework router"; then
        print_success "Hello route test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "Hello route test failed"
    fi
    test_count=$((test_count + 1))
fi

# Test 3: Another named route
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 3: About route (/about)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 3: About route (/about) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 3: About route (/about)"
    if curl -s "http://localhost:$TEST_PORT/about" | grep -q "This is the about page"; then
        print_success "About route test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "About route test failed"
    fi
    test_count=$((test_count + 1))
fi

# Test 4: API route (JSON response)
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 4: API route (/api/test)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 4: API route (/api/test) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 4: API route (/api/test)"
    api_response=$(curl -s "http://localhost:$TEST_PORT/api/test")
    if echo "$api_response" | grep -q '\"success\":true' && echo "$api_response" | grep -q '\"message\":\"URL rewrite working\"'; then
        print_success "API route test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "API route test failed"
        echo "Response: $api_response"
    fi
    test_count=$((test_count + 1))
fi

# Test 5: Direct .cfm access (should bypass router)
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 5: Direct .cfm access (/direct.cfm)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 5: Direct .cfm access (/direct.cfm) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 5: Direct .cfm access (/direct.cfm)"
    if curl -s "http://localhost:$TEST_PORT/direct.cfm" | grep -q "Direct CFML Access Test"; then
        print_success "Direct .cfm access test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "Direct .cfm access test failed"
    fi
    test_count=$((test_count + 1))
fi

# Test 6: Dynamic route handling
if [[ -n "$TEST_FILTER" ]] && ! echo "Test 6: Dynamic route (/products/laptop)" | grep -iq -- "$TEST_FILTER"; then
    print_status "↷ Skipping: Test 6: Dynamic route (/products/laptop) (does not match filter '$TEST_FILTER')"
else
    print_status "Test 6: Dynamic route (/products/laptop)"
    if curl -s "http://localhost:$TEST_PORT/products/laptop" | grep -q "Dynamic Route.*products/laptop"; then
        print_success "Dynamic route test passed"
        test_passed=$((test_passed + 1))
    else
        print_error "Dynamic route test failed"
    fi
    test_count=$((test_count + 1))
fi

# Test Results
echo ""
print_status "URL Rewrite Test Results:"
echo "  Tests run: $test_count"
echo "  Tests passed: $test_passed"
echo "  Tests failed: $((test_count - test_passed))"

if [ $test_passed -eq $test_count ]; then
    print_success "All URL rewrite tests passed!"
    echo ""
    print_success "✓ Framework-style routing: Working"
    print_success "✓ Named route handling: Working"
    print_success "✓ API route responses: Working"
    print_success "✓ Direct .cfm access: Working"
    print_success "✓ Dynamic route parsing: Working"
    print_success "✓ URL rewrite configuration: Working"
    cleanup
    exit 0
else
    print_error "Some URL rewrite tests failed"
    cleanup
    exit 1
fi
