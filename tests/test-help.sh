#!/bin/bash

# LuCLI Help Tests - focused on verifying help output formatting

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

LUCLI_JAR="target/lucli.jar"
LUCLI_BINARY="target/lucli"

echo -e "${BLUE}🧪 LuCLI Help Test Suite${NC}"
echo -e "${BLUE}=========================${NC}"
echo ""

# Ensure build artifacts exist (but don't rebuild automatically here)
if [ ! -f "$LUCLI_JAR" ] || [ ! -f "$LUCLI_BINARY" ]; then
  echo -e "${RED}❌ LuCLI artifacts not found (expected $LUCLI_JAR and $LUCLI_BINARY)${NC}"
  echo -e "${YELLOW}💡 Run 'mvn clean package -DskipTests -Pbinary' before this script.${NC}"
  exit 1
fi

TOTAL_TESTS=0
FAILED_TESTS=0

run_help_prefix_test() {
  local test_name="$1"
  local command="$2"
  local expected_prefix="$3"

  echo -e "${CYAN}Testing: ${test_name}${NC}"
  echo -e "${YELLOW}Command: ${command}${NC}"
  TOTAL_TESTS=$((TOTAL_TESTS + 1))

  # Capture output; help may go to stdout or stderr depending on picocli version/config
  local output
  output=$(eval "$command" 2>&1 || true)

  local first_line
  first_line=$(printf '%s\n' "$output" | head -n 1)

  if [[ "$first_line" == "$expected_prefix"* ]]; then
    echo -e "${GREEN}✅ PASSED${NC}"
    echo -e "${GREEN}First line: ${first_line}${NC}"
  else
    echo -e "${RED}❌ FAILED${NC}"
    echo -e "${RED}Expected prefix: ${expected_prefix}${NC}"
    echo -e "${RED}Actual first line: ${first_line}${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
  fi
  echo ""
}

# Test: `lucli server start --help` usage header
run_help_prefix_test \
  "server start help shows correct usage header" \
  "java -jar $LUCLI_JAR server start --help" \
  "Usage: lucli server start"

echo -e "${BLUE}📊 Help Test Summary${NC}"
echo -e "${BLUE}====================${NC}"
echo -e "Total tests run: ${TOTAL_TESTS}"
echo -e "Tests passed: $((TOTAL_TESTS - FAILED_TESTS))"
echo -e "Tests failed: ${FAILED_TESTS}"

if [ "$FAILED_TESTS" -eq 0 ]; then
  echo ""
  echo -e "${GREEN}🎉 All help tests passed.${NC}"
  exit 0
else
  echo ""
  echo -e "${RED}⚠️  Some help tests failed. See output above.${NC}"
  exit 1
fi
