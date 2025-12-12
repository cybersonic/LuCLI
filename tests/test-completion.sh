#!/usr/bin/env bash

# Test suite for LuCLI shell completion functionality
# This tests the __complete endpoint and completion script generation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAR_FILE="$PROJECT_ROOT/target/lucli.jar"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

test_count=0
pass_count=0
fail_count=0

# Helper function to run a test
run_test() {
    local test_name="$1"
    local command="$2"
    local expected="$3"
    
    test_count=$((test_count + 1))
    
    echo -n "Test $test_count: $test_name ... "
    
    output=$(eval "$command" 2>/dev/null)
    
    if [[ "$output" == "$expected" ]]; then
        echo -e "${GREEN}PASS${NC}"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}FAIL${NC}"
        echo "  Expected: $expected"
        echo "  Got: $output"
        fail_count=$((fail_count + 1))
    fi
}

# Helper function to test if output contains a pattern
run_test_contains() {
    local test_name="$1"
    local command="$2"
    local pattern="$3"
    
    test_count=$((test_count + 1))
    
    echo -n "Test $test_count: $test_name ... "
    
    output=$(eval "$command" 2>/dev/null)
    
    if [[ "$output" == *"$pattern"* ]]; then
        echo -e "${GREEN}PASS${NC}"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}FAIL${NC}"
        echo "  Expected to contain: $pattern"
        echo "  Got: $output"
        fail_count=$((fail_count + 1))
    fi
}

# Helper function to test if command succeeds
run_test_success() {
    local test_name="$1"
    local command="$2"
    
    test_count=$((test_count + 1))
    
    echo -n "Test $test_count: $test_name ... "
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}FAIL${NC}"
        fail_count=$((fail_count + 1))
    fi
}

echo "================================"
echo "LuCLI Completion Test Suite"
echo "================================"
echo ""

# Check if JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    cd "$PROJECT_ROOT"
    mvn clean package -q -DskipTests
    echo ""
fi

echo "Testing __complete endpoint..."
echo ""

# Test 1: Complete first subcommand (server, modules, cfml, completion)
run_test_contains "Complete 'lucli server' at position 1" \
    "java -jar $JAR_FILE __complete --words='lucli server' --current=1" \
    "server"

# Test 2: Complete server subcommands (start, stop, status, list, monitor, log)
run_test_contains "Complete server subcommands at position 2" \
    "java -jar $JAR_FILE __complete --words='lucli server start' --current=2" \
    "start"

# Test 3: Complete modules command
run_test_contains "Complete 'lucli modules' at position 1" \
    "java -jar $JAR_FILE __complete --words='lucli modules' --current=1" \
    "modules"

# Test 4: Complete cfml command
run_test_contains "Complete 'lucli cfml' at position 1" \
    "java -jar $JAR_FILE __complete --words='lucli cfml' --current=1" \
    "cfml"

# Test 5: Complete completion command (should appear in root options)
run_test_contains "Complete 'lucli completion' at position 1" \
    "java -jar $JAR_FILE __complete --words='lucli completion' --current=1" \
    "completion"

# Test 6: Complete with global options (--verbose, --debug, --timing)
run_test_contains "Complete global options" \
    "java -jar $JAR_FILE __complete --words='lucli --' --current=1" \
    "--verbose"

echo ""
echo "Testing completion script generation..."
echo ""

# Test 7: Generate bash completion script
run_test_contains "Generate bash completion script" \
    "java -jar $JAR_FILE completion bash" \
    "_lucli_completion()"

# Test 8: Bash script contains lucli command reference
run_test_contains "Bash script contains __complete endpoint call" \
    "java -jar $JAR_FILE completion bash" \
    "__complete"

# Test 9: Generate zsh completion script
run_test_contains "Generate zsh completion script" \
    "java -jar $JAR_FILE completion zsh" \
    "_lucli()"

# Test 10: Zsh script contains lucli command reference
run_test_contains "Zsh script contains __complete endpoint call" \
    "java -jar $JAR_FILE completion zsh" \
    "__complete"

# Test 11: Bash script is valid bash syntax
run_test_success "Bash completion script has valid syntax" \
    "java -jar $JAR_FILE completion bash | bash -n"

# Test 12: Zsh script has valid zsh syntax (basic check)
run_test_contains "Zsh completion script contains compdef" \
    "java -jar $JAR_FILE completion zsh" \
    "#compdef lucli"

echo ""
echo "Testing edge cases..."
echo ""

# Test 13: Invalid current index should not crash
run_test_success "Handle invalid current index gracefully" \
    "java -jar $JAR_FILE __complete --words='lucli' --current=999"

# Test 14: Missing --current should not crash
run_test_success "Handle missing --current argument gracefully" \
    "java -jar $JAR_FILE __complete --words='lucli'"

# Test 15: Missing --words should not crash
run_test_success "Handle missing --words argument gracefully" \
    "java -jar $JAR_FILE __complete --current=1"

# Test 16: Empty words should not crash
run_test_success "Handle empty words gracefully" \
    "java -jar $JAR_FILE __complete --words='' --current=0"

echo ""
echo "================================"
echo "Test Summary"
echo "================================"
echo -e "Total tests: $test_count"
echo -e "Passed: ${GREEN}$pass_count${NC}"
echo -e "Failed: ${RED}$fail_count${NC}"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi
