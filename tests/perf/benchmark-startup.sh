#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
LUCLI_JAR="${ROOT_DIR}/target/lucli.jar"
LUCLI_BINARY="${ROOT_DIR}/target/lucli"

RUNS=20
WARMUP=3
OUTPUT_FILE="${ROOT_DIR}/tests/perf/benchmark-latest.json"
BASELINE_FILE=""
MAX_REGRESSION_PCT=""

usage() {
    cat <<'EOF'
Usage: tests/perf/benchmark-startup.sh [options]

Runs repeatable latency benchmarks for:
  1) startup/version command: lucli --version
  2) simple CFML command:     lucli cfml 'writeOutput(now())'

Options:
  --runs <n>                  Number of measured runs per command (default: 20)
  --warmup <n>                Number of warmup runs per command (default: 3)
  --output <path>             JSON output file (default: tests/perf/benchmark-latest.json)
  --baseline <path>           Baseline JSON to compare against
  --max-regression-pct <pct>  Fail if any median latency regression exceeds pct
  --help                      Show this help
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --runs)
            RUNS="$2"
            shift 2
            ;;
        --warmup)
            WARMUP="$2"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --baseline)
            BASELINE_FILE="$2"
            shift 2
            ;;
        --max-regression-pct)
            MAX_REGRESSION_PCT="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if ! [[ "${RUNS}" =~ ^[0-9]+$ ]] || [[ "${RUNS}" -le 0 ]]; then
    echo "--runs must be a positive integer" >&2
    exit 1
fi

if ! [[ "${WARMUP}" =~ ^[0-9]+$ ]] || [[ "${WARMUP}" -lt 0 ]]; then
    echo "--warmup must be a non-negative integer" >&2
    exit 1
fi

if [[ -n "${MAX_REGRESSION_PCT}" ]] && ! [[ "${MAX_REGRESSION_PCT}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "--max-regression-pct must be a non-negative number" >&2
    exit 1
fi

if [[ -n "${BASELINE_FILE}" && ! -f "${BASELINE_FILE}" ]]; then
    echo "Baseline file not found: ${BASELINE_FILE}" >&2
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required for benchmarking" >&2
    exit 1
fi

if [[ "${CI:-}" != "true" && -f "${ROOT_DIR}/.sdkmanrc" ]]; then
    SDKMAN_JAVA_VERSION="$(grep '^java=' "${ROOT_DIR}/.sdkmanrc" | head -n 1 | cut -d'=' -f2- | xargs || true)"
    if [[ -n "${SDKMAN_JAVA_VERSION}" && -d "${HOME}/.sdkman/candidates/java/${SDKMAN_JAVA_VERSION}" ]]; then
        export JAVA_HOME="${HOME}/.sdkman/candidates/java/${SDKMAN_JAVA_VERSION}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
fi

NEEDS_BUILD=false
if [[ ! -f "${LUCLI_JAR}" || ! -x "${LUCLI_BINARY}" ]]; then
    NEEDS_BUILD=true
elif ! java -jar "${LUCLI_JAR}" --version >/dev/null 2>&1; then
    NEEDS_BUILD=true
fi

if [[ "${NEEDS_BUILD}" == "true" ]]; then
    echo "ℹ️ LuCLI artifacts missing or not runnable with current Java, building target/lucli.jar and target/lucli..."
    (
        cd "${ROOT_DIR}"
        rm -rf target
        mvn package -q -Dmaven.test.skip=true -Djreleaser.dry.run=true
        cat src/bin/lucli.sh target/lucli.jar > target/lucli
        chmod 755 target/lucli
    )
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"

python3 - "${LUCLI_JAR}" "${RUNS}" "${WARMUP}" "${OUTPUT_FILE}" "${BASELINE_FILE}" "${MAX_REGRESSION_PCT}" <<'PY'
import datetime
import json
import math
import statistics
import subprocess
import sys
import time
from pathlib import Path

jar = sys.argv[1]
runs = int(sys.argv[2])
warmup = int(sys.argv[3])
output_file = sys.argv[4]
baseline_file = sys.argv[5]
max_regression_raw = sys.argv[6]
max_regression = float(max_regression_raw) if max_regression_raw else None

benchmarks = [
    ("startup_version", ["java", "-jar", jar, "--version"]),
    ("cfml_now", ["java", "-jar", jar, "cfml", "writeOutput(now())"]),
]

def p95(values):
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = max(0, math.ceil(0.95 * len(ordered)) - 1)
    return ordered[idx]

def run_benchmark(name, command):
    for _ in range(warmup):
        warmup_proc = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if warmup_proc.returncode != 0:
            raise RuntimeError(f"Warmup failed for {name} with exit code {warmup_proc.returncode}")

    durations = []
    for _ in range(runs):
        started = time.perf_counter()
        proc = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        elapsed_ms = (time.perf_counter() - started) * 1000.0
        if proc.returncode != 0:
            raise RuntimeError(f"Benchmark run failed for {name} with exit code {proc.returncode}")
        durations.append(elapsed_ms)

    return {
        "runs": runs,
        "warmup_runs": warmup,
        "mean_ms": round(statistics.mean(durations), 3),
        "median_ms": round(statistics.median(durations), 3),
        "p95_ms": round(p95(durations), 3),
        "min_ms": round(min(durations), 3),
        "max_ms": round(max(durations), 3),
        "stddev_ms": round(statistics.pstdev(durations), 3),
        "samples_ms": [round(v, 3) for v in durations],
    }

results = {}
for name, cmd in benchmarks:
    results[name] = run_benchmark(name, cmd)

payload = {
    "generated_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "runs": runs,
    "warmup_runs": warmup,
    "results": results,
}

Path(output_file).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

print(f"Saved benchmark JSON: {output_file}")
for name in ["startup_version", "cfml_now"]:
    metrics = results[name]
    print(
        f"{name}: median={metrics['median_ms']}ms, p95={metrics['p95_ms']}ms, "
        f"mean={metrics['mean_ms']}ms, min={metrics['min_ms']}ms, max={metrics['max_ms']}ms"
    )

if baseline_file:
    baseline = json.loads(Path(baseline_file).read_text(encoding="utf-8"))
    baseline_results = baseline.get("results", {})
    failing_deltas = []

    print("")
    print(f"Comparison against baseline: {baseline_file}")
    for name in ["startup_version", "cfml_now"]:
        current_median = results[name]["median_ms"]
        baseline_median = baseline_results.get(name, {}).get("median_ms")
        if baseline_median is None or baseline_median == 0:
            print(f"{name}: baseline median missing; skipped")
            continue

        delta_ms = current_median - baseline_median
        delta_pct = (delta_ms / baseline_median) * 100.0
        direction = "faster" if delta_ms < 0 else "slower"
        print(
            f"{name}: baseline={baseline_median}ms -> current={current_median}ms "
            f"({abs(delta_ms):.3f}ms, {abs(delta_pct):.2f}% {direction})"
        )

        if max_regression is not None and delta_pct > max_regression:
            failing_deltas.append((name, delta_pct))

    if failing_deltas:
        labels = ", ".join([f"{name} ({pct:.2f}%)" for name, pct in failing_deltas])
        raise SystemExit(f"Regression threshold exceeded ({max_regression}%): {labels}")
PY
