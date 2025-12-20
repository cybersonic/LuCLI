# LuCLI Test Suite

## Test Organization

### Main Test Suites (Keep)
- `test.sh` - Comprehensive test suite covering all functionality
- `test-simple.sh` - Quick smoke tests (21 tests, ~10 seconds)

### Command-Specific Tests (Keep)
- `test-modules.sh` - Module command tests (list, init)
- `test-server-cfml.sh` - Server management tests

### Feature-Specific Tests (Keep)
- `test_cfml_comprehensive.sh` - CFML script execution tests
- `test_consistency.sh` - CLI vs Terminal mode consistency
- `test_tilde_fix.sh` - Tilde expansion tests
- `test-urlrewrite-integration.sh` - URL rewrite functionality

### Deprecated Tests (Consider Removing)
- `test_cfml.sh` - Superseded by test_cfml_comprehensive.sh
- `test_completion_simple.sh` - Old completion tests
- `test_interactive_completion.sh` - Old interactive tests
- `test_tab_completion.sh` - Old tab completion tests
- `test-completion.sh` - Newer but may be redundant

## Running Tests

```bash
# Quick smoke test
./tests/test-simple.sh

# Full suite
./tests/test.sh

# Specific command
./tests/test-modules.sh

# With custom LUCLI_HOME (clean environment)
LUCLI_HOME=/tmp/test-lucli ./tests/test-modules.sh
```

## Test Naming Convention
- `test-<command>.sh` - Command-specific tests (e.g., test-modules.sh, test-server.sh)
- `test-<feature>.sh` - Feature-specific tests (e.g., test-completion.sh)
- `test.sh` - Main comprehensive suite
- `test-simple.sh` - Quick smoke tests

## Running All Tests

To run all test suites (ideal for CI/CD):

```bash
./tests/test-all.sh
```

This will:
- Build LuCLI if needed
- Run all active test suites
- Report pass/fail for each suite
- Exit with code 0 (success) or 1 (failure)
- Display summary with clear visual feedback

### GitHub Actions / CI Integration

The `test-all.sh` script is designed for CI/CD:
- Exits with appropriate status codes
- Shows clear success/failure messages
- Displays failed suite names
- Can skip comprehensive tests (set `RUN_COMPREHENSIVE=true` to include)

Example CI usage:
```yaml
- name: Run LuCLI Tests
  run: ./tests/test-all.sh
```
