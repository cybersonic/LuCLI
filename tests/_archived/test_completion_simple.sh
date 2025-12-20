#!/bin/bash

# Simple Tab Completion Test for LuCLI
# Tests the underlying completion data availability

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LUCLI_BINARY="${SCRIPT_DIR}/target/lucli"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Test the SimpleServerConfigHelper directly
test_version_helper() {
    log_info "Testing SimpleServerConfigHelper for 7.x versions..."
    
    # Create a simple test program
    cat > VersionHelperTest.java <<'EOF'
import org.lucee.lucli.commands.SimpleServerConfigHelper;
import java.util.List;

public class VersionHelperTest {
    public static void main(String[] args) {
        try {
            SimpleServerConfigHelper helper = new SimpleServerConfigHelper();
            List<String> versions = helper.getAvailableVersions();
            
            System.out.println("Total versions available: " + versions.size());
            
            int count7x = 0;
            for (String version : versions) {
                if (version.startsWith("7")) {
                    System.out.println("Found 7.x version: " + version);
                    count7x++;
                }
            }
            
            if (count7x > 0) {
                System.out.println("SUCCESS: Found " + count7x + " version(s) starting with '7'");
                System.exit(0);
            } else {
                System.out.println("FAILED: No versions starting with '7' found");
                System.out.println("All versions:");
                for (String version : versions) {
                    System.out.println("  " + version);
                }
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF
    
    if [[ ! -x "$LUCLI_BINARY" ]]; then
        log_error "LuCLI binary not found at $LUCLI_BINARY"
        return 1
    fi
    
    # Compile and run
    if javac -cp "$LUCLI_BINARY" VersionHelperTest.java 2>/dev/null; then
        if java -cp ".:$LUCLI_BINARY" VersionHelperTest 2>&1 | grep -q "SUCCESS:"; then
            log_success "Version helper test - 7.x versions available"
            rm -f VersionHelperTest.java VersionHelperTest.class
            return 0
        else
            log_error "Version helper test - No 7.x versions found"
            java -cp ".:$LUCLI_BINARY" VersionHelperTest
            rm -f VersionHelperTest.java VersionHelperTest.class
            return 1
        fi
    else
        log_error "Version helper test - Compilation failed"
        rm -f VersionHelperTest.java VersionHelperTest.class
        return 1
    fi
}

# Test basic LuCLI startup
test_lucli_startup() {
    log_info "Testing LuCLI startup..."
    
    if [[ ! -x "$LUCLI_BINARY" ]]; then
        log_error "LuCLI binary not found at $LUCLI_BINARY"
        return 1
    fi
    
    # Test if LuCLI starts and exits properly
    if timeout 5s bash -c "echo 'exit' | '$LUCLI_BINARY' >/dev/null 2>&1"; then
        log_success "LuCLI startup test - Binary runs successfully"
        return 0
    else
        log_error "LuCLI startup test - Binary failed to run"
        return 1
    fi
}

# Simple test with sending basic commands
test_basic_commands() {
    log_info "Testing basic command execution..."
    
    if [[ ! -x "$LUCLI_BINARY" ]]; then
        log_error "LuCLI binary not found"
        return 1
    fi
    
    # Test basic help command
    local output
    if output=$(timeout 5s bash -c "echo -e 'help\\nexit' | '$LUCLI_BINARY' 2>&1"); then
        if [[ "$output" == *"Available commands"* ]] || [[ "$output" == *"help"* ]] || [[ "$output" == *"server"* ]]; then
            log_success "Basic commands test - Help command works"
            return 0
        else
            log_error "Basic commands test - Help command didn't produce expected output"
            echo "Output was: $output"
            return 1
        fi
    else
        log_error "Basic commands test - Command execution failed"
        return 1
    fi
}

# Test available config keys
test_config_keys() {
    log_info "Testing available configuration keys..."
    
    cat > ConfigKeysTest.java <<'EOF'
import org.lucee.lucli.commands.SimpleServerConfigHelper;
import java.util.List;

public class ConfigKeysTest {
    public static void main(String[] args) {
        try {
            SimpleServerConfigHelper helper = new SimpleServerConfigHelper();
            List<String> keys = helper.getAvailableKeys();
            
            System.out.println("Available configuration keys: " + keys.size());
            
            boolean hasVersion = false;
            boolean hasPort = false;
            
            for (String key : keys) {
                System.out.println("  " + key);
                if ("version".equals(key)) hasVersion = true;
                if ("port".equals(key)) hasPort = true;
            }
            
            if (hasVersion && hasPort) {
                System.out.println("SUCCESS: Found expected keys (version, port)");
                System.exit(0);
            } else {
                System.out.println("FAILED: Missing expected keys");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF
    
    if javac -cp "$LUCLI_BINARY" ConfigKeysTest.java 2>/dev/null; then
        if java -cp ".:$LUCLI_BINARY" ConfigKeysTest 2>&1 | grep -q "SUCCESS:"; then
            log_success "Config keys test - Expected keys available"
            rm -f ConfigKeysTest.java ConfigKeysTest.class
            return 0
        else
            log_error "Config keys test - Missing expected keys"
            java -cp ".:$LUCLI_BINARY" ConfigKeysTest
            rm -f ConfigKeysTest.java ConfigKeysTest.class
            return 1
        fi
    else
        log_error "Config keys test - Compilation failed"
        rm -f ConfigKeysTest.java ConfigKeysTest.class
        return 1
    fi
}

main() {
    echo "========================================"
    echo "LuCLI Tab Completion Test Suite"
    echo "========================================"
    echo
    
    local total_tests=0
    local passed_tests=0
    
    # Test 1: LuCLI Startup
    if test_lucli_startup; then
        passed_tests=$((passed_tests + 1))
    fi
    total_tests=$((total_tests + 1))
    echo
    
    # Test 2: Basic Commands
    if test_basic_commands; then
        passed_tests=$((passed_tests + 1))
    fi
    total_tests=$((total_tests + 1))
    echo
    
    # Test 3: Configuration Keys
    if test_config_keys; then
        passed_tests=$((passed_tests + 1))
    fi
    total_tests=$((total_tests + 1))
    echo
    
    # Test 4: Version Helper (the main completion data source)
    if test_version_helper; then
        passed_tests=$((passed_tests + 1))
    fi
    total_tests=$((total_tests + 1))
    echo
    
    echo "========================================"
    log_info "Test Summary:"
    log_info "Total tests: $total_tests"
    log_success "Passed tests: $passed_tests"
    
    if [[ $passed_tests -eq $total_tests ]]; then
        log_success "All tests passed! Tab completion data should be working."
        echo
        echo "To test interactive tab completion manually:"
        echo "1. Run: ./target/lucli"
        echo "2. Type: server config get version=7"
        echo "3. Press Tab to see completions"
        echo
        exit 0
    else
        local failed=$((total_tests - passed_tests))
        log_error "Failed tests: $failed"
        exit 1
    fi
}

main "$@"
