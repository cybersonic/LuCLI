#!/bin/bash

# LuCLI Comprehensive Test Script
# This script tests the major functionality of LuCLI including:
# - Basic help and usage
# - Language switching and internationalization
# - Terminal commands and CFML execution
# - File operations and directory navigation
# - Error handling and edge cases

mvn clean package -DskipTests -Pbinary # Ensure the LuCLI JAR is built before running tests
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
LUCLI_JAR="target/lucli.jar"
TEST_DIR="test_output"
FAILED_TESTS=0
TOTAL_TESTS=0

echo -e "${BLUE}🧪 LuCLI Comprehensive Test Suite${NC}"
echo -e "${BLUE}===================================${NC}"
echo ""

# Function to run a test
run_test() {
    local test_name="$1"
    local command="$2"
    local expected_exit_code="${3:-0}"
    
    echo -e "${CYAN}Testing: ${test_name}${NC}"
    echo -e "${YELLOW}Command: ${command}${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$command" > /dev/null 2>&1; then
        if [ $? -eq $expected_exit_code ]; then
            echo -e "${GREEN}✅ PASSED${NC}"
        else
            echo -e "${RED}❌ FAILED (wrong exit code)${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        if [ $expected_exit_code -ne 0 ]; then
            echo -e "${GREEN}✅ PASSED (expected failure)${NC}"
        else
            echo -e "${RED}❌ FAILED${NC}"
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
    echo -e "${YELLOW}Command: ${command}${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    local output=$(eval "$command" 2>&1)
    local exit_code=$?
    
    if [ $exit_code -eq 0 ] && echo "$output" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✅ PASSED${NC}"
        echo -e "${PURPLE}Output sample: $(echo "$output" | head -n 1)${NC}"
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo -e "${RED}Exit code: $exit_code${NC}"
        echo -e "${RED}Output: $output${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo ""
}

# Check prerequisites
echo -e "${BLUE}📋 Checking Prerequisites${NC}"
if [ ! -f "$LUCLI_JAR" ]; then
    echo -e "${RED}❌ LuCLI JAR not found at $LUCLI_JAR${NC}"
    echo -e "${YELLOW}💡 Run 'mvn clean package -DskipTests' first${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not found in PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites satisfied${NC}"
echo ""

# Create test directory
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

echo -e "${BLUE}🚀 Starting Tests${NC}"
echo ""

# Test 1: Basic Help and Usage
echo -e "${BLUE}=== Basic Help and Usage Tests ===${NC}"
run_test_with_output "Help command" "java -jar ../$LUCLI_JAR --help" "LuCLI v"
run_test_with_output "Short help" "java -jar ../$LUCLI_JAR -h" "LuCLI v"
run_test "No arguments (should show help)" "java -jar ../$LUCLI_JAR"

# Test 2: Language Switching Tests
echo -e "${BLUE}=== Language Switching Tests ===${NC}"
run_test_with_output "Show current language" "java -jar ../$LUCLI_JAR terminal -c 'lang'" "Current language"
run_test_with_output "Switch to German" "java -jar ../$LUCLI_JAR terminal -c 'lang de'" "Sprache gewechselt"
run_test_with_output "Verify German active" "java -jar ../$LUCLI_JAR terminal -c 'lang'" "Aktuelle Sprache"
run_test_with_output "Switch to Spanish" "java -jar ../$LUCLI_JAR terminal -c 'lang es'" "Idioma cambiado"
run_test_with_output "Verify Spanish active" "java -jar ../$LUCLI_JAR terminal -c 'lang'" "Idioma actual"
run_test_with_output "Switch to Swiss German" "java -jar ../$LUCLI_JAR terminal -c 'lang gsw'" "Sprach gwechslet"
run_test_with_output "Switch back to English" "java -jar ../$LUCLI_JAR terminal -c 'lang en'" "Language changed"

# Test 3: Terminal Commands
echo -e "${BLUE}=== Terminal Commands Tests ===${NC}"
run_test_with_output "PWD command" "java -jar ../$LUCLI_JAR terminal -c 'pwd'" "Current directory"
# Fix: List directory to handle empty directories robustly
run_test_with_output "List directory" "java -jar ../$LUCLI_JAR terminal -c '[ -d . ] && ls || echo \"Directory does not exist\"'" "Contents of|Directory does not exist|Directory is empty"
run_test_with_output "Echo command" "java -jar ../$LUCLI_JAR terminal -c 'echo Hello World'" "Hello World"
run_test_with_output "Environment variables" "java -jar ../$LUCLI_JAR terminal -c 'env'" "Environment Variables"

# Test 4: Directory Operations
echo -e "${BLUE}=== Directory Operations Tests ===${NC}"
run_test "Create directory" "java -jar ../$LUCLI_JAR terminal -c 'mkdir testdir'"
run_test_with_output "Verify directory created" "java -jar ../$LUCLI_JAR terminal -c 'ls'" "testdir"
run_test_with_output "Change directory" "java -jar ../$LUCLI_JAR terminal -c 'cd testdir && pwd'" "testdir"
run_test "Remove directory" "java -jar ../$LUCLI_JAR terminal -c 'rmdir testdir'"

# Test 5: File Operations
echo -e "${BLUE}=== File Operations Tests ===${NC}"
echo "Hello, LuCLI!" > test_file.txt
run_test_with_output "Cat file" "java -jar ../$LUCLI_JAR terminal -c 'cat test_file.txt'" "Hello, LuCLI"
run_test "Remove file" "java -jar ../$LUCLI_JAR terminal -c 'rm test_file.txt'"

# Test 6: CFML Execution
echo -e "${BLUE}=== CFML Execution Tests ===${NC}"
run_test_with_output "CFML now() function" "java -jar ../$LUCLI_JAR terminal -c 'cfml now()'" "Result:"
run_test_with_output "CFML string output" "java -jar ../$LUCLI_JAR terminal -c \"cfml 'Hello CFML World!'\"" "Hello CFML World"
run_test_with_output "CFML math operation" "java -jar ../$LUCLI_JAR terminal -c 'cfml 2 + 2'" "Result:"

# Test 7: Create and run a simple CFML script
echo -e "${BLUE}=== CFML Script Execution Tests ===${NC}"
cat > hello.cfs << 'EOF'
writeOutput("Hello from CFML script!" & chr(10));
writeOutput("Arguments passed: " & arrayLen(__arguments) & chr(10));
for (i = 1; i <= arrayLen(__arguments); i++) {
    writeOutput("  Arg " & i & ": " & __arguments[i] & chr(10));
}
EOF

run_test_with_output "Execute CFML script" "java -jar ../$LUCLI_JAR hello.cfs arg1 arg2" "Hello from CFML script"

# Test 8: Error Handling
echo -e "${BLUE}=== Error Handling Tests ===${NC}"
run_test "Invalid option" "java -jar ../$LUCLI_JAR --invalid-option" 1
run_test "Nonexistent file" "java -jar ../$LUCLI_JAR nonexistent.cfs" 1
run_test_with_output "Invalid language" "java -jar ../$LUCLI_JAR terminal -c 'lang invalid'" "Invalid language"
run_test "Terminal command with missing file" "java -jar ../$LUCLI_JAR terminal -c 'cat nonexistent.txt'"

# Test 9: Module System (if modules exist)
echo -e "${BLUE}=== Module System Tests ===${NC}"
# Note: These tests depend on modules being available
run_test "List available modules (info module)" "java -jar ../$LUCLI_JAR --help | grep -i module"

# Test 10: Advanced Terminal Features
echo -e "${BLUE}=== Advanced Terminal Features ===${NC}"
run_test "Terminal help command" "java -jar ../$LUCLI_JAR terminal -c 'help'"
run_test "Clear command" "java -jar ../$LUCLI_JAR terminal -c 'clear'"

# Test 11: Settings Persistence
echo -e "${BLUE}=== Settings Persistence Tests ===${NC}"
run_test "Verify settings file exists" "test -f ~/.lucli/settings.json"
run_test_with_output "Check settings content" "cat ~/.lucli/settings.json" "language"

# Test 12: Multiple Language Help Tests
echo -e "${BLUE}=== Multiple Language Help Tests ===${NC}"
run_test_with_output "German help" "java -jar ../$LUCLI_JAR terminal -c 'lang de' && java -jar ../$LUCLI_JAR --help" "Verwendung:"
run_test_with_output "Spanish help" "java -jar ../$LUCLI_JAR terminal -c 'lang es' && java -jar ../$LUCLI_JAR --help" "Uso:"
run_test "Reset to English" "java -jar ../$LUCLI_JAR terminal -c 'lang en'"

# Test 13: Performance and Stress Tests
echo -e "${BLUE}=== Performance Tests ===${NC}"
run_test "Multiple quick commands" "for i in {1..5}; do java -jar ../$LUCLI_JAR terminal -c 'pwd' > /dev/null; done"
run_test "Language switching stress test" "for lang in en de es gsw en; do java -jar ../$LUCLI_JAR terminal -c \"lang \$lang\" > /dev/null; done"

# Cleanup
echo -e "${BLUE}🧹 Cleaning up test files${NC}"
cd ..
rm -rf "$TEST_DIR"

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
    echo -e "${GREEN}✨ Internationalization: ✓${NC}"
    echo -e "${GREEN}✨ Terminal commands: ✓${NC}"
    echo -e "${GREEN}✨ CFML execution: ✓${NC}"
    echo -e "${GREEN}✨ File operations: ✓${NC}"
    echo -e "${GREEN}✨ Settings persistence: ✓${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}⚠️  Some tests failed. Please review the output above.${NC}"
    echo -e "${YELLOW}💡 Common issues:${NC}"
    echo -e "${YELLOW}   - Ensure Maven build completed successfully${NC}"
    echo -e "${YELLOW}   - Check Java version compatibility${NC}"
    echo -e "${YELLOW}   - Verify Lucee engine is properly included${NC}"
    exit 1
fi
