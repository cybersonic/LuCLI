# Changelog

All notable changes to this project will be documented in this file.

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
