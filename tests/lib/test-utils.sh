#!/bin/bash
# LuCLI Test Utilities Library
# Shared functions for all test scripts including JUnit XML output
#
# Usage: source this file at the top of your test script
#   source "$(dirname "$0")/lib/test-utils.sh"
#   init_test_suite "My Test Suite"
#   run_test "Test name" "command"
#   finish_test_suite

# Colors for output
export RED='\033[0;31m'
export GREEN='\033[0;32m'
export YELLOW='\033[1;33m'
export BLUE='\033[0;34m'
export PURPLE='\033[0;35m'
export CYAN='\033[0;36m'
export NC='\033[0m' # No Color

# Test state
_TEST_SUITE_NAME=""
_TEST_SUITE_START_TIME=0
_TOTAL_TESTS=0
_FAILED_TESTS=0
_SKIPPED_TESTS=0
_ORIGINAL_DIR=""

# JUnit XML state
declare -a _JUNIT_TEST_NAMES
declare -a _JUNIT_TEST_TIMES
declare -a _JUNIT_TEST_STATUSES
declare -a _JUNIT_TEST_MESSAGES
declare -a _JUNIT_TEST_CLASSNAMES
_JUNIT_CLASSNAME="LuCLI"
_JUNIT_XML_OUTPUT=""

# Escape XML special characters
xml_escape() {
    local str="$1"
    str="${str//&/&amp;}"
    str="${str//</&lt;}"
    str="${str//>/&gt;}"
    str="${str//\"/&quot;}"
    str="${str//\'/&apos;}"
    # Remove ANSI color codes
    str=$(echo "$str" | sed 's/\x1b\[[0-9;]*m//g')
    printf '%s' "$str"
}

# Record a test result
record_test_result() {
    local test_name="$1"
    local status="$2"      # "passed", "failed", "skipped"
    local duration="$3"    # in seconds
    local message="${4:-}" # failure/skip message (optional)
    
    _JUNIT_TEST_NAMES+=("$test_name")
    _JUNIT_TEST_TIMES+=("$duration")
    _JUNIT_TEST_STATUSES+=("$status")
    _JUNIT_TEST_MESSAGES+=("$message")
    _JUNIT_TEST_CLASSNAMES+=("$_JUNIT_CLASSNAME")
}

# Write JUnit XML report
write_junit_xml() {
    local output_file="$1"
    local total_time=$(($(date +%s) - _TEST_SUITE_START_TIME))
    local test_count=${#_JUNIT_TEST_NAMES[@]}
    local failure_count=0
    local skip_count=0
    
    # Count failures and skips
    for status in "${_JUNIT_TEST_STATUSES[@]}"; do
        case "$status" in
            failed) failure_count=$((failure_count + 1)) ;;
            skipped) skip_count=$((skip_count + 1)) ;;
        esac
    done
    
    # Write XML
    cat > "$output_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="$(xml_escape "$_TEST_SUITE_NAME")" tests="$test_count" failures="$failure_count" skipped="$skip_count" time="$total_time" timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)">
EOF
    
    for i in "${!_JUNIT_TEST_NAMES[@]}"; do
        local name=$(xml_escape "${_JUNIT_TEST_NAMES[$i]}")
        local classname=$(xml_escape "${_JUNIT_TEST_CLASSNAMES[$i]}")
        local time="${_JUNIT_TEST_TIMES[$i]}"
        local status="${_JUNIT_TEST_STATUSES[$i]}"
        local message=$(xml_escape "${_JUNIT_TEST_MESSAGES[$i]}")
        
        echo "    <testcase name=\"$name\" classname=\"$classname\" time=\"$time\">" >> "$output_file"
        
        case "$status" in
            failed)
                echo "      <failure message=\"Test failed\">$message</failure>" >> "$output_file"
                ;;
            skipped)
                echo "      <skipped message=\"$message\"/>" >> "$output_file"
                ;;
        esac
        
        echo "    </testcase>" >> "$output_file"
    done
    
    cat >> "$output_file" << EOF
  </testsuite>
</testsuites>
EOF
}

# Initialize test suite
init_test_suite() {
    _TEST_SUITE_NAME="${1:-LuCLI Tests}"
    _TEST_SUITE_START_TIME=$(date +%s)
    _ORIGINAL_DIR="$(pwd)"
    _TOTAL_TESTS=0
    _FAILED_TESTS=0
    _SKIPPED_TESTS=0
    _JUNIT_TEST_NAMES=()
    _JUNIT_TEST_TIMES=()
    _JUNIT_TEST_STATUSES=()
    _JUNIT_TEST_MESSAGES=()
    _JUNIT_TEST_CLASSNAMES=()
    
    # Set JUnit output path (can be overridden by JUNIT_XML_OUTPUT env var)
    if [[ -z "${NO_JUNIT_XML:-}" ]]; then
        _JUNIT_XML_OUTPUT="${JUNIT_XML_OUTPUT:-}"
    fi
    
    echo -e "${BLUE}🧪 $_TEST_SUITE_NAME${NC}"
    echo -e "${BLUE}$(printf '=%.0s' $(seq 1 ${#_TEST_SUITE_NAME}))====${NC}"
    echo ""
}

# Set the classname for subsequent tests (for grouping in JUnit XML)
set_test_classname() {
    _JUNIT_CLASSNAME="$1"
}

# Run a test with expected exit code
run_test() {
    local test_name="$1"
    local command="$2"
    local expected_exit_code="${3:-0}"
    local start_time=$(date +%s)
    
    # Filter support
    if [[ -n "${TEST_FILTER:-}" ]] && ! echo "$test_name" | grep -iq -- "$TEST_FILTER"; then
        echo -e "${BLUE}↷ Skipping: ${test_name}${NC}"
        record_test_result "$test_name" "skipped" "0" "Filtered out"
        _SKIPPED_TESTS=$((_SKIPPED_TESTS + 1))
        return 0
    fi
    
    echo -e "${CYAN}Testing: ${test_name}${NC}"
    
    _TOTAL_TESTS=$((_TOTAL_TESTS + 1))
    local output
    output=$(eval "$command" 2>&1)
    local actual_exit_code=$?
    local duration=$(($(date +%s) - start_time))
    
    if [ $actual_exit_code -eq $expected_exit_code ]; then
        echo -e "${GREEN}✅ PASSED${NC}"
        record_test_result "$test_name" "passed" "$duration" ""
        return 0
    elif [ $expected_exit_code -ne 0 ] && [ $actual_exit_code -ne 0 ]; then
        echo -e "${GREEN}✅ PASSED (expected failure)${NC}"
        record_test_result "$test_name" "passed" "$duration" ""
        return 0
    else
        echo -e "${RED}❌ FAILED (exit: $actual_exit_code, expected: $expected_exit_code)${NC}"
        _FAILED_TESTS=$((_FAILED_TESTS + 1))
        record_test_result "$test_name" "failed" "$duration" "Exit: $actual_exit_code, expected: $expected_exit_code. Output: $output"
        return 1
    fi
}

# Run a test that checks output contains a pattern
run_test_with_output() {
    local test_name="$1"
    local command="$2"
    local expected_pattern="$3"
    local start_time=$(date +%s)
    
    if [[ -n "${TEST_FILTER:-}" ]] && ! echo "$test_name" | grep -iq -- "$TEST_FILTER"; then
        echo -e "${BLUE}↷ Skipping: ${test_name}${NC}"
        record_test_result "$test_name" "skipped" "0" "Filtered out"
        _SKIPPED_TESTS=$((_SKIPPED_TESTS + 1))
        return 0
    fi
    
    echo -e "${CYAN}Testing: ${test_name}${NC}"
    
    _TOTAL_TESTS=$((_TOTAL_TESTS + 1))
    local output
    output=$(eval "$command" 2>&1)
    local exit_code=$?
    local duration=$(($(date +%s) - start_time))
    
    if [ $exit_code -eq 0 ] && echo "$output" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✅ PASSED${NC}"
        record_test_result "$test_name" "passed" "$duration" ""
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        _FAILED_TESTS=$((_FAILED_TESTS + 1))
        record_test_result "$test_name" "failed" "$duration" "Pattern '$expected_pattern' not found. Exit: $exit_code. Output: $(echo "$output" | head -n 5)"
        return 1
    fi
}

# Finish test suite and write results
finish_test_suite() {
    local exit_code=0
    
    echo ""
    echo -e "${BLUE}📊 Results: $_TEST_SUITE_NAME${NC}"
    echo -e "${BLUE}================================${NC}"
    echo -e "Total: $_TOTAL_TESTS | ${GREEN}Passed: $((_TOTAL_TESTS - _FAILED_TESTS - _SKIPPED_TESTS))${NC} | ${RED}Failed: $_FAILED_TESTS${NC} | ${YELLOW}Skipped: $_SKIPPED_TESTS${NC}"
    
    if [[ -n "$_JUNIT_XML_OUTPUT" ]]; then
        cd "$_ORIGINAL_DIR" 2>/dev/null || true
        write_junit_xml "$_JUNIT_XML_OUTPUT"
        echo -e "${GREEN}📄 JUnit XML: $_JUNIT_XML_OUTPUT${NC}"
    fi
    
    if [ $_FAILED_TESTS -gt 0 ]; then
        exit_code=1
    fi
    
    return $exit_code
}

# Get test results as JSON (for aggregation)
get_test_results_json() {
    local total_time=$(($(date +%s) - _TEST_SUITE_START_TIME))
    echo "{\"suite\":\"$_TEST_SUITE_NAME\",\"total\":$_TOTAL_TESTS,\"failed\":$_FAILED_TESTS,\"skipped\":$_SKIPPED_TESTS,\"time\":$total_time}"
}

# Export results to a temp file for aggregation
export_results_for_aggregation() {
    local output_file="$1"
    local total_time=$(($(date +%s) - _TEST_SUITE_START_TIME))
    
    # Write individual test results as newline-delimited JSON
    for i in "${!_JUNIT_TEST_NAMES[@]}"; do
        local name="${_JUNIT_TEST_NAMES[$i]}"
        local classname="${_JUNIT_TEST_CLASSNAMES[$i]}"
        local time="${_JUNIT_TEST_TIMES[$i]}"
        local status="${_JUNIT_TEST_STATUSES[$i]}"
        local message="${_JUNIT_TEST_MESSAGES[$i]}"
        # Simple JSON (no jq dependency)
        echo "{\"name\":\"$(echo "$name" | sed 's/"/\\"/g')\",\"classname\":\"$classname\",\"time\":$time,\"status\":\"$status\",\"message\":\"$(echo "$message" | sed 's/"/\\"/g' | tr '\n' ' ')\"}" >> "$output_file"
    done
}
