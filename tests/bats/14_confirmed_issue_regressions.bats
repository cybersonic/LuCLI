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

@test "issue #7: run command executes .cfs script with shebang" {
    local script_path
    script_path="$(mktemp "${BATS_TEST_TMPDIR}/shebang-script.XXXXXX.cfs")"
    cat > "${script_path}" << 'EOF'
#!/usr/bin/env lucli
writeOutput("shebang-script-ok");
EOF

    run_lucli run "${script_path}"
    assert_success
    assert_output_contains "shebang-script-ok"
}

@test "issue #59: aliased module subcommand help is forwarded to module showHelp" {
    local module_name module_dir
    module_name="help_route_module_${BATS_TEST_NUMBER}"
    module_dir="${LUCLI_HOME}/modules/${module_name}"

    run_lucli modules init "${module_name}" --no-git
    assert_success

    cat > "${module_dir}/Module.cfc" << 'EOF'
component {
    function main() {
        writeOutput("main");
    }

    function showHelp(string subcommand = "") {
        if (len(trim(arguments.subcommand))) {
            writeOutput("SUBCOMMAND_HELP:" & arguments.subcommand);
        } else {
            writeOutput("GLOBAL_HELP");
        }
    }
}
EOF

    run java -Dlucli.binary.name="${module_name}" -jar "${LUCLI_JAR}" migrate --help
    assert_help_exit_code
    assert_output_contains "SUBCOMMAND_HELP:migrate"
}

@test "issue #44: build.sh install target resolves active lucli path first" {
    run grep -E '^INSTALL_TARGET=\$\(which lucli 2>/dev/null \|\| echo "\$HOME/\.local/bin/lucli"\)$' "${LUCLI_ROOT_DIR}/build.sh"
    assert_success
}

@test "issue #44: build.sh install copies binary to resolved INSTALL_TARGET" {
    run grep -E '^    cp target/lucli "\$INSTALL_TARGET"$' "${LUCLI_ROOT_DIR}/build.sh"
    assert_success
}

@test "issue #71: pom configures JReleaser plugin to use jreleaser.yml" {
    run grep -F '<configFile>${project.basedir}/jreleaser.yml</configFile>' "${LUCLI_ROOT_DIR}/pom.xml"
    assert_success
}

@test "issue #71: release workflow invokes jreleaser with explicit config file" {
    run grep -F 'mvn -DskipTests=true -Djreleaser.config.file=jreleaser.yml jreleaser:full-release' "${LUCLI_ROOT_DIR}/.github/workflows/release.yml"
    assert_success
}
