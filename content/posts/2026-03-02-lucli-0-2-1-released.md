---
title: Lucli 0.2.1 Released
layout: post
date: 2026-03-02
draft: true
permalink: lucli-0-2-1-released
categories: lucli
---

Time flies when you are having coding fun, so I kinda forgot to do a release for a whole of LuCLI (as have been using and upgrading locally for a number of projects!)≥

You can check out the next alpha version here: 
https://github.com/cybersonic/LuCLI/releases/tag/v0.2.1

Some notable changes are:

- **URL rewriting overhaul (Tomcat RewriteValve):** Replaced Tuckey UrlRewriteFilter (`urlrewrite.xml`) with Tomcat’s built-in RewriteValve (`rewrite.config`) to avoid servlet API compatibility issues and remove the external JAR dependency. Rewrite rules now use Apache `mod_rewrite` syntax and live at the Host level under `conf/Catalina/<host>/rewrite.config`.
- **Deprecations around rewriting:** `urlrewrite.xml` is now deprecated (startup warning + migration guidance). Also URL rewriting is **not supported** on Jetty runtime.
- **New env var syntax + safer substitution rules:** Introduced `#env:VAR_NAME#` (with default support like `#env:PORT:-8080#`), plus “protected zones” where only `#env:` substitution runs (preserving `${VAR}` for Lucee/JVM runtime evaluation). Deprecated `${VAR}` and bare `#VAR#` (still works but warns).
- **Runtime providers (big feature):** New `runtime` support in `lucee.json` for pluggable runtimes:
  - **Lucee Express** (default)
  - **Vendor Tomcat** (point at existing Tomcat via `catalinaHome` / `CATALINA_HOME`)
  - **Docker** (experimental) with lifecycle managed via LuCLI (`server stop` / `server status` / `server list` work with containers)
  - **Jetty** (experimental) added experimental Jetty runtime
- **Better server control options:** Added `--config` / `-c` to `server stop` and `server restart` so you can target servers by config file (no need to know `--name`).
- **`.lucli` scripting improvements:**
  - New `source` directive to load `.env`-style files inside `.lucli` scripts
  - New CLI `--envfile <path>` to preload env vars before running a script
- **UX/terminal improvements:**
  - New reusable **Table** UI component for consistent CLI tables
  - Emoji handling refactor with centralized `EmojiSupport` + one global `showEmojis` setting
  - Multiple completion fixes (file-path completion after trailing space, `~/` completion preserved, external command file completion fallback)
  - REPL loop now catches exceptions so the terminal doesn’t crash

- **Config quality-of-life:**
  - `lucee.json` now supports `//` and `/* */` comments
  - Relative paths in `envVars` are resolved to absolute paths against the project directory
  - `server run --dry-run` parity with `server start --dry-run`, plus `--include-env` for showing env vars passed through
- **Module/help improvements:** Modules now support `--help` that prints subcommands + args/types/defaults sourced from `module.json`; plus new plural aliases (`server→servers`, `module→modules`, `secret→secrets`).
- **Bug fixes:** Shebang handling for `.cfs` / `.cfm`, server status for vendor Tomcat PID overwrite issue, environment merge bug, output capture issues in `.lucli` scripts, prompt path stripping bug.
- **Tooling:** Added SpotBugs Maven integration + docs.


Phew! And loads more to come as I work on the runtimes! 

Would love your feeback and if you find bugs/issues let me in in the github repoirbe1