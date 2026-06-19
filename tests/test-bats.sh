#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BATS_DIR="${ROOT_DIR}/tests/bats"
LUCLI_JAR="${ROOT_DIR}/target/lucli.jar"
LUCLI_BINARY="${ROOT_DIR}/target/lucli"

if [[ "${CI:-}" != "true" && -f "${ROOT_DIR}/.sdkmanrc" ]]; then
    SDKMAN_JAVA_VERSION="$(grep '^java=' "${ROOT_DIR}/.sdkmanrc" | head -n 1 | cut -d'=' -f2- | xargs || true)"
    if [[ -n "${SDKMAN_JAVA_VERSION}" && -d "${HOME}/.sdkman/candidates/java/${SDKMAN_JAVA_VERSION}" ]]; then
        export JAVA_HOME="${HOME}/.sdkman/candidates/java/${SDKMAN_JAVA_VERSION}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
fi

if command -v bats >/dev/null 2>&1; then
    BATS_BIN="$(command -v bats)"
else
    echo "❌ bats-core is not installed."
    echo "Install on macOS with: brew install bats-core"
    exit 1
fi

export BATS_TEST_TIMEOUT="${BATS_TEST_TIMEOUT:-10}"

if [[ "${CI:-}" == "true" && -z "${TERM:-}" ]]; then
    export TERM="dumb"
fi
if [[ -z "${NO_JUNIT_XML:-}" ]]; then
    BATS_JUNIT_XML_OUTPUT="${BATS_JUNIT_XML_OUTPUT:-${JUNIT_XML_OUTPUT:-test-bats-results.xml}}"
else
    BATS_JUNIT_XML_OUTPUT=""
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


BATS_CACHE_HOME="${BATS_CACHE_HOME:-${TMPDIR:-/tmp}/lucli-bats-cache}"
mkdir -p "${BATS_CACHE_HOME}"
PREWARM_PROJECT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lucli-prewarm.XXXXXX")"
cat > "${PREWARM_PROJECT_DIR}/lucee.json" << 'EOF'
{
  "name": "bats-runtime-cache",
  "version": "1.0.0",
  "lucee": {
    "version": "7.0.4.34",
    "variant": "zero"
  },
  "openBrowser": false
}
EOF
if LUCLI_HOME="${BATS_CACHE_HOME}" java -jar "${LUCLI_JAR}" server start --prewarm "${PREWARM_PROJECT_DIR}" >/dev/null 2>&1; then
    export LUCLI_BATS_EXPRESS_CACHE_DIR="${BATS_CACHE_HOME}/express"
fi
rm -rf "${PREWARM_PROJECT_DIR}"

if [[ -n "${BATS_JUNIT_XML_OUTPUT}" ]]; then
    BATS_REPORT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bats-report.XXXXXX")"
    BATS_EXIT_CODE=0

    set +e
    if [[ -n "${TEST_FILTER:-}" ]]; then
        "${BATS_BIN}" -F tap --report-formatter junit --output "${BATS_REPORT_DIR}" --filter "${TEST_FILTER}" "$@" "${BATS_DIR}"
    else
        "${BATS_BIN}" -F tap --report-formatter junit --output "${BATS_REPORT_DIR}" "$@" "${BATS_DIR}"
    fi
    BATS_EXIT_CODE=$?
    set -e

    BATS_REPORT_FILE="$(find "${BATS_REPORT_DIR}" -type f -name '*.xml' | head -n 1 || true)"
    if [[ -z "${BATS_REPORT_FILE}" ]]; then
        BATS_REPORT_FILE="$(find "${BATS_REPORT_DIR}" -type f | head -n 1 || true)"
    fi

    if [[ -n "${BATS_REPORT_FILE}" ]]; then
        mkdir -p "$(dirname "${BATS_JUNIT_XML_OUTPUT}")"
        cp "${BATS_REPORT_FILE}" "${BATS_JUNIT_XML_OUTPUT}"
        echo "📄 BATS JUnit XML report: ${BATS_JUNIT_XML_OUTPUT}"
    else
        echo "⚠️ Could not locate BATS JUnit XML output in ${BATS_REPORT_DIR}"
    fi

    rm -rf "${BATS_REPORT_DIR}"
    exit "${BATS_EXIT_CODE}"
fi

if [[ -n "${TEST_FILTER:-}" ]]; then
    "${BATS_BIN}" -F pretty --filter "${TEST_FILTER}" "$@" "${BATS_DIR}"
else
    "${BATS_BIN}" -F pretty "$@" "${BATS_DIR}"
fi
