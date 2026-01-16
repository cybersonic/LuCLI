# Changelog

All notable changes to this project will be documented in this file.

## Unreleased
- **Server Sandbox Mode:** Added a `--sandbox` option to `lucli server run` to start a transient foreground server without creating or modifying `lucee.json` and without persisting the server instance after shutdown.
- **Run Commands from Modules:** Added `executeCommand` method to `BaseModule.cfc` to allow modules to programmatically execute LuCLI commands.
- **Env File Configuration & Tomcat Environment:** Added `envFile` and `envVars` top-level keys to `lucee.json` so projects can control which env file is loaded for `${VAR}` substitution and explicitly pass selected variables through to the Tomcat process environment.
- **Example: server-envvars:** New `examples/server-envvars/` example demonstrating `envFile`, `envVars`, and inspection of environment variables from within Lucee via `GetEnvironmentVariable()`.

## 0.1.258
- **Secrets Management:** Added first-class secrets management support with safeguards to prevent accidental leakage in logs, dry-run output, and documentation examples. Includes automatic checks for hard-coded secrets and improved validation of sensitive configuration values.
- **Server Configuration Editor:** Introduced an interactive server configuration editor and new `server new`/`server edit` style commands for creating and modifying Lucee server configurations without manually editing `lucee.json`. Centralized configuration handling in a new `ServerConfigHelper` for more consistent behavior.
- **Configuration Locking:** Added configuration lock behavior and tests to ensure locked server configurations cannot be modified unintentionally, improving safety for shared or production server configs.
- **Lucee Enable/Disable Flags:** Added `--enable-lucee` and `--disable-lucee` options to `lucli server start` so you can explicitly turn Lucee on or off per run without changing the underlying configuration file.
- **Server Open Command & Webroot Override:** Added a `lucli server open` command to quickly open the active server in a browser, plus support for overriding the webroot at startup for temporary testing setups.
- **Terminal Completion Enhancements:** Improved terminal autocompletion to include new server commands and options, as well as better coverage of existing commands in both CLI and interactive terminal modes.
- **Local Repository Improvements:** Added an initial local repository implementation to support more flexible module and project workflows (e.g., local repositories for tools like markspresso), laying groundwork for richer dependency and module sources.
- **Documentation Updates:** Expanded and corrected documentation, including new content docs and fixes to the generated command reference and completion markdown for more accurate and readable help output.
- **Internal Refactoring:** Refactored internal classes to keep public APIs and high-level logic at the top of key classes, improving readability and maintainability without changing user-facing behavior.

## 0.1.229
- **Dependency Management:** Added `deps` and `install` CLI commands for managing project dependencies. New dependency infrastructure with `dependencySettings` configuration.
- **Server Run Command:** New `server run` command for running Lucee server in foreground mode (non-daemon), useful for Docker containers and debugging.
- **Server Configuration Improvements:** Added AJP connector configuration support with ability to disable via `ajp.enabled=false`. Enhanced mapping computation for server startup. Additional server start command options now available.
- **Docker Support:** Added Docker documentation and examples with improved default user configuration in Dockerfile.
- **Server Prune Enhancement:** Added confirmation prompt and `--force` flag to `server prune` command for safer cleanup operations.
- **Version Display:** Enhanced version message output with improved formatting.
- **Architecture Refactoring:** Major refactoring to simplify architecture - consolidated terminal implementations, migrated commands to direct Picocli implementation, improved code quality. Added naming conventions documentation.
- **Command Timing:** Added execution timing information to all Picocli commands for performance monitoring.
- **Lucee Extension Support:** Added comprehensive extension dependency management with name-based resolution via extension registry. Extensions can be defined in `lucee.json` dependencies using friendly names (e.g., "h2", "redis", "postgres") instead of UUIDs. Supports both automatic ID-based installation via `LUCEE_EXTENSIONS` environment variable and direct .lex file deployment to server deploy folder.
- **Extension Registry:** Created `ExtensionRegistry` with JSON-based mapping of 24+ common extensions (databases, cache, core utilities) from friendly names/slugs to UUIDs. Registry located at `src/main/resources/extensions/lucee-extensions.json` with comprehensive documentation.
- **Dependency Installer:** New `ExtensionDependencyInstaller` handles extension installation and deployment. Extensions with URL/path are deployed to `{serverDir}/lucee-server/deploy/` folder. Lock file tracks extension IDs and sources.
- **Alternate Configuration Files:** Added `-c`/`--config` option to `server start` and `server run` commands for using alternate configuration files (e.g., `lucli server start -c lucee-prod.json`).
- **Stop All Servers:** Added `--all` flag to `server stop` command to stop all running servers at once: `lucli server stop --all`. Displays progress and summary of stopped servers.
- **Bug Fixes:** Renamed `LuceLockFile` to `LuceeLockFile` (corrected spelling).
- **Examples:** New `examples/dependency-extensions/` and `examples/server-environments/` demonstrating extension usage and environment configurations. Added `examples/dependency-git-subpath/` for dependency documentation. Archived old examples.
- **Testing:** Removed obsolete test scripts and updated .gitignore.

## 0.1.194
- **Dry-Run Enhancements:** Added `--include-lucee` flag to `server start --dry-run` for previewing resolved `.CFConfig.json` before deployment. Shows merged configuration from `configurationFile` and inline `configuration` with environment variable substitution applied.
- **Documentation:** New comprehensive guides for [Dry-Run Preview System](documentation/DRY_RUN_PREVIEW.md) and [Environment Variables](documentation/ENVIRONMENT_VARIABLES.md). Updated main documentation index with quick-start links.
- **Testing:** Enhanced test suite with dry-run validation tests for configuration preview functionality.

## 0.1.192
- **Lucee Admin Password** Added lucee admin configuration in `admin.enabled` and `admin.password` keys of lucee.json
- **HTTPS Support:** Added full HTTPS support with per-server keystores, automatic certificate generation, and HTTP→HTTPS redirect. Fixed Tomcat configuration to use modern `SSLHostConfig` nested element structure. Includes optional `host` field for custom domains and `--dry-run` preview flags (`--include-https-keystore-plan`, `--include-https-redirect-rules`, `--include-all`).
- **Configuration Management:** New `server get` and `server set` commands with dot notation support for reading/writing configuration values. Includes `--dry-run` flag for previewing changes. Added environment variable substitution with `${VAR_NAME}` and `${VAR_NAME:-default}` syntax, automatic `.env` file loading, and virtual read-only `serverDir` key.
- **Server Management Improvements:** Smart shutdown port auto-assignment (HTTP port + 1000 with conflict detection), comprehensive port conflict checking (HTTP, HTTPS, JMX, shutdown), `server list --running` filter, and enhanced startup output with server directory path.
- **Tomcat Configuration:** `--dry-run` mode now shows patched `server.xml` and `web.xml` (not just vendor templates) via `--include-tomcat-web` and `--include-tomcat-server` flags. Support for passing arbitrary Lucee server options through `server start`.
- **Module System Enhancements:** Module repository support with bundled local index and external repositories via `~/.lucli/settings.json`. Git-aware module protection for development modules. Modules can now accept Picocli @Option annotations for parameters. Enhanced module initialization with `--git` flag.
- **Lucee CFConfig Integration:** Improved handling with deep JSON merging - `configurationFile` loads as base, inline `configuration` overrides/extends. Enables shared base configs with per-project customization.
- **Shell & Terminal:** Migrated to picocli's built-in `AutoComplete` generator (bash + zsh). Enhanced terminal with name display, Timer utility for script execution, and improved argument parsing (`--argument=value`, `argument=value`, `--no-argument`).
- **Module System:** Added output and background colors to BaseModule. Removed BaseModule functions from Module.cfc template. Improved module repository list view.
- **Platform Updates:** Updated to Lucee 7.0.1.93-SNAPSHOT and Java 21. Updated documentation and `lucee.schema.json` with `enableLucee=true` default. New `documentation/ENVIRONMENT_VARIABLES.md`.

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
