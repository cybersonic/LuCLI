#!/usr/bin/env bats

load './test_helper.bash'

setup_file() {
    require_lucli_artifacts
}

setup() {
    setup_lucli_home
}

teardown() {
    cleanup_lucli_home
}

measure_lucli_elapsed_ms() {
    run python3 - "${LUCLI_JAR}" "$@" <<'PY'
import subprocess
import sys
import time

jar = sys.argv[1]
args = sys.argv[2:]
command = ["java", "-jar", jar, *args]

started = time.perf_counter()
completed = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
elapsed_ms = int((time.perf_counter() - started) * 1000)
print(elapsed_ms)
sys.exit(completed.returncode)
PY
}

@test "perf smoke: --version completes under threshold" {
    if ! command -v python3 >/dev/null 2>&1; then
        skip "python3 is required for perf smoke timing"
    fi

    local threshold_ms="${LUCLI_PERF_SMOKE_VERSION_MS:-15000}"
    measure_lucli_elapsed_ms --version
    assert_success
    assert_output_matches "^[0-9]+$"

    local elapsed_ms="${output}"
    [ "${elapsed_ms}" -le "${threshold_ms}" ]
}

@test "perf smoke: cfml now() completes under threshold" {
    if ! command -v python3 >/dev/null 2>&1; then
        skip "python3 is required for perf smoke timing"
    fi

    local threshold_ms="${LUCLI_PERF_SMOKE_CFML_NOW_MS:-20000}"
    measure_lucli_elapsed_ms cfml 'writeOutput(now())'
    assert_success
    assert_output_matches "^[0-9]+$"

    local elapsed_ms="${output}"
    [ "${elapsed_ms}" -le "${threshold_ms}" ]
}
