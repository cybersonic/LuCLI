#!/bin/bash
# LuCLI - Run All Tests
# Runs all test suites and reports results suitable for CI/CD
# Produces JUnit XML output for CI integration
#
# Usage:
#   ./tests/test-all.sh                    # Run ALL tests (comprehensive + server)
#   SKIP_COMPREHENSIVE=1 ./tests/test-all.sh  # Skip comprehensive suite
#   SKIP_SERVER_TESTS=1 ./tests/test-all.sh   # Skip server integration tests
#   NO_JUNIT_XML=1 ./tests/test-all.sh     # Disable JUnit XML output
#   JUNIT_XML_OUTPUT=results.xml ./tests/test-all.sh  # Custom output path

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
SUITE_START_TIME=$(date +%s)

# JUnit XML configuration
if [[ -z "${NO_JUNIT_XML:-}" ]]; then
    JUNIT_XML_OUTPUT="${JUNIT_XML_OUTPUT:-test-all-results.xml}"
else
    JUNIT_XML_OUTPUT=""
fi

# Arrays for JUnit XML aggregation
declare -a JUNIT_SUITE_NAMES
declare -a JUNIT_SUITE_TIMES
declare -a JUNIT_SUITE_RESULTS  # "passed" or "failed"
declare -a JUNIT_SUITE_OUTPUTS

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   LuCLI - Full Test Suite Runner       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Build if needed (skip in CI where it's pre-built)
LUCLI_JAR="target/lucli.jar"
if [[ "${CI:-}" == "true" ]]; then
    echo -e "${YELLOW}CI detected, using pre-built JAR${NC}"
elif [ ! -f "$LUCLI_JAR" ]; then
    echo -e "${YELLOW}📦 Building LuCLI...${NC}"
    mvn package -DskipTests -Pbinary -q || {
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    }
    echo -e "${GREEN}✅ Build complete${NC}"
    echo ""
fi

# Function to escape XML special characters
xml_escape() {
    local str="$1"
    str="${str//&/&amp;}"
    str="${str//</&lt;}"
    str="${str//>/&gt;}"
    str="${str//\"/&quot;}"
    # Remove ANSI color codes
    str=$(echo "$str" | sed 's/\x1b\[[0-9;]*m//g')
    printf '%s' "$str"
}

# Function to write JUnit XML report
write_junit_xml() {
    local output_file="$1"
    local total_time=$(($(date +%s) - SUITE_START_TIME))
    local test_count=${#JUNIT_SUITE_NAMES[@]}
    local failure_count=${#FAILED_SUITES[@]}
    
    cat > "$output_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="LuCLI Test Suites" tests="$test_count" failures="$failure_count" time="$total_time" timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)">
EOF
    
    for i in "${!JUNIT_SUITE_NAMES[@]}"; do
        local name=$(xml_escape "${JUNIT_SUITE_NAMES[$i]}")
        local time="${JUNIT_SUITE_TIMES[$i]}"
        local result="${JUNIT_SUITE_RESULTS[$i]}"
        local output=$(xml_escape "${JUNIT_SUITE_OUTPUTS[$i]}")
        
        echo "  <testsuite name=\"$name\" tests=\"1\" failures=\"$( [[ "$result" == "failed" ]] && echo 1 || echo 0 )\" time=\"$time\">" >> "$output_file"
        echo "    <testcase name=\"$name\" classname=\"LuCLI.TestSuites\" time=\"$time\">" >> "$output_file"
        
        if [[ "$result" == "failed" ]]; then
            echo "      <failure message=\"Test suite failed\"><![CDATA[$output]]></failure>" >> "$output_file"
        fi
        
        echo "    </testcase>" >> "$output_file"
        echo "  </testsuite>" >> "$output_file"
    done
    
    echo "</testsuites>" >> "$output_file"
    echo -e "${GREEN}📄 JUnit XML report: $output_file${NC}"
}

# Function to run a test suite
run_suite() {
    local suite_name="$1"
    local test_script="$2"
    local suite_start=$(date +%s)
    
    echo -e "${BLUE}▶ Running: ${suite_name}${NC}"
    TOTAL_SUITES=$((TOTAL_SUITES + 1))
    
    local output_file="/tmp/test-output-$$.txt"
    
    if bash "$test_script" > "$output_file" 2>&1; then
        echo -e "${GREEN}  ✅ PASSED${NC}"
        PASSED_SUITES=$((PASSED_SUITES + 1))
        JUNIT_SUITE_RESULTS+=("passed")
        JUNIT_SUITE_OUTPUTS+=("")  # No output needed for passing tests
    else
        echo -e "${RED}  ❌ FAILED${NC}"
        FAILED_SUITES+=("$suite_name")
        JUNIT_SUITE_RESULTS+=("failed")
        # Capture last 50 lines for failure report
        JUNIT_SUITE_OUTPUTS+=("$(tail -n 50 "$output_file")")
        # Show last 10 lines to console
        echo -e "${YELLOW}  Last 10 lines of output:${NC}"
        tail -n 10 "$output_file" | sed 's/^/    /'
    fi
    
    local suite_time=$(($(date +%s) - suite_start))
    JUNIT_SUITE_NAMES+=("$suite_name")
    JUNIT_SUITE_TIMES+=("$suite_time")
    
    rm -f "$output_file"
    echo ""
}

# Run test suites
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo -e "${BLUE}Running Test Suites${NC}"
echo -e "${BLUE}═══════════════════════════════════════${NC}"
echo ""

# Core test suites (always run)
run_suite "Simple Smoke Tests" "tests/test-simple.sh"
run_suite "Help System" "tests/test-help.sh"
run_suite "Modules Commands" "tests/test-modules.sh"
run_suite "Dependency Management" "tests/test-deps.sh"
run_suite "CFML Execution" "tests/test_cfml_comprehensive.sh"
run_suite "CLI vs Terminal Consistency" "tests/test_consistency.sh"
run_suite "Tilde Expansion" "tests/test_tilde_fix.sh"

# Comprehensive suite (skip with SKIP_COMPREHENSIVE=1)
if [ "${SKIP_COMPREHENSIVE:-}" != "1" ]; then
    run_suite "Comprehensive Suite" "tests/test.sh"
fi

# Server integration tests (skip with SKIP_SERVER_TESTS=1)
if [ "${SKIP_SERVER_TESTS:-}" != "1" ]; then
    run_suite "Server CFML Integration" "tests/test-server-cfml.sh"
    run_suite "URL Rewrite Integration" "tests/test-urlrewrite-integration.sh"
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

# Write JUnit XML if enabled
if [[ -n "$JUNIT_XML_OUTPUT" ]]; then
    write_junit_xml "$JUNIT_XML_OUTPUT"
fi

if [ ${#FAILED_SUITES[@]} -eq 0 ]; then
    echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  🎉 ALL TESTS PASSED!                  ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
    exit 0
else
    echo -e "${RED}╔════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  ⚠️  SOME TESTS FAILED                  ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${RED}Failed Suites:${NC}"
    for suite in "${FAILED_SUITES[@]}"; do
        echo -e "${RED}  - $suite${NC}"
    done
    exit 1
fi
