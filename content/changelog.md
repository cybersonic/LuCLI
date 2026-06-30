---
title: Changelog
subtitle: Release notes and notable updates for LuCLI
layout: page
draft: false
---

This page highlights recent LuCLI changes.

For the complete release history, see:

- [Full `CHANGELOG.md` on GitHub](https://github.com/cybersonic/LuCLI/blob/main/CHANGELOG.md)
- [GitHub Releases](https://github.com/cybersonic/LuCLI/releases)

## Unreleased

Recent work currently in progress includes:

- Launch4j-based `lucli.exe` packaging for Windows releases.
- Better environment/config merge behavior (including `configurationFile` layering).
- Runtime extension activation fixes for environment-specific dependency toggles.
- MCP improvements for tool schema discovery and cleaner tool descriptions.
- Profile-aware module and alias-binary command behavior fixes.

## Recent Releases

### 0.3.17

- Added dependency examples docs and improved dry-run preview controls.
- Added Java 21 runtime checks in wrapper scripts.
- Added extension dependency `enabled` toggles and prewarm support.
- Fixed static file passthrough behavior in default URL rewrite config.
- Added whitelabeling support for branded LuCLI distributions.

### 0.3.3

- Expanded BATS coverage and migration from legacy shell tests.
- Added script argument variables (`ARGS`, `ARGV`, `__namedArguments`).
- Added `lucli ai prompt --estimate` rough cost estimation mode.
- Added SpotBugs integration and testing/QA documentation.
- Improved server lifecycle resolution and ambiguity handling for multi-server projects.

### 0.2.23

- Added cross-platform bootstrap installers (`install.sh` and `install.ps1`).
- Added `.lucli` script output redirection (`>` and `>>`).
- Added per-module MCP server mode via `lucli mcp <module>`.
- Added interactive and guided `lucli ai config` workflows.
- Added system backup command group (`create`, `list`, `verify`, `prune`, `restore`).

### 0.2.1

- Migrated URL rewriting to Tomcat `RewriteValve` (`rewrite.config`).
- Added `#env:VAR#` syntax and protected substitution zones in `lucee.json`.
- Added pluggable runtime providers (Lucee Express, vendor Tomcat, Docker, Jetty).
- Added `--config` support for `server stop` and `server restart`.
- Added `source` directive for `.lucli` scripts and `--envfile` support.

## Looking for older entries?

The full, canonical changelog is maintained in [`CHANGELOG.md`](https://github.com/cybersonic/LuCLI/blob/main/CHANGELOG.md) and includes every historical release.
