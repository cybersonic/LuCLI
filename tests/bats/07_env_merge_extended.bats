#!/usr/bin/env bats

load './test_helper.bash'

setup_file() {
    require_lucli_artifacts
    setup_lucli_home
}
teardown_file() {
    cleanup_lucli_home
}

@test "server start dry-run prod env shows password override" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=prod --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"password\" : \"secret123\""
}

@test "server start dry-run dev env keeps base maxMemory" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=dev --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"maxMemory\" : \"512m\""
}

@test "server start dry-run staging env overrides maxMemory" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=staging --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"maxMemory\" : \"1024m\""
}

@test "server start dry-run staging env keeps base minMemory" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=staging --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"minMemory\" : \"128m\""
}

@test "server start dry-run prod env preserves admin enabled true" {
    local project_dir
    project_dir="$(create_env_test_project)"

    run_lucli server start --env=prod --dry-run "${project_dir}"
    assert_success
    assert_output_contains "\"admin\" : {"
    assert_output_contains "\"enabled\" : true"
}
