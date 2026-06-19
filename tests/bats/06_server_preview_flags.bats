#!/usr/bin/env bats

load './test_helper.bash'

setup_file() {
    require_lucli_artifacts
    setup_lucli_home
}

@test "server start dry-run include env,lucee shows only selected sections" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli_in_dir "${project_dir}" server start --dry-run --include env,lucee
    assert_success
    assert_output_contains "Realized environment variables used to resolve lucee.json placeholders"
    assert_output_contains ".CFConfig.json"
    [[ "${output}" != *"Realized lucee.json for:"* ]]
}

@test "server run dry-run include env omits realized lucee.json section" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli_in_dir "${project_dir}" server run --dry-run --include env
    assert_success
    assert_output_contains "Realized environment variables used to resolve lucee.json placeholders"
    [[ "${output}" != *"Realized lucee.json for:"* ]]
}

@test "server run dry-run include rejects unsupported sections" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli_in_dir "${project_dir}" server run --dry-run --include env,lucee
    assert_failure
    assert_output_contains "Unknown --include section 'lucee' for server run"
}

teardown_file() {
    cleanup_lucli_home
}

create_https_preview_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/https-preview.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "https-preview",
  "port": 8080,
  "version": "1.0.0",
  "lucee": {
    "version": "7.0.4.34",
    "variant": "zero"
  },
  "https": {
    "enabled": true,
    "port": 8443,
    "redirect": true
  }
}
EOF

    printf '%s\n' "${project_dir}"
}

create_extension_env_preview_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/ext-env-preview.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "ext-env-preview",
  "port": 8080,
  "dependencySettings": {
    "useLockFile": false
  },
  "dependencies": {
    "h2": {
      "type": "extension",
      "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
    }
  }
}
EOF

    printf '%s\n' "${project_dir}"
}

@test "server start dry-run include-lucee shows CFConfig preview" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --dry-run --include-lucee "${project_dir}"
    assert_success
    assert_output_contains ".CFConfig.json"
}

@test "server start dry-run include-tomcat-server shows server.xml preview" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --dry-run --include-tomcat-server "${project_dir}"
    assert_success
    assert_output_contains "server.xml"
}

@test "server start dry-run include-tomcat-web shows web.xml preview" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --dry-run --include-tomcat-web "${project_dir}"
    assert_success
    assert_output_contains "web.xml"
}

@test "server start dry-run include-https-keystore-plan shows keystore details" {
    local project_dir
    project_dir="$(create_https_preview_project)"

    run_lucli server start --dry-run --include-https-keystore-plan "${project_dir}"
    assert_success
    assert_output_contains "keystore"
}

@test "server start dry-run include-https-redirect-rules shows redirect details" {
    local project_dir
    project_dir="$(create_https_preview_project)"

    run_lucli server start --dry-run --include-https-redirect-rules "${project_dir}"
    assert_success
    assert_output_contains "redirect"
}

@test "server start dry-run include-all shows combined previews" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --dry-run --include-all "${project_dir}"
    assert_success
    assert_output_contains ".CFConfig.json"
    assert_output_contains "server.xml"
    assert_output_contains "web.xml"
}

@test "server start dry-run include-env shows LUCEE_EXTENSIONS from lucee.json when lockfile is disabled" {
    local project_dir
    project_dir="$(create_extension_env_preview_project)"

    [[ ! -f "${project_dir}/lucee-lock.json" ]]

    run_lucli_in_dir "${project_dir}" server start --dry-run --include-env
    assert_success
    assert_output_contains "LUCEE_EXTENSIONS"
    assert_output_contains "465E1E35-2425-4F4E-8B3FAB638BD7280A"
    [[ "${output}" != *"Realized lucee.json for:"* ]]
}

create_realized_env_preview_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/realized-env-preview.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "#env:PREVIEW_SERVER_NAME#",
  "port": 8080,
  "envVars": {
    "APP_MODE": "#env:PREVIEW_MODE:-dev#",
    "FALLBACK_MODE": "#env:MISSING_PREVIEW_MODE:-fallback-value#"
  }
}
EOF

    cat > "${project_dir}/.env" << 'EOF'
PREVIEW_SERVER_NAME=realized-env-preview
PREVIEW_MODE=preview-dot-env
EOF

    printf '%s\n' "${project_dir}"
}

@test "server start dry-run include-env shows realized lucee.json substitution variables" {
    local project_dir
    project_dir="$(create_realized_env_preview_project)"

    run_lucli_in_dir "${project_dir}" server start --dry-run --include-env
    assert_success
    assert_output_contains "Realized environment variables used to resolve lucee.json placeholders"
    assert_output_contains "\"PREVIEW_SERVER_NAME\" : \"realized-env-preview\""
    assert_output_contains "\"PREVIEW_MODE\" : \"preview-dot-env\""
    assert_output_contains "\"MISSING_PREVIEW_MODE\" : \"fallback-value\""
    [[ "${output}" != *"Realized lucee.json for:"* ]]
}

create_env_file_override_preview_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/env-file-override-preview.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "env-file-override-preview",
  "port": 8080,
  "envFile": "./default.env",
  "envVars": {
    "SETTING1": "from-base"
  },
  "environments": {
    "prod": {
      "envFile": "./prod.env",
      "envVars": {
        "SETTING1": "overridden-in-prod"
      }
    }
  }
}
EOF

    cat > "${project_dir}/default.env" << 'EOF'
DEFAULT=from-default
EOF

    cat > "${project_dir}/prod.env" << 'EOF'
DEFAULT=from-prod
EOF

    printf '%s\n' "${project_dir}"
}

@test "server start dry-run include-env with --env prod uses environment-specific envFile values" {
    local project_dir
    project_dir="$(create_env_file_override_preview_project)"

    run_lucli_in_dir "${project_dir}" server start --dry-run --env prod --include env
    assert_success
    assert_output_contains "\"DEFAULT\" : \"from-prod\""
    assert_output_contains "\"SETTING1\" : \"overridden-in-prod\""
}
