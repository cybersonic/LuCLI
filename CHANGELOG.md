# Changelog

All notable changes to this project will be documented in this file.

## Unreleased
- **Version Output Includes Java Runtime:** `lucli --version` now includes a `Java Version:` line alongside LuCLI and Lucee versions, sourced from the active Java runtime (`java -version`) with a runtime-property fallback for portability.
- **Fix: `.lucli` Leading `lucli` Prefix Now Preserves Quoted Arguments:** Script-line normalization no longer rebuilds commands from parsed tokens when stripping an initial `lucli` prefix. We now remove only the leading command token from the raw line, preserving quoted values like `name=\"Mark Drew\"` for module and `run` invocations. Added regression test coverage for quoted argument preservation.
- **Fix: OpenAI AI Config Timeout Compatibility:** `lucli ai config add` now omits `custom.timeout` for OpenAI-compatible providers (`openai`, `copilot`, `deepseek`, `grok`, `ollama`, `perplexity`, `other`) because newer OpenAI-compatible APIs reject it as an unsupported request argument. When `--timeout` is provided for those providers, LuCLI now warns that the value is ignored.
- **Admin Disabled Security Env Enforcement:** When `admin.enabled=false`, LuCLI now enforces `LUCEE_ADMIN_ENABLED=false` in runtime process environments and Tomcat `setenv.sh`/`setenv.bat` generation, and surfaces it in dry-run `--include-env` previews.
- **Fix: `.lucli` Command Failures Now Exit Non-Zero:** `.lucli` scripts now propagate subcommand exit codes (for example, `run` failures) so command errors no longer end with an overall success exit status. Added BATS regression coverage.
- **Fix: `cfml` Command Exits Non-Zero on Failure:** `lucli cfml '<expr>'` now exits with code 1 when the expression throws. Previously, the catch block logged the error and fell through to `return result` with `result == null`, which picocli maps to exit 0. Callers checking exit codes (shell pipelines, CI jobs, pre-commit hooks, doctest harnesses) now correctly see failure.
- **Fix: `JAVA_HOME` Preflight Before Server Start:** `lucli server start` (and `wheels start`) now detects missing or broken `JAVA_HOME` before the child JVM is spawned, and prints an actionable error pointing at Adoptium. Previously the child exited silently and the parent reported a misleading "port conflicts detected" error, forcing users to hunt through `~/.wheels/servers/<name>/server.err` to find the real cause. The preflight now runs against the effective child-process environment (parent shell + `.env` file + `lucee.json` `envVars`), so project-level `JAVA_HOME` overrides are honored and the check won't false-positive when the var is defined in project config but not the parent shell.
- **Module Dispatch: Auto-Bind Positional Args to Typed Params:** LuCLI's module dispatch now maps CLI positional args (`arg1`, `arg2`, ...) to a target function's typed parameters by declaration order. Module authors can declare typed signatures like `function greet(required string name, boolean verbose = false)` and get rich MCP JSONSchema (via existing auto-discovery), MCP named-arg binding (standard CFML `argumentCollection`), and CLI positional binding (this change) all from a single source of truth. Named args and already-populated typed params win over positional fallback; excess positional args stay in argCollection for variadic access. Closes the last remaining "write a per-function arg-parsing shim" pattern for LuCLI module authors.
- **Lockfile-Independent Extension Activation:** Server runtime extension activation no longer requires `dependencySettings.useLockFile=true`. `server start`/`server run` now derive extension deployment and `LUCEE_EXTENSIONS` from `lucee.json` dependencies when lockfiles are disabled (including dry-run `--include-env` previews), with lockfile entries still used when enabled/available.
- **Deps Install Materializes Extension Files by Default:** `lucli deps install` now copies/downloads `type: "extension"` dependencies with `path`/`url` into their `installPath` during install (instead of only recording metadata). Added `dependencySettings.materializeExtensionsOnInstall` (default `true`) to opt out and keep metadata/cache-based behavior.
- **BATS Coverage for Local Extension Deployment:** Added an integration lifecycle test that creates a fake local `.lex` dependency, runs `lucli deps install`, starts the server, and verifies the extension is deployed under `~/.lucli/servers/<server>/lucee-server/deploy/`.
- **CI Test Result Publishing Permissions:** Updated GitHub Actions test jobs to grant `pull-requests: write` and `issues: write` so `EnricoMi/publish-unit-test-result-action` can post PR comments without failing Linux unit/integration jobs with `403 Resource not accessible by integration`.
- **Server Runtime Prewarm Flag (`--prewarm`):** Added `--prewarm` support to both `lucli server start` and `lucli server run` to pre-download runtime artifacts and exit without starting a server. LuCLI now prewarms Lucee Express for `lucee-express` runtime, prewarms Lucee JARs for `tomcat`/`jetty` runtimes, and reports a no-download message for `docker` runtime.
- **Server Header Shows Generated Lucee Config Path:** When server startup writes `.CFConfig.json`, LuCLI now prints `Generated lucee config in: <path>` alongside existing startup header lines so the effective Lucee config file location is explicit.
- **Fix: Profile-Aware Home Directory in Script Engine:** `LuceeScriptEngine.getLucliHomeDirectory()` now uses the active CLI profile's home directory instead of a hardcoded `~/.lucli` path. This ensures that `BaseModule.cfc` and other shared resources are provisioned to the correct home directory (e.g. `~/.wheels/modules/` when running as `wheels`).
- **Server Environment Fallback (`LUCLI_ENV`) + Docker Default:** `lucli server start` and `lucli server run` now fall back to `LUCLI_ENV` when `--env/--environment` is not provided, while still honoring explicit `--env` precedence. The Docker image now sets `LUCLI_ENV=""` by default so deployments can override it at runtime (for example `docker run -e LUCLI_ENV=prod ...`).
- **Server Warmup Flag (`--warmup`):** Added `--warmup` support to `lucli server start` and `lucli server run` to enable Lucee build-time warmup by injecting both `LUCEE_ENABLE_WARMUP=true` and `-Dlucee.enable.warmup=true` for that invocation (without persisting changes to `lucee.json`).
- **Fix: MCP Tool Output Capture:** `lucli mcp <module>` now correctly captures module `out()` / `err()` output into JSON-RPC response bodies. Previously, Lucee's `systemOutput()` bypassed `System.setOut()` redirection because it caches stream references at engine startup — so every MCP tool returned empty content to callers while output leaked to the terminal, contaminating the stdio protocol stream. `BaseModule` now uses `createObject("java", "java.lang.System").out.println(...)` for dynamic lookup that honors redirection.
- **MCP Tool Hiding:** Public functions inherited from `BaseModule` (`init`, `getEnv`, `getSecret`, `verbose`, `getAbsolutePath`, `executeCommand`, `version`, `showHelp`) are now automatically excluded from MCP `tools/list` responses. Modules can additionally declare specific commands to hide with a new `mcpHiddenTools()` convention:

    ```cfm
    public array function mcpHiddenTools() {
        return ["start", "stop", "console"];
    }
    ```

    Hidden tools remain reachable as CLI subcommands — this is purely an MCP-surface filter for commands that are stateful, interactive, or otherwise inappropriate for autonomous agent use.
- **Fix: BaseModule.cfc Dev Sync:** The shared `BaseModule.cfc` in the active profile's modules directory is now refreshed when its content differs from the JAR-bundled copy, instead of only when the LuCLI version number changes. Dev iterations that modify `src/main/resources/modules/BaseModule.cfc` now reach the installed copy without needing a formal version bump.
- **`executeModuleAndReturn()` on `LuceeScriptEngine`:** Framework callers can now invoke a module function and capture its return value without triggering the legacy print-if-non-null CLI behavior. Used internally by MCP tool introspection.
- **`#project:path#` Placeholder in Configuration:** Added `#project:path#` placeholder support for `lucee.json` configuration values (e.g. datasource DSNs). Resolved at server start alongside `#env:VAR#` and `#secret:NAME#`, replacing the token with the absolute project directory path.


## 0.3.3
- **Unit Test Coverage One-Shot Script:** Added `tests/unit-tests-coverage.sh` to run Maven unit tests with JaCoCo coverage from project root and print/open generated report paths (`target/site/jacoco/index.html`).
- **Fix: Gemini AI Config Template Output:** `lucli ai config add --type gemini` now emits the GeminiEngine-oriented template shape with provider-specific keys (`apikey`, `connectTimeout`, `socketTimeout`, `temperature`, `conversationSizeLimit`, and `beta`) and no default-mode field when left at the command default.
- **Fix: AI Provider Mapping for `ai config add`:** `lucli ai config add --type claude` / `--type gemini` now infer the correct Lucee engine classes when `--class` is omitted, emit provider-specific API key fields (`apiKey` vs `secretKey`), preserve OpenAI-compatible `type` handling, and mask/list both key formats consistently.
- **PR Review Workflow Templates + Script:** Added a new `pr-review/` workflow with reusable reviewer/synthesis prompts, baseline review rules, example pending-code context payload, and a LuCLI command script (`pr-review/review-pending-code.lucli`) that runs multi-agent review passes and produces a synthesized final report.
- **Server Slug-First Lifecycle Resolution + BATS Coverage:** Server lifecycle now treats server slug/name as the primary identity, with project path as a secondary one-to-many index. Project-scoped `status`, `stop`, and `prune` commands now fail with a clear ambiguity error when multiple server slugs are linked to the same project path (requiring `--name` or `--config`), `status`/`prune` now accept `--config` to resolve alternate `lucee*.json` names, and `prune --force` now attempts a managed stop before pruning. Added new BATS lifecycle tests validating start/status/stop flow, config-targeted stopping, and multi-slug ambiguity behavior.
- **BATS Migration Coverage Expansion:** Added BATS coverage for module install validation and module run execution, server dry-run preview flags (`--include-lucee`, `--include-tomcat-server`, `--include-tomcat-web`, HTTPS preview flags, and `--include-all`), extended environment merge assertions, computed dependency mappings in dry-run CFConfig output, server command-surface smoke checks, and binary/JAR version parity. Also hardened BATS temp home setup to work in both `setup` and `setup_file` scopes.
- **Fix: Whitespace Flag Semantics + Coverage:** Made whitespace preservation opt-in by default (`--whitespace` now explicitly enables preserved writer mode), corrected CFML writer mode mapping for the flag, and added deterministic BATS coverage that verifies `cfmlWriter` toggles between `regular` and `white-space`.
- **Fix: `.lucli` Commented Continuation Lines:** Fixed line-continuation parsing so `# ...` comment lines inside backslash-continued commands are ignored instead of being concatenated into the executed command.
- **AI Prompt Rough Cost Estimation:** Added `lucli ai prompt --estimate` to show a pre-send rough estimate (estimate-only mode, no provider request is sent) that breaks down composed text tokens, JSON envelope overhead tokens, and image heuristic tokens. Added optional `--assume-output-tokens`, `--input-price-per-1m`, and `--output-price-per-1m` to approximate total tokens and estimated USD cost with explicit warning language that billed usage may differ.
- **Updated install location for `./build.sh install`:** Changed the default install location to `~/.local/bin/lucli` for better compatibility with the curl installer and common Linux conventions. The script now checks for an existing `lucli` in the PATH and uses that location if found, otherwise it falls back to `~/.local/bin/lucli`.
- **Docs: README Status Badges + Contributing Guide:** Replaced placeholder README badges with live GitHub Actions workflow badges, refreshed key README links/version notes, and added a new `CONTRIBUTING.md` guide for contributor onboarding.
- **Testing Migration to BATS:** We are moving tests from legacy shell suites to BATS (`tests/bats/` and `tests/test-bats.sh`).
- **Block Direct `.cfc` Execution via `run`:** `lucli run <file>.cfc` (and equivalent shortcut invocation) is now blocked with a clear error message to avoid component pathing instability. Use module entry points via `lucli modules run <module>` instead.
- **Runtime CWD Sync + Local Component Resolution:** LuCLI now tracks effective runtime CWD across terminal and `.lucli` execution flows, refreshes Lucee `/cwd` mappings before eval, and fixes `cd` + `cfml new LocalComponent()` / relative `run` behavior in command scripts.
- **URL Rewrite Direct CFML Bypass:** Tomcat `rewrite.config` now preserves direct `.cfm` / `.cfc` / `.cfml` requests while still routing framework-style paths through the configured router file, with integration tests and harness cleanup updates to validate this behavior.
- **Docs: LuCLI Command Scripts Terminology:** Documentation now standardizes `.lucli` / `.luc` automation files as “LuCLI command scripts,” adds a dedicated guide, and updates cross-links from CFML execution and CLI basics pages.
- **One-Shot Server Overrides No Longer Persist to `lucee.json`:** `lucli server start` / `lucli server run` now apply invocation overrides (including bare `key=value`, `--port`, `--webroot`, and `--enable-lucee` / `--disable-lucee`) in memory for that run only, instead of mutating your project config file.
- **CFConfig Path Normalization + Legacy Compatibility:** Standardized server CFConfig handling to `~/.lucli/<server>/context/.CFConfig.json` (removing the duplicated `lucee-server` path segment), while still reading legacy locations when present and cleaning up old nested files/directories after migration.
- **Script Argument Variables (`ARGS`, `ARGV`, `__namedArguments`):** CFML scripts (`.cfs`, `.cfm`) now have access to `ARGS` (a struct with both numeric positional keys and named `key=value` entries), `ARGV` (an array with the script filename as element 0 followed by raw args), and `__namedArguments` (a map of only the named key=value arguments). The `__env` / `ENV` binding also now includes variables set via `set` in the parent `.lucli` script.
- **`set` Variable Propagation to External Commands:** Variables defined via `set VAR=value` in `.lucli` scripts are now merged into the environment of external child processes (e.g. shell commands, `env`, `curl`). Previously they were only visible to the embedded CFML engine context.
- **Fix: `lucli module init` Wizard Error:** Fixed an interactive prompt issue where module creation could fail with `❌ Error: No line found` after entering module details.
- **AI Config Quiet Mode:** Added `--quiet` to `lucli ai config add` to suppress printing imported config payload output, making CI logs safer.

## 0.2.23

 - **Bootstrap Installers + Download UX:** Added cross-platform bootstrap installers (`install.sh` and `install.ps1`) for one-line install flows (`curl ... | sh` and `irm ... | iex`), published as release assets, and documented in README/install docs. Updated the download page with copyable install commands, pinned-version examples, and automatic pinned-version updates from the latest release (with release workflow stamping).
 - **Adding JQ and Curl to docker image**  - this allows us to use common tools alongside with lucli in pipelines
 - **Fix: Module Install with `--rev` on Branch Names:** Fixed `modules install --rev <branch>` failing when git is not available. You can now install modules from both tags and branches in non-git environments.
 - **`.lucli` Output Redirection:** Added script-level output redirection support in `.lucli` files using `>` (overwrite) and `>>` (append), so command/module output can be routed directly to files during automation runs.
 - **Windows Bat File fixes**
 - **MCP Server for Modules:** Added `lucli mcp <module>` to run a per-module MCP server over stdio, exposing a module’s public functions as MCP tools (with JSON schema-derived input). 
 - **AI Command (`lucli ai`) Enhancements:**
   - Added `lucli ai` command group with provider config, prompting, testing, and skill-path management workflows.
   - Registered `AiCommand` at the root CLI command level so `lucli ai ...` works consistently in one-shot command mode.
   - Added AI command coverage in terminal help output (`ai config`, `ai config defaults`, `ai config add`, `ai config list`, `ai prompt`, `ai list`, `ai skill`).
   - Added guided provider setup via `lucli ai config add --guided` with optional `--test-after-save`.
   - Added `lucli ai config defaults` for default endpoint/model management, and made `lucli ai config` show subcommand help.
   - Added provider listing from Lucee CFConfig (`lucli ai list` / `lucli ai config list`) and compatibility handling for `--refresh`.
   - Added secret key masking by default in list/config output, with explicit `--show` to reveal full values when needed.
   - Updated prompt UX to support repeatable `--image`, rules via `--rules-file` / `--rules-folder`, and clearer instruction composition behavior.
   - Added `@file` support for `--text` so prompt task content can be loaded directly from files.
   - Added `lucli ai prompt --dry-run` to print the fully resolved prompt payload/composed question without sending a provider request.
   - Added `lucli ai prompt --output-file <path>` support to persist rendered prompt responses (including `--json` and dry-run output), with overwrite protection via `--force`.
   - Fixed `--system @file` / `--text @file` handling by disabling picocli `@argfile` expansion, so file paths are consumed literally and full file contents are loaded as intended.
 - **Module Runtime Permissions + Env/Secrets Resolution:**
   - Added module metadata keys `permissions.env` and `permissions.secrets` to declare runtime env/secret aliases.
   - Added shorthand compatibility arrays `envVars` and `secrets` (treated as required aliases).
   - Added module runtime resolver with `.env.lucli` support and optional `.env` fallback (controlled by settings).
   - Added support for `#secret:NAME#` resolution in module runtime values via LuCLI local secret store.
   - Added strict-mode controls for module env exposure (`moduleRuntime.strictEnv`) and `.env` fallback (`moduleRuntime.allowDotEnvFallback`).
   - Added install/update permission approval prompts and persisted grants under `modulePermissions` in settings.
   - Extended BaseModule helpers to support injected context (`getEnv` precedence updates and new `getSecret` helper).
 - **System Backup Commands:** Added `lucli system backup` command group with `create`, `list`, `verify`, `prune`, and `restore` subcommands.
 - **System Backup Create Progress:** `lucli system backup create` now shows a shared progress bar with source size totals, supports `--progress` for per-file archive output, and excludes legacy `~/.lucli/backups` content by default unless backups are explicitly included.
 - **System Inspect Command:** Added `lucli system inspect --lucee` to pretty-print Lucee CFConfig JSON from `~/.lucli/lucee-server/lucee-server/context/.CFConfig.json` (or `--path`), and made `lucli system` show help when run without a subcommand.
 - **Safer Backup Storage Location:** Backup archives now default to `~/.lucli_backups` (outside `~/.lucli`) to reduce risk of losing backups when cleaning or replacing the LuCLI home directory. Optional overrides are supported via `LUCLI_BACKUPS_DIR` or `-Dlucli.backups.dir`.
 - **Backup Retention Pruning:** Added `lucli system backup prune --older-than <duration> --keep <count> [--force]` with dry-run by default and checksum sidecar cleanup when pruning.
 - **Module Install Name/Alias Override (`--name`):** Added `-n` / `--name` option to `modules install` and `modules add` commands, allowing you to install a module under a different local name that overrides the `module.json` name (e.g., `lucli modules install --url=https://github.com/cybersonic/lucli-bitbucket.git#dev --name=bitbucket-dev`). Includes validation of the alias and improved error messages when module names mismatch.
 - **Modules Help Improvements:** The `modules --help` output now includes an explicit "Install/Add Options" section listing `-u`, `-r`, `-n`, and `-f` flags with descriptions.

## 0.2.1

- **URL Rewriting: Migrated to Tomcat RewriteValve:** Replaced the Tuckey UrlRewriteFilter (`urlrewrite.xml`) with Tomcat's built-in RewriteValve (`rewrite.config`). This eliminates javax/jakarta servlet API compatibility issues across Tomcat versions and removes the need for an external JAR. Rewrite rules now use Apache `mod_rewrite` syntax and are placed at the Host level (`conf/Catalina/<host>/rewrite.config`) instead of inside `WEB-INF/`. When both HTTPS redirect and URL rewriting are enabled, rules are combined into a single `rewrite.config` file.
- **⚠️ Deprecation: `urlrewrite.xml`:** Existing projects using `urlrewrite.xml` will see a warning at server startup. Migrate your rewrite rules to the new `rewrite.config` format (Apache `mod_rewrite` syntax). See the updated [URL Rewriting documentation](content/docs/080_https-and-routing/010_url-rewriting.md).
- **Jetty Runtime: URL Rewriting Not Supported:** URL rewriting is not available when using the Jetty runtime. A warning is displayed if `urlRewrite.enabled` is set with a Jetty-based runtime.
- **New `#env:VAR#` Variable Syntax:** Variable substitution in `lucee.json` now uses `#env:VAR_NAME#` syntax (e.g., `#env:DB_HOST#`, `#env:PORT:-8080#` for defaults). The `env:` prefix makes intent explicit, distinguishing environment lookups from `#secret:NAME#` and potential future CFML expression support.
- **Protected Zones:** The `configuration`, `environments.<env>.configuration`, and `jvm.additionalArgs` blocks are protected — only `#env:VAR#` substitution is applied, preserving `${VAR}` for Lucee runtime evaluation and JVM system properties.
- **⚠️ Deprecation: `${VAR}` and bare `#VAR#` Syntax:** The old `${VAR}` syntax and the bare `#VAR#` syntax (without the `env:` prefix) are both deprecated. They still work but trigger a one-time warning. Migrate to `#env:VAR#` — the old syntax will be removed in a future release.
- **CFML Expression Guard:** A warning is shown if `#...#` placeholders contain parentheses, which may indicate accidental CFML expressions rather than simple variable references.
- **`--config` Support for `server stop` and `server restart`:** Added `--config` / `-c` option to `server stop` and `server restart` commands. Allows stopping/restarting servers by specifying their configuration file (e.g., `lucli server stop --config lucee-docker.json`) instead of requiring `--name`. The server name is resolved from the config file automatically.
- **`source` Directive for `.lucli` Scripts:** Added a `source` command for loading `.env`-style files inside `.lucli` scripts (e.g., `source .env`). Paths are resolved relative to the script's directory. Loaded variables become available as `${VAR}` placeholders and in `scriptEnvironment`, just like `set`.
- **`--envfile` CLI Option:** Added `--envfile <path>` flag to pre-load environment variables from a file before script execution (e.g., `lucli --envfile=.env myscript.lucli`). Variables are available to all script lines from the start.
- **⚠️ Deprecation: Top-level `version` in `lucee.json`:** The Lucee engine version should now be specified under a new `"lucee"` block: `"lucee": { "version": "6.2.2.91", "variant": "standard" }`. This frees up the top-level `"version"` key for specifying the application/project version. Existing configs with a top-level `"version"` containing a Lucee version string (e.g. `"6.2.2.91"`) are automatically migrated at load time with a deprecation warning. A `"variant"` field (default `"standard"`) replaces the previous `runtime.variant` for selecting Lucee editions. **The old format will be removed in a future release.**
- **Fix: Shebang Line Handling:** Fixed syntax error when executing CFML scripts (.cfs, .cfm) that contain shebang lines (e.g., `#!/usr/bin/env lucli`). Scripts can now include shebangs for direct execution while remaining compatible with `lucli run`. The shebang line is automatically stripped before the script is passed to the Lucee engine.
- **JSON Comments:** `lucee.json` now supports `//` line comments and `/* */` block comments, making it easier to annotate your configuration.
- **Relative Path Resolution in envVars:** Relative paths (starting with `./` or `../`) in `envVars` are now automatically resolved to absolute paths against the project directory, so the server process receives correct paths regardless of its working directory.
- **Dry-Run for `server run`:** `lucli server run --dry-run` now works the same as `server start --dry-run`, showing the realized configuration without starting the server.
- **Environment Preview (`--include-env`):** Added `--include-env` flag for `--dry-run` on both `server start` and `server run` to display the environment variables that would be passed to the runtime (CATALINA_OPTS, LUCEE_EXTENSIONS, envVars, etc.). Also included in `--include-all`.
- **Platform Updates:** Updated to Lucee 7.0.2.101-SNAPSHOT.
- **Module Help:** Modules now support `--help` (e.g. `lucli markspresso --help`) to display available subcommands with their arguments, types, and defaults. Module name, version, and description are pulled from `module.json`. Module authors can override `showHelp()` for custom help output.
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
- **Fix:** Fixed silent failures when executing picocli subcommands (e.g. `server stop`) in `.lucli` scripts. Captured output was being discarded instead of returned, so commands would run but produce no visible output or error messages.
- **SpotBugs Integration:** Added SpotBugs Maven plugin for static analysis. New documentation at `content/docs/150_testing-and-qa/020_spotbugs.md` covers setup, usage, and configuration.

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
