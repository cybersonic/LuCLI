# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

- **Shell Completion:** Migrated shell completion to picocli's built-in `AutoComplete` script generator (bash + zsh via `bashcompinit`). Removed the custom `__complete` endpoint and related completion provider classes. Lucee version completion remains dynamic via `lucli versions-list` (cached for 24 hours).
- **Tomcat Configuration Loading:** Refactored `--include-tomcat-web` and `--include-tomcat-server` flags in `--dry-run` mode to load vendor `web.xml` / `server.xml` from the Lucee Express distribution and then apply LuCLI’s XML patchers in-memory. This means the preview shows the **patched** config (ports, docBase, Lucee enable/REST toggles, HTTPS connector/RewriteValve, etc.) without writing any files to disk.
- **Configuration Management:** Add `server get <key>` command to retrieve configuration values with dot notation support (e.g., `server get jvm.maxMemory`). Add `server set <key=value>` command to set multiple configuration values in one command (e.g., `server set port=8080 admin.enabled=false`). Both commands support `--dry-run` flag to preview changes without saving. Unknown configuration keys trigger warnings but are still set for forward compatibility.
- **Environment Variable Substitution:** Support `${VAR_NAME}` and `${VAR_NAME:-default_value}` syntax for environment variable replacement in `lucee.json`. Substitution applies to all string fields including nested JSON in the `configuration` object. Variables are resolved from `.env` file (highest priority), then system environment variables, then defaults, then placeholders remain unchanged.
- **Automatic .env File Loading:** LuCLI automatically loads `.env` file from the project directory (same location as `lucee.json`) with variables taking precedence over system environment variables. Supports standard `.env` format with KEY=VALUE pairs, comments, and quoted values. Non-existent `.env` files are silently ignored.
- **Virtual `serverDir` Configuration Key:** Add read-only `serverDir` key that returns `~/.lucli/servers/<server-name>` for script access to server directory path.
- **Server Output Enhancements:** Display server directory path in startup output. Add `--dry-run` flag to `server start` to preview final configuration with all variables substituted and defaults applied without starting the server. New flags `--include-tomcat-web` and `--include-tomcat-server` allow previewing the generated Tomcat `web.xml` and `server.xml` configuration files during `--dry-run` (e.g., `lucli server start --dry-run --include-tomcat-web --include-tomcat-server`).
- **HTTPS Support (Per-server, minimal config):** Added optional `https` block (and `host`) to `lucee.json`.
  - Enable with `https.enabled=true` (e.g., `lucli server config set https.enabled=true`).
  - Defaults to HTTPS port 8443 and HTTP→HTTPS redirect (unless explicitly disabled).
  - Uses a per-server PKCS12 keystore under `~/.lucli/servers/<name>/certs/` so servers remain self-contained for packaging.
  - Adds `host` to control the default hostname used in generated URLs and certificate SANs.
  - Added new `--dry-run` preview flags: `--include-https-keystore-plan` and `--include-https-redirect-rules`.
  - Added `--include-all` as a convenience to enable all dry-run preview outputs.
- **Smart Shutdown Port Auto-Assignment:** Automatically assign shutdown port if not specified in `lucee.json`, defaulting to HTTP port + 1000. If that port is taken by other servers or OS processes, search 9000-9999 range for availability. Respects explicit `shutdownPort` values in configuration.
- **New Documentation:** Add `documentation/ENVIRONMENT_VARIABLES.md` covering environment variable substitution, `.env` file usage, supported fields, best practices, and troubleshooting.
- Added documentation for lucee.json and upated the schema for `enableLucee=true` by default.
- Updated to lucee 7.0.1.93-SNAPSHOT and Java 21.
- Enable both `--argument=something` and `argument=something` as well as converting `--argument` to `argument=true` and `--no-argument` to `argument=false` in LuCLI one-shot command execution for better compatibility with various shells and scripts.
- Added Timer utility to lucli file execution. 
- Adapted Timer utility to print out columns correctly
- Lucli modules now accept any parameters via Picocli @Option annotations.
- Adding unmatched commands to server start command so that we can pass through arbitrary lucee server options.
- Updating checking of all ports for conflicts before starting server, including HTTP, JMX, and shutdown ports.
- Adding output and backgound colours to the BaseModule for output. 
- Removed BaseModule functions from the Module.cfc template
- updated tests/test.sh test script
- Added name to the terminal!
- added `server list --running` option to list only running servers.
- Protect development modules under `~/.lucli/modules/<name>` from being overwritten by `lucli modules install` and `lucli modules update` when a `.git` directory is present.
- Enhance `lucli module(s) init` to optionally initialize a Git repository in the new module directory (via `--git` / `--no-git` flags and an interactive prompt), so dev modules are automatically marked and protected.
- Add a bundled local module repository index at `src/main/resources/repository/local.json` and use it to augment `lucli modules list` output with modules that are available but not yet installed, marking installed modules with a success indicator.
- Add support for external module repositories configured in `~/.lucli/settings.json` under `moduleRepositories`, which are merged into the `lucli modules list` view.
- Improve CFConfig handling in `lucee.json`: `configurationFile` (external JSON file) is now loaded first as a base configuration, and inline `configuration` values override/extend it. This enables shared base CFConfigs with per-project customization via deep JSON merging.

## 0.1.121

- Add first-class Java agent support for Lucee servers via `lucee.json`:
  - New optional `agents` section in `lucee.json` to declare named Java agents with `enabled`, `jvmArgs`, and `description` fields.
  - `lucli server start` now accepts `--agents`, `--enable-agent`, `--disable-agent`, and `--no-agents` flags to toggle agents per run without editing `lucee.json`.
  - JVM options are assembled in a stable order: memory settings, JMX flags, active agents' `jvmArgs`, then existing `jvm.additionalArgs`.
- Fix interactive terminal autocomplete behavior to provide more accurate and reliable command suggestions.
- Improve `ls` output alignment when using emoji prefixes:
  - Column width calculation now accounts for the `emoji + space + name` display string, keeping columns consistent.
  - Script and executable files now use a more consistently rendered emoji to reduce visual drift between columns.
- Refactor the interactive terminal to use the Picocli + JLine3 integration (`InteractiveTerminalV2`), unifying CLI and terminal command handling and improving error reporting.
- Expand one-shot execution support so `lucli` can run CFML scripts, CFML components, LuCLI modules, and `.lucli` batch scripts with consistent argument handling and built-in variables.
- Enhance module management:
  - Add richer `lucli modules` support for listing, initializing, installing, updating, and uninstalling modules under `~/.lucli/modules`.
  - Introduce `module.json` metadata and public `module.schema.json` for editor tooling and validation.
  - Add language and template example modules under `src/main/resources/modules/`.
- Improve server configuration and security:
  - Add XML-based patchers for `server.xml` and `web.xml` so LuCLI can adjust the HTTP connector port and ROOT context docBase to match `lucee.json`.
  - Automatically add a `security-constraint` to prevent `lucee.json` from being served over HTTP.
  - Extend `lucee.json` with `urlRewrite`, `admin`, `configuration`, and `configurationFile` options, plus a published `lucee.schema.json` for validation.
- Add new documentation:
  - `documentation/EXECUTING_COMMANDS.md` covering CFML scripts, components, modules, and `.lucli` batch scripts.
  - `documentation/SERVER_AGENTS.md` explaining Java agent configuration and server start flags.
  - `documentation/SERVER_TEMPLATE_DEVELOPMENT.md` describing Tomcat template generation and conditional blocks for URL rewriting and admin routes.
  - Additional documentation updates and examples for configuration and URL rewriting.
