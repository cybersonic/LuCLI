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

@test "server start --dry-run uses base configuration" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"port\" : 8080"
    assert_output_contains "\"maxMemory\" : \"512m\""
}

@test "server start --env=prod --dry-run applies environment overrides" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=prod --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"port\" : 8090"
    assert_output_contains "\"maxMemory\" : \"2048m\""
    assert_output_contains "with environment: prod"
}

@test "invalid environment falls back to base configuration with warning" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=invalid --dry-run "${project_dir}"
    assert_success
    assert_output_contains "not found in lucee.json; using base configuration"
    assert_output_contains "\"port\" : 8080"
}
