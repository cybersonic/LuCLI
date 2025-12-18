#!/usr/bin/env bash

# Test suite for LuCLI shell completion functionality
# This tests completion script generation (picocli AutoComplete-based) and dynamic version listing.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
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
    (cd "$PROJECT_ROOT" && mvn clean package -q -DskipTests)
    echo ""
fi

echo "Testing dynamic version listing..."
echo ""

# Test 1: versions-list should return at least 1 version (API or fallback)
run_test_success "versions-list returns at least 1 line" \
    "test \"$(java -jar $JAR_FILE versions-list | wc -l | tr -d ' ')\" -gt 0"

echo ""
echo "Testing completion script generation..."
echo ""

# Test 2: Generate bash completion script
run_test_contains "Generate bash completion script" \
    "java -jar $JAR_FILE completion bash" \
    "function _complete_lucli()"

# Test 3: Bash script should contain dynamic version hook
run_test_contains "Bash script uses versions-list for --version" \
    "java -jar $JAR_FILE completion bash" \
    "versions-list"

# Test 4: Bash script is valid bash syntax
run_test_success "Bash completion script has valid syntax" \
    "java -jar $JAR_FILE completion bash | bash -n"

# Test 5: Generate zsh completion script (bash-compatible + bashcompinit glue)
run_test_contains "Generate zsh completion script" \
    "java -jar $JAR_FILE completion zsh" \
    "bashcompinit"

# Test 6: Zsh script should contain dynamic version hook
run_test_contains "Zsh script uses versions-list for --version" \
    "java -jar $JAR_FILE completion zsh" \
    "versions-list"

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
