#!/bin/bash

# Interactive Tab Completion Test for LuCLI
# This script attempts to test actual tab completion behavior

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LUCLI_BINARY="${SCRIPT_DIR}/target/lucli"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_note() {
    echo -e "${BLUE}[NOTE]${NC} $1"
}

# Test if expect is available
check_expect() {
    if command -v expect >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Install expect if on macOS
offer_expect_install() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        log_note "On macOS, you can install expect with: brew install expect"
    elif command -v apt-get >/dev/null 2>&1; then
        log_note "On Ubuntu/Debian, you can install expect with: sudo apt-get install expect"
    elif command -v yum >/dev/null 2>&1; then
        log_note "On RHEL/CentOS, you can install expect with: sudo yum install expect"
    else
        log_note "Please install 'expect' package for your system to run interactive tests"
    fi
}

# Test tab completion using expect
test_with_expect() {
    local test_case="$1"
    local input_command="$2"
    local expected_pattern="$3"
    
    log_info "Testing: $test_case"
    
    # Create expect script
    cat > test_completion.exp <<EOF
#!/usr/bin/expect -f

set timeout 10
set lucli_binary "$LUCLI_BINARY"
set test_command "$input_command"

# Start LuCLI
spawn \$lucli_binary
expect {
    "lucli>" {
        # Good, we got the prompt
    }
    timeout {
        puts "TIMEOUT: LuCLI didn't start properly"
        exit 1
    }
}

# Send the partial command
send "\$test_command"

# Send tab character
send "\t"

# Wait a moment for completion
sleep 0.5

# Look for completions in the output
expect {
    -re "$expected_pattern" {
        puts "SUCCESS: Found expected completion pattern"
        send "\\r"
        expect "lucli>"
        send "exit\\r"
        expect eof
        exit 0
    }
    timeout {
        puts "TIMEOUT: No completion found matching pattern"
        send "\\r"
        expect "lucli>"
        send "exit\\r"
        expect eof
        exit 1
    }
}
EOF
    
    # Run the expect script
    if expect test_completion.exp 2>&1 | grep -q "SUCCESS:"; then
        log_success "$test_case - Completion working"
        rm -f test_completion.exp
        return 0
    else
        log_error "$test_case - Completion not working as expected"
        rm -f test_completion.exp
        return 1
    fi
}

# Manual testing guide
manual_test_guide() {
    echo
    echo "========================================"
    echo "Manual Tab Completion Test Guide"
    echo "========================================"
    echo
    echo "Since expect is not available, please test manually:"
    echo
    echo "1. Start LuCLI:"
    echo "   ${BLUE}./target/lucli${NC}"
    echo
    echo "2. Test server config get version completion:"
    echo "   ${BLUE}server config get version=7${NC}${YELLOW}<Tab>${NC}"
    echo "   Expected: Show 7.x versions like 7.0.0.346, 7.0.0.145"
    echo
    echo "3. Test server config set version completion:"
    echo "   ${BLUE}server config set version=7${NC}${YELLOW}<Tab>${NC}"
    echo "   Expected: Show 7.x versions like 7.0.0.346, 7.0.0.145"
    echo
    echo "4. Test port completion:"
    echo "   ${BLUE}server config get port=80${NC}${YELLOW}<Tab>${NC}"
    echo "   Expected: Show ports like 8080, 8888"
    echo
    echo "5. Test boolean completion:"
    echo "   ${BLUE}server config set monitoring.enabled=tr${NC}${YELLOW}<Tab>${NC}"
    echo "   Expected: Show 'true'"
    echo
    echo "6. Test basic server completion:"
    echo "   ${BLUE}server con${NC}${YELLOW}<Tab>${NC}"
    echo "   Expected: Complete to 'config'"
    echo
    echo "7. Exit LuCLI:"
    echo "   ${BLUE}exit${NC}"
    echo
    echo "========================================"
}

# Demo script that shows what the user should see
create_demo_script() {
    log_info "Creating demo script to show expected tab completion behavior..."
    
    cat > demo_completion.sh <<'EOF'
#!/bin/bash

echo "========================================"
echo "LuCLI Tab Completion Demo"
echo "========================================"
echo
echo "This demo shows what you should see when testing tab completion:"
echo

echo "1. Starting LuCLI..."
echo "$ ./target/lucli"
echo "lucli> "
echo

echo "2. Testing version completion:"
echo "lucli> server config get version=7<TAB>"
echo "Completions should appear:"
echo "  7.0.0.346    ⚡ 7.0.0.346"
echo "  7.0.0.145    ⚡ 7.0.0.145" 
echo "  7.0.0.090    ⚡ 7.0.0.090"
echo

echo "3. Testing set command:"
echo "lucli> server config set version=7<TAB>"
echo "Same completions as above should appear."
echo

echo "4. Testing port completion:"
echo "lucli> server config get port=80<TAB>"
echo "Port completions should appear:"
echo "  8080    🔌 8080"
echo "  8888    🔌 8888"
echo

echo "5. Testing boolean completion:"
echo "lucli> server config set monitoring.enabled=tr<TAB>"
echo "Boolean completion should appear:"
echo "  true    ✅ true"
echo

echo "========================================"
echo "If you see completions like above, the fix is working!"
echo "========================================"
EOF
    
    chmod +x demo_completion.sh
    log_success "Demo script created: demo_completion.sh"
}

main() {
    echo "========================================"
    echo "LuCLI Interactive Tab Completion Test"
    echo "========================================"
    echo
    
    if [[ ! -x "$LUCLI_BINARY" ]]; then
        log_error "LuCLI binary not found at $LUCLI_BINARY"
        log_info "Please run 'mvn clean package -Pbinary' first"
        exit 1
    fi
    
    if check_expect; then
        log_success "expect command available - running interactive tests"
        echo
        
        local total_tests=0
        local passed_tests=0
        
        # Test 1: Version completion for get command
        if test_with_expect \
            "server config get version=7 completion" \
            "server config get version=7" \
            "7\\.0\\.0"; then
            passed_tests=$((passed_tests + 1))
        fi
        total_tests=$((total_tests + 1))
        echo
        
        # Test 2: Version completion for set command
        if test_with_expect \
            "server config set version=7 completion" \
            "server config set version=7" \
            "7\\.0\\.0"; then
            passed_tests=$((passed_tests + 1))
        fi
        total_tests=$((total_tests + 1))
        echo
        
        # Test 3: Server subcommand completion
        if test_with_expect \
            "server config subcommand completion" \
            "server con" \
            "config"; then
            passed_tests=$((passed_tests + 1))
        fi
        total_tests=$((total_tests + 1))
        echo
        
        echo "========================================"
        log_info "Interactive Test Summary:"
        log_info "Total tests: $total_tests"
        log_success "Passed tests: $passed_tests"
        
        if [[ $passed_tests -eq $total_tests ]]; then
            log_success "All interactive tests passed!"
            echo
            log_success "Tab completion is working correctly! ✨"
            echo
        else
            local failed=$((total_tests - passed_tests))
            log_error "Failed tests: $failed"
            echo
            log_error "Some tab completion tests failed. Please check the implementation."
        fi
        
    else
        log_info "expect command not available - cannot run automated interactive tests"
        offer_expect_install
        manual_test_guide
        echo
        
        # Still create demo script for reference
        create_demo_script
    fi
    
    log_info "For manual testing, run: ./target/lucli"
    log_info "Then try: server config get version=7<Tab>"
}

main "$@"
