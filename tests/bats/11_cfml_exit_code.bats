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

@test "cfml command exits 0 when expression succeeds" {
    # Bare expression result is returned, not auto-printed; use writeOutput
    # to exercise the success path end-to-end.
    run_lucli cfml 'writeOutput(1 + 2)'
    assert_success
    assert_output_contains "3"
}

@test "cfml now() returns a Lucee timestamp literal" {
    run_lucli cfml 'writeOutput(now())'
    assert_success
    assert_output_matches "\\{ts '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}'\\}"
}

@test "cfml command exits non-zero when expression throws" {
    # Regression guard: picocli was mapping the catch-block's null return
    # to exit 0, so every CFML failure reported success. Shell pipelines,
    # CI exit-code checks, and pre-commit hooks relied on this being 1.
    run_lucli cfml 'throw(message="boom")'
    assert_failure
}

@test "cfml command exits non-zero on syntax error" {
    run_lucli cfml 'this is not valid cfml'
    assert_failure
}
