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

@test "modules help works" {
    run_lucli modules --help
    assert_help_exit_code
    assert_output_contains "modules"
}

@test "modules list works when empty" {
    run_lucli modules list
    assert_success
    [[ "${output}" != *"Error"* ]]
}

@test "modules init creates expected files" {
    local module_name="bats_module_${BATS_TEST_NUMBER}"
    local module_dir="${LUCLI_HOME}/modules/${module_name}"

    run_lucli modules init "${module_name}" --no-git
    assert_success
    [ -d "${module_dir}" ]
    [ -f "${module_dir}/Module.cfc" ]
    [ -f "${module_dir}/module.json" ]
    [ -f "${module_dir}/README.md" ]
}

@test "modules list includes initialized module" {
    local module_name="bats_module_${BATS_TEST_NUMBER}"

    run_lucli modules init "${module_name}" --no-git
    assert_success

    run_lucli modules list
    assert_success
    assert_output_contains "${module_name}"
}

@test "modules install without URL fails" {
    local module_name="missing_url_${BATS_TEST_NUMBER}"

    run_lucli modules install "${module_name}"
    assert_failure
}

@test "modules run executes initialized module" {
    local module_name="bats_run_module_${BATS_TEST_NUMBER}"
    local module_dir="${LUCLI_HOME}/modules/${module_name}"

    run_lucli modules init "${module_name}" --no-git
    assert_success

    cat > "${module_dir}/Module.cfc" << EOF
component {
    function main() {
        writeOutput("Hello from ${module_name} module");
    }
}
EOF

    run_lucli modules run "${module_name}"
    assert_success
    assert_output_contains "Hello from ${module_name} module"
}

@test "run command executes cfm script" {
    run_lucli run "${LUCLI_ROOT_DIR}/tests/cfml/run.cfm"
    assert_success
    assert_output_contains "Hello from a tag based file"
}

@test "run whitespace flag toggles cfmlWriter mode" {
    local script_path
    script_path="$(mktemp "${BATS_TEST_TMPDIR}/whitespace_probe.XXXXXX.cfm")"
    cat > "${script_path}" << 'EOF'
<cfoutput>A


B
</cfoutput>
EOF

    run_lucli --verbose run "${script_path}"
    assert_success
    assert_output_contains "\"cfmlWriter\": \"regular\""

    run_lucli --verbose --whitespace run "${script_path}"
    assert_success
    assert_output_contains "\"cfmlWriter\": \"white-space\""
}

@test "run command blocks direct cfc execution" {
    run_lucli run "${LUCLI_ROOT_DIR}/tests/cfml/Run.cfc"
    assert_failure
    assert_output_contains ".cfc"
    assert_output_contains "not supported"
    assert_output_contains "modules run"
}

@test "shortcut command blocks direct cfc execution" {
    run_lucli "${LUCLI_ROOT_DIR}/tests/cfml/Run.cfc"
    assert_failure
    assert_output_contains ".cfc"
    assert_output_contains "not supported"
    assert_output_contains "modules run"
}

@test "lucli script works via shortcut and run" {
    local script_path
    script_path="$(mktemp "${BATS_TEST_TMPDIR}/test_run.XXXXXX.lucli")"
    cat > "${script_path}" << 'EOF'
echo "Run .lucli works"
EOF

    run_lucli "${script_path}"
    assert_success
    assert_output_contains "Run .lucli works"

    run_lucli run "${script_path}"
    assert_success
    assert_output_contains "Run .lucli works"
}

@test "lucli script exits non-zero when a command fails" {
    local script_base script_path missing_script
    script_base="$(mktemp "${BATS_TEST_TMPDIR}/test_run_failure.XXXXXX")"
    script_path="${script_base}.lucli"
    mv "${script_base}" "${script_path}"
    missing_script="${BATS_TEST_TMPDIR}/missing-${BATS_TEST_NUMBER}.cfs"

    cat > "${script_path}" << EOF
run "${missing_script}"
EOF

    run_lucli "${script_path}"
    assert_failure
    assert_output_contains "File not found"
}
