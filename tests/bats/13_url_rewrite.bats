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
    mktemp -d "${BATS_TEST_TMPDIR}/url-rewrite.XXXXXX"
}

wait_for_http_200() {
    local url="$1"
    local max_seconds="${2:-120}"
    local elapsed=0
    local http_code

    while [[ "${elapsed}" -lt "${max_seconds}" ]]; do
        http_code="$(curl -s -o /dev/null -w "%{http_code}" "${url}" || true)"
        if [[ "${http_code}" == "200" ]]; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    return 1
}

@test "server start with urlRewrite.enabled=true installs RewriteValve and routes /hello through index.cfm" {
    if ! command -v curl >/dev/null 2>&1; then
        skip "curl is required for URL rewrite integration verification"
    fi

    local project_dir
    local server_name
    local port
    local server_xml
    local rewrite_config
    local routed_response
    local direct_response

    project_dir="$(create_project_dir)"
    server_name="url-rewrite-${RANDOM}"
    port="$(find_available_test_port)"

    cat > "${project_dir}/lucee.json" <<EOF
{
  "name": "${server_name}",
  "port": ${port},
  "openBrowser": false,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
EOF

    cat > "${project_dir}/index.cfm" <<'EOF'
<cfscript>
pathInfo = cgi.path_info ?: "";
writeOutput("ROUTED_OK|" & pathInfo);
</cfscript>
EOF

    cat > "${project_dir}/direct.cfm" <<'EOF'
<cfoutput>DIRECT_CFM_OK</cfoutput>
EOF

    run_lucli server start "${project_dir}"
    assert_success
    assert_output_contains "Server started successfully"
    assert_output_contains "${server_name}"

    if ! wait_for_http_200 "http://127.0.0.1:${port}/" 120; then
        false
    fi

    server_xml="${LUCLI_HOME}/servers/${server_name}/conf/server.xml"
    rewrite_config="${LUCLI_HOME}/servers/${server_name}/conf/Catalina/localhost/rewrite.config"
    [ -f "${server_xml}" ]
    [ -f "${rewrite_config}" ]
    run grep -q "org.apache.catalina.valves.rewrite.RewriteValve" "${server_xml}"
    assert_success

    routed_response="$(curl -fsS "http://127.0.0.1:${port}/hello")"
    [[ "${routed_response}" == *"ROUTED_OK|/hello"* ]]

    direct_response="$(curl -fsS "http://127.0.0.1:${port}/direct.cfm")"
    [[ "${direct_response}" == *"DIRECT_CFM_OK"* ]]

    run_lucli server stop --name "${server_name}"
    assert_success
}
