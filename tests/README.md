# LuCLI Test Suite

This directory contains integration and functional tests for LuCLI.

## Quick Start

```bash
# Run ALL tests (comprehensive + server integration)
./tests/test-all.sh

# Skip comprehensive suite (faster)
SKIP_COMPREHENSIVE=1 ./tests/test-all.sh

# Skip server integration tests (no port requirements)
SKIP_SERVER_TESTS=1 ./tests/test-all.sh

# Quick mode (skip both slow suites)
SKIP_COMPREHENSIVE=1 SKIP_SERVER_TESTS=1 ./tests/test-all.sh
```

## Test Organization

### Primary Test Runner
| Script | Purpose | JUnit XML |
|--------|---------|----------|
| `test-all.sh` | Runs all quick suites, aggregates results | ✅ `test-all-results.xml` |

### Core Test Suites (Always Run by test-all.sh)
| Script | Purpose | Duration |
|--------|---------|----------|
| `test-simple.sh` | Smoke tests: version, help, binary, basic CFML | ~10s |
| `test-help.sh` | Help system formatting validation | ~5s |
| `test-modules.sh` | Module commands: list, init | ~15s |
| `test-deps.sh` | Dependency management commands | ~20s |
| `test_cfml_comprehensive.sh` | Interactive CFML execution | ~10s |
| `test_consistency.sh` | CLI vs Terminal mode parity | ~10s |
| `test_tilde_fix.sh` | Tilde path expansion | ~5s |

### Optional Test Suites
| Script | Flag | Purpose |
|--------|------|---------|  
| `test.sh` | `RUN_COMPREHENSIVE=true` | Full 50+ test comprehensive suite (~2min) |
| `test-server-cfml.sh` | `RUN_SERVER_TESTS=true` | Server lifecycle with CFML integration |
| `test-urlrewrite-integration.sh` | `RUN_SERVER_TESTS=true` | URL rewriting framework support |

### Utility/Supporting Tests
| Script | Purpose | Status |
|--------|---------|--------|
| `test-completion.sh` | Shell completion script generation | Keep |

### Deprecated/Archived (in `_archived/`)
These have been superseded or are no longer relevant:
- Old completion tests (replaced by `test-completion.sh`)
- Obsolete interactive tests

## JUnit XML Output

All test scripts support JUnit XML for CI integration:

```bash
# test-all.sh writes to test-all-results.xml by default
./tests/test-all.sh

# Custom output path
JUNIT_XML_OUTPUT=results/tests.xml ./tests/test-all.sh

# Disable JUnit XML
NO_JUNIT_XML=1 ./tests/test-all.sh

# test.sh (comprehensive) writes to test-results.xml
./tests/test.sh
```

## Test Naming Convention

- `test-<command>.sh` - Command-specific (e.g., `test-modules.sh`)
- `test-<feature>.sh` - Feature-specific (e.g., `test-completion.sh`)
- `test_<legacy>.sh` - Legacy naming (underscores, consider renaming)
- `test.sh` - Main comprehensive suite
- `test-simple.sh` - Quick smoke tests
- `test-all.sh` - Aggregate runner for CI

## Writing New Tests

### Using the Shared Library

New tests can use `lib/test-utils.sh` for consistent output and JUnit XML:

```bash
#!/bin/bash
source "$(dirname "$0")/lib/test-utils.sh"

init_test_suite "My Feature Tests"
set_test_classname "LuCLI.MyFeature"

run_test "Basic functionality" "java -jar target/lucli.jar --version"
run_test_with_output "Output contains version" "java -jar target/lucli.jar --version" "LuCLI"
run_test "Expected failure" "java -jar target/lucli.jar --invalid" 1

finish_test_suite
```

### Test Utilities Provided

- `init_test_suite "Name"` - Initialize test suite
- `set_test_classname "Class"` - Set JUnit classname for grouping
- `run_test "name" "command" [expected_exit_code]` - Test exit code
- `run_test_with_output "name" "command" "pattern"` - Test output contains pattern
- `finish_test_suite` - Print summary and write JUnit XML

## CI/CD Integration

### GitHub Actions Example

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build
        run: mvn package -DskipTests -Pbinary
      
      - name: Run Tests
        run: ./tests/test-all.sh
      
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: test-all-results.xml
      
      - name: Publish Test Report
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: 'test-all-results.xml'
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SKIP_COMPREHENSIVE` | unset | Skip `test.sh` comprehensive suite |
| `SKIP_SERVER_TESTS` | unset | Skip server integration tests |
| `JUNIT_XML_OUTPUT` | `test-all-results.xml` | JUnit XML output path |
| `NO_JUNIT_XML` | unset | Disable JUnit XML output |
| `TEST_FILTER` | unset | Filter tests by name substring |
| `LUCLI_HOME` | `~/.lucli` | Override LuCLI home for isolated testing |
| `CI` | unset | Skip build if set (assumes pre-built) |

## Troubleshooting

### Tests fail with "JAR not found"
Run `mvn package -DskipTests -Pbinary` first, or let `test-all.sh` build automatically.

### Server tests fail with port conflicts
Server tests need free ports (8080, 9080, etc.). Stop any running LuCLI servers or other services.

### Tests hang on macOS
Some tests use `timeout` which behaves differently. Install GNU coreutils: `brew install coreutils`.

## Test Coverage Goals

| Area | Current | Target |
|------|---------|--------|
| CLI commands | ✅ Good | Maintain |
| Server lifecycle | ⚠️ Optional | Include in CI |
| CFML execution | ✅ Good | Maintain |
| Error handling | ⚠️ Basic | Expand |
| Configuration | ⚠️ Basic | Add more |
| Edge cases | ❌ Minimal | Add focused tests |
