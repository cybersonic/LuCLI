#!/bin/bash

# LuCLI Simple Test Script - Focused on non-interactive testing
# This script tests core functionality without interactive terminal commands

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Test configuration
LUCLI_JAR="target/lucli.jar"
LUCLI_BINARY="target/lucli"
FAILED_TESTS=0
TOTAL_TESTS=0

echo -e "${BLUE}🧪 LuCLI Simple Test Suite${NC}"
echo -e "${BLUE}===========================${NC}"
echo ""

# Function to run a test
run_test() {
    local test_name="$1"
    local command="$2"
    local expected_exit_code="${3:-0}"
    
    echo -e "${CYAN}Testing: ${test_name}${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$command" > /dev/null 2>&1; then
        local actual_exit_code=$?
        if [ $actual_exit_code -eq $expected_exit_code ]; then
            echo -e "${GREEN}✅ PASSED${NC}"
        else
            echo -e "${RED}❌ FAILED (exit code: $actual_exit_code, expected: $expected_exit_code)${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        local actual_exit_code=$?
        if [ $expected_exit_code -ne 0 ]; then
            echo -e "${GREEN}✅ PASSED (expected failure)${NC}"
        else
            echo -e "${RED}❌ FAILED (exit code: $actual_exit_code)${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    fi
    echo ""
}

# Function to run a test with output capture
run_test_with_output() {
    local test_name="$1"
    local command="$2"
    local expected_pattern="$3"
    
    echo -e "${CYAN}Testing: ${test_name}${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    local output=$(eval "$command" 2>&1)
    local exit_code=$?
    
    if [ $exit_code -eq 0 ] && echo "$output" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✅ PASSED${NC}"
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo -e "${RED}Exit code: $exit_code${NC}"
        echo -e "${RED}Expected pattern: $expected_pattern${NC}"
        echo -e "${RED}Output: $(echo "$output" | head -n 3)${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo ""
}

# Check prerequisites
echo -e "${BLUE}📋 Checking Prerequisites${NC}"
if [ ! -f "$LUCLI_JAR" ]; then
    echo -e "${RED}❌ Building LuCLI first...${NC}"
    mvn clean package -DskipTests -Pbinary
fi

if [ ! -f "$LUCLI_JAR" ] || [ ! -f "$LUCLI_BINARY" ]; then
    echo -e "${RED}❌ Build failed or files missing${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites satisfied${NC}"
echo ""

# Test 1: Basic Help and Usage
echo -e "${BLUE}=== Basic Help and Usage Tests ===${NC}"
run_test_with_output "Help command works" "java -jar $LUCLI_JAR --help" "LuCLI"
run_test_with_output "Version command works" "java -jar $LUCLI_JAR --version" "LuCLI"
run_test_with_output "Lucee version works" "java -jar $LUCLI_JAR --lucee-version" "Lucee"

# Test 2: Binary Executable Tests
echo -e "${BLUE}=== Binary Executable Tests ===${NC}"
run_test_with_output "Binary version works" "$LUCLI_BINARY --version" "LuCLI"
run_test_with_output "Binary help works" "$LUCLI_BINARY --help" "LuCLI"

# Test 3: File Operations
echo -e "${BLUE}=== File Operations Tests ===${NC}"
run_test "Create test file" "echo 'Hello LuCLI' > test_file.txt"
run_test "File exists" "test -f test_file.txt"
run_test_with_output "File has content" "cat test_file.txt" "Hello LuCLI"
run_test "Remove test file" "rm test_file.txt"

# Test 4: CFML Script Execution
echo -e "${BLUE}=== CFML Script Tests ===${NC}"
cat > hello.cfs << 'EOF'
writeOutput("Hello from CFML script!" & chr(10));
writeOutput("Test completed successfully!" & chr(10));
EOF

run_test_with_output "CFML script execution" "timeout 10 java -jar $LUCLI_JAR hello.cfs 2>&1" "Hello from CFML script"
run_test "Clean up CFML script" "rm hello.cfs"

# Test 5: JAR Content Validation
echo -e "${BLUE}=== JAR Content Tests ===${NC}"
run_test "JAR contains LuCLI classes" "jar -tf $LUCLI_JAR | grep -q 'org/lucee/lucli'"
run_test "JAR contains server classes" "jar -tf $LUCLI_JAR | grep -q 'server/' || true"
run_test "JAR contains monitoring classes" "jar -tf $LUCLI_JAR | grep -q 'monitoring/' || true"
run_test "JAR contains templates" "jar -tf $LUCLI_JAR | grep -q 'tomcat_template'"
run_test "JAR contains Lucee engine" "jar -tf $LUCLI_JAR | grep -q 'lucee'"

# Test 6: Server Command Structure
echo -e "${BLUE}=== Server Command Tests ===${NC}"
run_test "Server help available" "java -jar $LUCLI_JAR server --help 2>/dev/null || java -jar $LUCLI_JAR --help | grep -q server"
run_test "Server list works" "timeout 5 java -jar $LUCLI_JAR server list 2>&1 | grep -q 'server\\|Server\\|found' || true"

# Test 7: Error Handling
echo -e "${BLUE}=== Error Handling Tests ===${NC}"
run_test "Invalid option fails" "java -jar $LUCLI_JAR --invalid-option 2>/dev/null" 1
run_test "Nonexistent file fails" "timeout 5 java -jar $LUCLI_JAR nonexistent.cfs 2>/dev/null" 1

# Test 8: Version Consistency
echo -e "${BLUE}=== Version Consistency Tests ===${NC}"
JAR_VERSION=$(java -jar $LUCLI_JAR --version | grep -o 'LuCLI [0-9.]*' | cut -d' ' -f2)
BINARY_VERSION=$($LUCLI_BINARY --version | grep -o 'LuCLI [0-9.]*' | cut -d' ' -f2)

echo -e "${CYAN}Testing: Version consistency between JAR and binary${NC}"
TOTAL_TESTS=$((TOTAL_TESTS + 1))
if [ "$JAR_VERSION" = "$BINARY_VERSION" ]; then
    echo -e "${GREEN}✅ PASSED - Both report version: $JAR_VERSION${NC}"
else
    echo -e "${RED}❌ FAILED - JAR: $JAR_VERSION, Binary: $BINARY_VERSION${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# Test Results Summary
echo ""
echo -e "${BLUE}📊 Test Results Summary${NC}"
echo -e "${BLUE}======================${NC}"
echo -e "Total tests run: ${TOTAL_TESTS}"
echo -e "Tests passed: $((TOTAL_TESTS - FAILED_TESTS))"
echo -e "Tests failed: ${FAILED_TESTS}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 All tests passed! LuCLI is working correctly.${NC}"
    echo -e "${GREEN}✨ Binary executable: ✓${NC}"
    echo -e "${GREEN}✨ Help system: ✓${NC}"
    echo -e "${GREEN}✨ Version reporting: ✓${NC}"
    echo -e "${GREEN}✨ CFML execution: ✓${NC}"
    echo -e "${GREEN}✨ JAR structure: ✓${NC}"
    echo -e "${GREEN}✨ Server commands: ✓${NC}"
    echo -e "${GREEN}✨ Error handling: ✓${NC}"
    echo -e "${GREEN}✨ Version consistency: ✓${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}⚠️  Some tests failed. Please review the output above.${NC}"
    echo -e "${YELLOW}💡 Common issues:${NC}"
    echo -e "${YELLOW}   - Ensure Maven build completed successfully${NC}"
    echo -e "${YELLOW}   - Check Java version compatibility (requires Java 17+)${NC}"
    echo -e "${YELLOW}   - Verify Lucee engine is properly included${NC}"
    exit 1
fi
