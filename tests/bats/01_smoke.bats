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

@test "help command shows usage" {
    run_lucli --help
    assert_help_exit_code
    assert_output_contains "Usage:"
}

@test "short help shows usage" {
    run_lucli -h
    assert_help_exit_code
    assert_output_contains "Usage:"
}

@test "version command works" {
    run_lucli --version
    assert_success
    assert_output_contains "LuCLI"
    assert_output_contains "Java Version:"
}

@test "lucee version output includes Lucee" {
    run_lucli --lucee-version
    assert_success
    assert_output_contains "Lucee"
}

@test "invalid option exits non-zero" {
    run_lucli --invalid-option
    assert_failure
}

@test "nonexistent file exits non-zero" {
    run_lucli nonexistent.cfs
    assert_failure
}

@test "binary version command works" {
    run_lucli_binary --version
    assert_success
    assert_output_contains "LuCLI"
}

@test "binary help command shows usage" {
    run_lucli_binary --help
    assert_help_exit_code
    assert_output_contains "Usage"
}

@test "version format is semantic" {
    run_lucli --version
    assert_success
    assert_output_matches 'LuCLI Version: [0-9]+\.[0-9]+\.[0-9]+'
}
