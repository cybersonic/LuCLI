# Changelog

All notable changes to this project will be documented in this file.

## Unreleased
- **Runtime Providers:** LuCLI now supports pluggable server runtimes via the new `runtime` key in `lucee.json`. Choose how your Lucee server runs:
  - **Lucee Express** (default) — same as before, no config change needed.
  - **Vendor Tomcat** — point LuCLI at your own Tomcat installation with `"runtime": {"type": "tomcat", "catalinaHome": "/path/to/tomcat"}`. LuCLI validates the installation, checks Lucee/Tomcat version compatibility, and creates an isolated per-server instance. Also supports the `CATALINA_HOME` environment variable as a fallback.
  - **Docker** *(experimental)* — run Lucee in a Docker container with `"runtime": "docker"`. Automatically pulls the `lucee/lucee` image, maps ports and volumes, and manages the container lifecycle. `server stop`, `server status`, and `server list` all work with Docker containers.
- **Runtime Configuration in lucee.json:** Supports both string shorthand (`"runtime": "docker"`) and object form (`"runtime": {"type": "tomcat", "catalinaHome": "..."}`) with optional fields for Docker image, tag, and container name. Updated `lucee.schema.json` with the new schema.
- **Fix: Server Status with Tomcat:** Fixed a bug where `server status` would report a running server as "NOT RUNNING" because the PID file was being overwritten during Tomcat startup.
- **Documentation:** Added runtime providers guide covering all three providers with configuration examples and a manual QA test guide.
- **New Aliases** Added plural aliases for better discoverability: `lucli server` → `lucli servers`, `lucli module` → `lucli modules`, `lucli secret` → `lucli secrets`.
- **Reusable Table UI Component:** Added `org.lucee.lucli.ui.Table` class for consistent CLI table rendering across commands. Features include multiple title rows, auto-sizing columns, full-width separators, optional footer, and configurable border styles (BOX, ASCII, NONE). Uses builder pattern for fluent API.
- **ModuleConfig Loader:** Added `org.lucee.lucli.modules.ModuleConfig` class that loads `module.json` once with sensible defaults. Eliminates redundant JSON parsing (was reading file 3x per module) and provides status detection (DEV/INSTALLED/AVAILABLE).
- **Modules Refactoring:** Moved module commands to `cli/commands/modules/` subpackage. Refactored `ModulesListCommandImpl` to use new Table and ModuleConfig classes for cleaner, more maintainable code.
- **Code Cleanup:** Removed unused imports across codebase, fixed WindowsSupport statics, general import organization.
- **Emoji Support Refactoring:** Created new `EmojiSupport` class with centralized global emoji control. Single `showEmojis` setting in `~/.lucli/settings.json` now affects all output. Use `EmojiSupport.process()` to replace emojis with text fallbacks when disabled. Simplified `PromptConfig` and `Terminal` to use this unified approach.
- **Fix: Prompt Display Bug:** Fixed broken Unicode regex patterns that were stripping letters from paths in prompts (e.g., showing `//` instead of `~/Code/LuCLI`).
- **Fix: Environment Configuration Merge:** Fixed issue where environment-specific config in `lucee.json` (e.g., `--env=prod`) would revert to defaults instead of properly overriding base config values. Now reads raw JSON from file for correct deep merging.
- **Fix: Terminal Exception Handling:** Added general exception handler to the REPL loop so uncaught exceptions display an error message and continue instead of crashing the terminal.
- **Fix: Tab Completion for File Paths:** Fixed tab completion for `ls`, `cat`, `cd`, `head`, `tail`, etc. - completion now triggers correctly after typing a command with a trailing space. Also fixed `~/` path completion to preserve the tilde prefix instead of expanding to the full home path.
- **External Command File Completion:** Added file path completion as fallback for external commands like `code`, `vim`, `open`, etc.
- **Welcome Page:** When starting a Lucee server with `enableLucee=true` (the default), LuCLI now automatically creates a welcome `index.cfm` in the webroot if one doesn't exist. The page displays server info, helpful commands, and links to documentation. Existing `index.cfm` files are never overwritten.
- **Fix:** resolved an issue after refactoring where the values are not returned from the `executeLucliScriptCommand` method, causing CFML command outputs to not be displayed in the terminal. The method now returns the command output as a string, which is printed to the terminal if not empty.

## 0.1.293
- **REPL Command:** Added `lucli repl` command for an interactive CFML read-eval-print loop. Provides a focused CFML-only environment for quick experimentation with history support, separate from the full terminal mode.
- **Script Preprocessor:** New `LucliScriptPreprocessor` for `.lucli` script files with support for:
  - Line continuation using backslash (`\`) at end of line
  - Comment stripping (lines starting with `#`)
  - Environment conditional blocks (`#@env:dev ... #@end`, `#@prod`, `#@env:!prod`)
  - Secret resolution (`${secret:NAME}`)
  - Placeholder substitution
- **Environment Flag:** Added `--env`/`-e` option to set the execution environment (dev, staging, prod) for `.lucli` scripts. Also reads from `LUCLI_ENV` environment variable as fallback.
- **Code Refactoring:** Major internal refactoring of LuCLI main entry point and command handling:
  - Merged LuCLICommand into LuCLI to eliminate code duplication
  - Cleaned up main() method and moved routing logic for better maintainability
  - Added cleaner output methods with format string support
  - Fixed deprecated method names after refactoring
- **Documentation:** Added blog layout templates and navigation components. Added TODO document for picocli enhancements and remaining refactoring tasks.
- **Git Dependency Caching:** Added persistent git dependency cache under `~/.lucli/deps/git-cache` (configurable via `usePersistentGitCache`) and a new `lucli deps prune` command to clear cached git clones.

## 0.1.266
- **Fixing build process:** Corrected the release workflow to add the expected "static" lucee.json

## v0.1.265
- **Server Sandbox Mode:** Added a `--sandbox` option to `lucli server run` to start a transient foreground server without creating or modifying `lucee.json` and without persisting the server instance after shutdown.
- **Run Commands from Modules:** Added `executeCommand` method to `BaseModule.cfc` to allow modules to programmatically execute LuCLI commands.
- **Env File Configuration & Tomcat Environment:** Added `envFile` and `envVars` top-level keys to `lucee.json` so projects can control which env file is loaded for `${VAR}` substitution and explicitly pass selected variables through to the Tomcat process environment. You can see usage examples in the updated [Environment Variables documentation](documentation/ENVIRONMENT_VARIABLES.md).
- **Dependency Install Reliability:** Improved `deps install` and local repository behavior when using env-driven configuration and custom repositories.


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
