#!/usr/bin/env bats

load './test_helper.bash'

setup_file() {
    require_lucli_artifacts
}

setup() {
    setup_lucli_home
}

teardown() {
    stop_all_servers_if_possible
    cleanup_lucli_home
}

create_project_dir() {
    mktemp -d "${BATS_TEST_TMPDIR}/admin-security.XXXXXX"
}

@test "server start with admin.enabled=false removes admin servlet mapping and adds deny constraint" {
    local project_dir
    local server_name
    local port
    local server_web_xml
    local compact_xml

    project_dir="$(create_project_dir)"
    server_name="admin-disabled-${RANDOM}"
    port="$(find_available_test_port)"

    cat > "${project_dir}/lucee.json" <<EOF
{
  "name": "${server_name}",
  "port": ${port},
  "openBrowser": false,
  "admin": {
    "enabled": false
  }
}
EOF

    run_lucli server start "${project_dir}"
    assert_success
    assert_output_contains "Server started successfully"
    assert_output_contains "${server_name}"

    server_web_xml="${LUCLI_HOME}/servers/${server_name}/conf/web.xml"
    [ -f "${server_web_xml}" ]

    compact_xml="$(tr -d '\n\r\t ' < "${server_web_xml}")"

    [[ "${compact_xml}" != *"<servlet-mapping><servlet-name>CFMLServlet</servlet-name><url-pattern>/lucee/admin.cfm</url-pattern></servlet-mapping>"* ]]
    [[ "${compact_xml}" == *"<url-pattern>/lucee/admin.cfm</url-pattern>"* ]]
    [[ "${compact_xml}" == *"<auth-constraint"* ]]

    run_lucli server stop --name "${server_name}"
    assert_success
}
