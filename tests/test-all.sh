#!/bin/bash
# LuCLI - Run All Tests
# Runs all test suites and reports results suitable for CI/CD

set -e

cd "$(dirname "$0")/.." || exit 1

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Track results
TOTAL_SUITES=0
PASSED_SUITES=0
FAILED_SUITES=()

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   LuCLI - Full Test Suite Runner     ║${NC}"
echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo ""

# Build if needed
LUCLI_JAR="target/lucli.jar"
if [ ! -f "$LUCLI_JAR" ]; then
    echo -e "${YELLOW}📦 Building LuCLI...${NC}"
    mvn package -DskipTests -Pbinary -q || {
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    }
    echo -e "${GREEN}✅ Build complete${NC}"
    echo ""
fi

# Function to run a test suite
run_suite() {
    local suite_name="$1"
    local test_script="$2"
    
    echo -e "${BLUE}▶ Running: ${suite_name}${NC}"
    TOTAL_SUITES=$((TOTAL_SUITES + 1))
    
    if bash "$test_script" > /tmp/test-output-$$.txt 2>&1; then
        echo -e "${GREEN}  ✅ PASSED${NC}"
        PASSED_SUITES=$((PASSED_SUITES + 1))
    else
        echo -e "${RED}  ❌ FAILED${NC}"
        FAILED_SUITES+=("$suite_name")
        # Show last 10 lines of output
        echo -e "${YELLOW}  Last 10 lines of output:${NC}"
        tail -n 10 /tmp/test-output-$$.txt | sed 's/^/    /'
    fi
    rm -f /tmp/test-output-$$.txt
    echo ""
}

# Run test suites
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo -e "${BLUE}Running Test Suites${NC}"
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo ""

run_suite "Simple Smoke Tests" "tests/test-simple.sh"
run_suite "Modules Commands" "tests/test-modules.sh"
run_suite "CFML Execution" "tests/test_cfml_comprehensive.sh"
run_suite "CLI vs Terminal Consistency" "tests/test_consistency.sh"
run_suite "Tilde Expansion" "tests/test_tilde_fix.sh"

# Optional: Run comprehensive suite (can be slow)
if [ "$RUN_COMPREHENSIVE" = "true" ]; then
    run_suite "Comprehensive Suite" "tests/test.sh"
fi

# Results Summary
echo ""
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo -e "${BLUE}Test Results Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo ""
echo -e "Total Suites: ${TOTAL_SUITES}"
echo -e "${GREEN}Passed: ${PASSED_SUITES}${NC}"
echo -e "${RED}Failed: ${#FAILED_SUITES[@]}${NC}"
echo ""

if [ ${#FAILED_SUITES[@]} -eq 0 ]; then
    echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  🎉 ALL TESTS PASSED!                 ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
    exit 0
else
    echo -e "${RED}╔════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  ⚠️  SOME TESTS FAILED                 ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${RED}Failed Suites:${NC}"
    for suite in "${FAILED_SUITES[@]}"; do
        echo -e "${RED}  - $suite${NC}"
    done
    exit 1
fi
