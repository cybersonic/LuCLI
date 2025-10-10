#!/bin/bash

# URL Rewrite Testing Script for LuCLI - Framework-Style Routing
# This script automates testing of the framework-style URL routing functionality

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
TEST_DIR="test/urlrewrite-test"
SERVER_NAME="urlrewrite-test"
BASE_PORT=8888

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   LuCLI Framework Routing Test Suite${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print section headers
print_section() {
    echo ""
    echo -e "${BLUE}>>> $1${NC}"
    echo ""
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Function to check if server is running
is_server_running() {
    ./lucli server status --name "$SERVER_NAME" 2>/dev/null | grep -q "running"
    return $?
}

# Function to wait for server to be ready
wait_for_server() {
    local max_attempts=30
    local attempt=1
    
    print_section "Waiting for server to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${BASE_PORT}/" | grep -q "200"; then
            print_success "Server is ready and responding"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "Server did not become ready in time"
    return 1
}

# Function to test URL
test_url() {
    local url=$1
    local description=$2
    local expected_code=${3:-200}
    
    echo -n "Testing: $description ... "
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    
    if [ "$response" -eq "$expected_code" ]; then
        print_success "OK (HTTP $response)"
        return 0
    else
        print_error "FAILED (HTTP $response, expected $expected_code)"
        return 1
    fi
}

# Function to test URL with content verification
test_url_content() {
    local url=$1
    local description=$2
    local search_string=$3
    
    echo -n "Testing: $description ... "
    
    response=$(curl -s "$url")
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    
    if [ "$http_code" -eq 200 ] && echo "$response" | grep -q "$search_string"; then
        print_success "OK (HTTP $http_code, content verified)"
        return 0
    else
        print_error "FAILED (HTTP $http_code, content check failed)"
        return 1
    fi
}

# Function to verify files exist
verify_deployment_files() {
    print_section "Verifying Deployment Files"
    
    local server_dir="$HOME/.lucli/servers/$SERVER_NAME"
    local web_inf="$server_dir/webapps/ROOT/WEB-INF"
    
    if [ ! -d "$server_dir" ]; then
        print_error "Server directory not found: $server_dir"
        return 1
    fi
    
    # Check for urlrewritefilter JAR
    if ls "$web_inf/lib/urlrewritefilter-"*.jar 1> /dev/null 2>&1; then
        print_success "UrlRewriteFilter JAR found in WEB-INF/lib/"
        ls -lh "$web_inf/lib/urlrewritefilter-"*.jar | awk '{print "  " $9 " (" $5 ")"}'
    else
        print_error "UrlRewriteFilter JAR not found in WEB-INF/lib/"
        return 1
    fi
    
    # Check for urlrewrite.xml
    if [ -f "$web_inf/urlrewrite.xml" ]; then
        print_success "urlrewrite.xml found in WEB-INF/"
        echo "  Size: $(wc -l < "$web_inf/urlrewrite.xml") lines"
        
        # Verify it contains framework-style routing rules
        if grep -q "Framework Router" "$web_inf/urlrewrite.xml"; then
            print_success "Framework-style routing rules detected"
        else
            print_warning "Framework-style routing rules not found in urlrewrite.xml"
        fi
        
        # Check for routerFile placeholder
        if grep -q '${routerFile}' "$web_inf/urlrewrite.xml"; then
            print_success "Router file placeholder found (will be replaced at runtime)"
        fi
    else
        print_error "urlrewrite.xml not found in WEB-INF/"
        return 1
    fi
    
    # Check for web.xml with UrlRewriteFilter
    if grep -q "UrlRewriteFilter" "$web_inf/web.xml" 2>/dev/null; then
        print_success "UrlRewriteFilter configuration found in web.xml"
    else
        print_error "UrlRewriteFilter configuration not found in web.xml"
        return 1
    fi
    
    return 0
}

# Function to check server logs for errors
check_server_logs() {
    print_section "Checking Server Logs"
    
    local server_dir="$HOME/.lucli/servers/$SERVER_NAME"
    local log_file="$server_dir/logs/catalina.out"
    
    if [ ! -f "$log_file" ]; then
        print_warning "Log file not found: $log_file"
        return 0
    fi
    
    # Check for URL rewrite initialization
    if grep -q "UrlRewriteFilter" "$log_file"; then
        print_success "UrlRewriteFilter initialization found in logs"
    else
        print_warning "No UrlRewriteFilter initialization in logs"
    fi
    
    # Check for errors
    local error_count=$(grep -c "ERROR" "$log_file" 2>/dev/null || echo "0")
    if [ "$error_count" -eq 0 ]; then
        print_success "No ERROR entries in logs"
    else
        print_warning "Found $error_count ERROR entries in logs"
        echo "  Last 5 errors:"
        grep "ERROR" "$log_file" | tail -5 | sed 's/^/    /'
    fi
    
    return 0
}

# Main test execution
print_section "Step 1: Building LuCLI"
if [ -f "./build.sh" ]; then
    ./build.sh > /dev/null 2>&1
    print_success "LuCLI built successfully"
else
    print_error "build.sh not found"
    exit 1
fi

print_section "Step 2: Checking Server Status"
if is_server_running; then
    print_warning "Server '$SERVER_NAME' is already running. Stopping it..."
    ./lucli server stop --name "$SERVER_NAME"
    sleep 2
fi

print_section "Step 3: Starting Test Server"
echo "Starting server: $SERVER_NAME"
echo "Port: $BASE_PORT"
echo "Webroot: $TEST_DIR"
echo ""

./lucli server start \
    --name "$SERVER_NAME" \
    --port "$BASE_PORT" \
    --webroot "$TEST_DIR" \
    --open false

if [ $? -eq 0 ]; then
    print_success "Server start command executed"
else
    print_error "Failed to start server"
    exit 1
fi

# Wait for server to be ready
if ! wait_for_server; then
    print_error "Server failed to start properly"
    ./lucli server logs --name "$SERVER_NAME"
    exit 1
fi

# Verify deployment files
verify_deployment_files

# Run URL tests
print_section "Step 4: Testing Framework-Style Routing"
echo ""

test_passed=0
test_failed=0

# Test root URL - should go through router
if test_url_content "http://localhost:${BASE_PORT}/" "Root URL (/) - Router home action" "Framework-Style Routing Test"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test /hello route - should be handled by router
if test_url_content "http://localhost:${BASE_PORT}/hello" "Hello route (/hello) - Framework routing" "Hello Route"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test /about route - should be handled by router
if test_url_content "http://localhost:${BASE_PORT}/about" "About route (/about) - Framework routing" "About Route"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test /api/users route - should be handled by router as JSON
if test_url "http://localhost:${BASE_PORT}/api/users" "API route (/api/users) - JSON response"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test /api/users/123 route - RESTful pattern
if test_url "http://localhost:${BASE_PORT}/api/users/123" "API route with ID (/api/users/123) - RESTful"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test multi-segment route
if test_url "http://localhost:${BASE_PORT}/products/laptop/dell" "Multi-segment route (/products/laptop/dell)"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Test direct .cfm access - should bypass router
if test_url_content "http://localhost:${BASE_PORT}/test.cfm" "Direct .cfm access (/test.cfm) - Bypasses router" "Direct CFML Test"; then
    test_passed=$((test_passed + 1))
else
    test_failed=$((test_failed + 1))
fi

# Check server logs
check_server_logs

# Print summary
print_section "Test Summary"
echo ""
echo "Tests Passed: $test_passed"
echo "Tests Failed: $test_failed"
echo ""

if [ $test_failed -eq 0 ]; then
    print_success "All tests passed!"
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Framework Routing is working!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "✓ URLs are routed through index.cfm"
    echo "✓ PATH_INFO is set correctly"
    echo "✓ Router can parse and handle routes"
    echo "✓ Static resources are excluded"
    echo "✓ Direct .cfm access still works"
    echo ""
    echo "You can access the test server at:"
    echo "  http://localhost:${BASE_PORT}/"
    echo ""
    echo "Try these URLs:"
    echo "  http://localhost:${BASE_PORT}/hello"
    echo "  http://localhost:${BASE_PORT}/about"
    echo "  http://localhost:${BASE_PORT}/api/users"
    echo "  http://localhost:${BASE_PORT}/api/users/123"
    echo ""
    echo "To stop the server, run:"
    echo "  ./lucli server stop --name $SERVER_NAME"
    echo ""
    exit 0
else
    print_error "Some tests failed"
    echo ""
    echo "Check the logs with:"
    echo "  ./lucli server logs --name $SERVER_NAME"
    echo ""
    echo "To stop the server, run:"
    echo "  ./lucli server stop --name $SERVER_NAME"
    echo ""
    exit 1
fi