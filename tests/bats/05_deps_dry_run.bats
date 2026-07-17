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

create_nested_deps_project() {
    local tmp_root root_project nested1 nested2 nested3 nested4

    tmp_root="$(mktemp -d "${BATS_TEST_TMPDIR}/deps-project.XXXXXX")"
    root_project="${tmp_root}/root-project"
    nested1="${root_project}/dependencies/nested1"
    nested2="${nested1}/dependencies/nested2"
    nested3="${nested2}/dependencies/nested3"
    nested4="${nested3}/dependencies/nested4"

    mkdir -p "${nested4}"

    cat > "${root_project}/lucee.json" << 'EOF'
{
  "name": "root-project",
  "dependencies": {
    "nested1": {
      "source": "git",
      "installPath": "dependencies/nested1"
    }
  },
  "dependencySettings": {
    "installDevDependencies": true
  },
  "environments": {
    "dev": {
      "dependencySettings": {
        "installDevDependencies": false
      }
    }
  }
}
EOF

    cat > "${nested1}/lucee.json" << 'EOF'
{
  "name": "nested1",
  "dependencies": {
    "nested2": {
      "source": "git",
      "installPath": "dependencies/nested2"
    }
  }
}
EOF

    cat > "${nested2}/lucee.json" << 'EOF'
{
  "name": "nested2",
  "dependencies": {
    "nested3": {
      "source": "git",
      "installPath": "dependencies/nested3"
    }
  }
}
EOF

    cat > "${nested3}/lucee.json" << 'EOF'
{
  "name": "nested3",
  "dependencies": {
    "nested4": {
      "source": "git",
      "installPath": "dependencies/nested4"
    }
  }
}
EOF

    cat > "${nested4}/lucee.json" << 'EOF'
{
  "name": "nested4",
  "dependencies": {}
}
EOF

    printf '%s\n' "${root_project}"
}

create_deps_hooks_project() {
    local project_dir
    project_dir="$(mktemp -d "${BATS_TEST_TMPDIR}/deps-hooks-project.XXXXXX")"

    cat > "${project_dir}/lucee.json" << 'EOF'
{
  "name": "deps-hooks-project",
  "dependencies": {},
  "events": {
    "before": {
      "depsInstall": [
        "touch .before-deps-install-hook"
      ]
    },
    "after": {
      "depsInstall": [
        "touch .after-deps-install-hook"
      ]
    }
  }
}
EOF

    printf '%s\n' "${project_dir}"
}

@test "deps dry-run shows root install plan without nested blocks" {
    local root_project
    root_project="$(create_nested_deps_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install --dry-run' _ "${root_project}" "${LUCLI_JAR}"
    assert_success
    assert_output_contains "Would install:"
    [[ "${output}" != *"[Nested:"* ]]
}

@test "deps dry-run include-nested-deps shows nested project blocks" {
    local root_project
    root_project="$(create_nested_deps_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install --dry-run --include-nested-deps' _ "${root_project}" "${LUCLI_JAR}"
    assert_success
    assert_output_contains "[Nested: dependencies/nested1"
    assert_output_contains "[Nested: dependencies/nested1/dependencies/nested2"
}

@test "deps dry-run include-nested-deps with --env=dev is lenient for nested projects" {
    local root_project
    root_project="$(create_nested_deps_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install --dry-run --include-nested-deps --env=dev' _ "${root_project}" "${LUCLI_JAR}"
    assert_success
    assert_output_contains "Environment 'dev' not found in nested project"
}

@test "deps include-nested-deps reports max nested depth warning" {
    local root_project
    root_project="$(create_nested_deps_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install --dry-run --include-nested-deps' _ "${root_project}" "${LUCLI_JAR}"
    assert_success
    assert_output_contains "maximum nested dependency depth"
}

@test "deps install runs before/after depsInstall lifecycle hooks" {
    local project_dir
    project_dir="$(create_deps_hooks_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install' _ "${project_dir}" "${LUCLI_JAR}"
    assert_success
    assert_output_contains "No git or extension dependencies to install"
    [[ -f "${project_dir}/.before-deps-install-hook" ]]
    [[ -f "${project_dir}/.after-deps-install-hook" ]]
}

@test "deps install --dry-run does not run depsInstall lifecycle hooks" {
    local project_dir
    project_dir="$(create_deps_hooks_project)"

    run bash -c 'cd "$1" && java -jar "$2" deps install --dry-run' _ "${project_dir}" "${LUCLI_JAR}"
    assert_success
    [[ ! -f "${project_dir}/.before-deps-install-hook" ]]
    [[ ! -f "${project_dir}/.after-deps-install-hook" ]]
}