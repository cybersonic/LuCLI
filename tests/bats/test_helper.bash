#!/usr/bin/env bash

# Shared helpers for LuCLI BATS tests.

LUCLI_ROOT_DIR="$(cd "${BATS_TEST_DIRNAME}/../.." && pwd)"
LUCLI_JAR="${LUCLI_ROOT_DIR}/target/lucli.jar"
LUCLI_BINARY="${LUCLI_ROOT_DIR}/target/lucli"

require_lucli_artifacts() {
    if [[ ! -f "${LUCLI_JAR}" ]]; then
        skip "Missing ${LUCLI_JAR}. Build first: mvn package -Pbinary (or ./tests/test-bats.sh)"
    fi

    if [[ ! -x "${LUCLI_BINARY}" ]]; then
        skip "Missing executable ${LUCLI_BINARY}. Build first: mvn package -Pbinary (or ./tests/test-bats.sh)"
    fi

    if ! command -v java >/dev/null 2>&1; then
        skip "java not found in PATH"
    fi

    if ! java -jar "${LUCLI_JAR}" --version >/dev/null 2>&1; then
        skip "LuCLI artifact is not runnable with current Java. Run ./tests/test-bats.sh to apply project SDKMAN env and rebuild if needed."
    fi
}

setup_lucli_home() {
    export LUCLI_HOME
    local tmp_base
    tmp_base="${BATS_TEST_TMPDIR:-${BATS_FILE_TMPDIR:-${TMPDIR:-/tmp}}}"
    LUCLI_HOME="$(mktemp -d "${tmp_base%/}/lucli-home.XXXXXX")"
    if [[ -n "${LUCLI_BATS_EXPRESS_CACHE_DIR:-}" && -d "${LUCLI_BATS_EXPRESS_CACHE_DIR}" ]]; then
        ln -s "${LUCLI_BATS_EXPRESS_CACHE_DIR}" "${LUCLI_HOME}/express"
    fi
}

cleanup_lucli_home() {
    if [[ -n "${LUCLI_HOME:-}" && -d "${LUCLI_HOME}" ]]; then
        rm -rf "${LUCLI_HOME}"
    fi
}

run_lucli() {
    run java -jar "${LUCLI_JAR}" "$@"
}

run_lucli_binary() {
    run "${LUCLI_BINARY}" "$@"
}

run_lucli_in_dir() {
    local workdir="$1"
    shift
    run bash -c 'cd "$1" && shift && jar="$1" && shift && java -jar "$jar" "$@"' _ "${workdir}" "${LUCLI_JAR}" "$@"
}

prewarm_test_lucee_runtime() {
    local tmp_base
    local project_dir
    tmp_base="${BATS_TEST_TMPDIR:-${BATS_FILE_TMPDIR:-${TMPDIR:-/tmp}}}"
    project_dir="$(mktemp -d "${tmp_base%/}/prewarm-project.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "bats-runtime-prewarm",
  "version": "1.0.0",
  "lucee": {
    "version": "7.0.4.34",
    "variant": "zero"
  },
  "openBrowser": false
}
EOF

    java -jar "${LUCLI_JAR}" server start --prewarm "${project_dir}" >/dev/null 2>&1
    rm -rf "${project_dir}"
}

stop_all_servers_if_possible() {
    java -jar "${LUCLI_JAR}" server stop --all >/dev/null 2>&1 || true
}

find_available_test_port() {
    local attempts=0
    local port
    while [[ "${attempts}" -lt 200 ]]; do
        port=$((20000 + RANDOM % 30000))
        if command -v lsof >/dev/null 2>&1; then
            if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
                attempts=$((attempts + 1))
                continue
            fi
            printf '%s\n' "${port}"
            return 0
        fi

        if command -v python3 >/dev/null 2>&1; then
            if python3 - <<EOF >/dev/null 2>&1
import socket
s = socket.socket()
try:
    s.bind(("127.0.0.1", ${port}))
finally:
    s.close()
EOF
            then
                printf '%s\n' "${port}"
                return 0
            fi
            attempts=$((attempts + 1))
            continue
        fi

        # Last-resort fallback when no probing utility exists.
        if [[ -n "${port}" ]]; then
            printf '%s\n' "${port}"
            return 0
        fi
        attempts=$((attempts + 1))
    done
    return 1
}

assert_success() {
    [ "${status}" -eq 0 ]
}

assert_failure() {
    [ "${status}" -ne 0 ]
}

assert_help_exit_code() {
    [ "${status}" -eq 0 ] || [ "${status}" -eq 1 ] || [ "${status}" -eq 2 ]
}

assert_output_contains() {
    local needle="$1"
    [[ "${output}" == *"${needle}"* ]]
}

assert_output_matches() {
    local pattern="$1"
    [[ "${output}" =~ ${pattern} ]]
}

create_env_test_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/env-project.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "env-test",
  "port": 8080,
  "version": "1.0.0",
  "lucee": {
    "version": "7.0.4.34",
    "variant": "zero"
  },
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m"
  },
  "admin": {
    "enabled": true,
    "password": ""
  },
  "monitoring": {
    "enabled": true,
    "jmx": {
      "port": 8999
    }
  },
  "environments": {
    "prod": {
      "port": 8090,
      "jvm": {
        "maxMemory": "2048m"
      },
      "admin": {
        "password": "secret123"
      },
      "monitoring": {
        "enabled": false
      },
      "openBrowser": false
    },
    "dev": {
      "port": 8091,
      "monitoring": {
        "enabled": true,
        "jmx": {
          "port": 9000
        }
      }
    },
    "staging": {
      "port": 8092,
      "jvm": {
        "maxMemory": "1024m"
      }
    }
  }
}
EOF

    printf '%s\n' "${project_dir}"
}
