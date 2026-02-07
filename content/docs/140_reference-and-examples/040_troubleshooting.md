---
title: Troubleshooting
layout: docs
---

This page collects quick answers to problems you are likely to hit when getting started with LuCLI.

## Server does not start

**Symptoms:** `lucli server start` fails or exits immediately.

**Checks:**

- Run with extra diagnostics:
  - `lucli --debug --timing server start`
- Inspect logs:
  - `lucli server log --follow`
  - Look for port conflicts, missing files, or stack traces.
- Try starting without Java agents:
  - `lucli server start --no-agents`
- Verify your `lucee.json` syntax (it must be valid JSON).

## Cannot reach the server in the browser

**Symptoms:** browser shows connection errors or 404s for all pages.

**Checks:**

- Confirm which port the server is using:
  - `lucli server status`
- Make sure `lucee.json` and your project files live in the directory where you ran `lucli server start`.
- If using URL rewriting, confirm that:
  - `urlRewrite.enabled` is `true`.
  - Your router file (e.g. `index.cfm`) exists.
  - See [URL Rewriting](../080_https-and-routing/010_url-rewriting/).

## CFML file not found

**Symptoms:** `lucli script.cfs` reports that the file does not exist.

**Checks:**

- Confirm the current working directory (`pwd`) and that the path is correct.
- Use a relative or absolute path that actually exists.
- Remember that LuCLI resolves files relative to the current directory by default.

## Module not found

**Symptoms:** `lucli some-module` fails with a "module not found" error.

**Checks:**

- List installed modules:
  - `lucli modules list`
- Ensure the module lives under `~/.lucli/modules/`.
- Try the explicit form:
  - `lucli modules run some-module ...`

## URL rewriting behaves unexpectedly

**Symptoms:** some routes 404, static assets break, or `cgi.path_info` is empty.

**Checks:**

- Confirm `urlRewrite` settings in `lucee.json`.
- Inspect generated files under your project webroot:
  - `WEB-INF/web.xml`
  - `WEB-INF/urlrewrite.xml`
- Use the debug techniques described in [URL Rewriting](../080_https-and-routing/010_url-rewriting/).

## Where to look next

- `lucli --help` and command-specific `--help` output.
- Logs under `~/.lucli/servers/<name>/logs/`.
- The examples and guides in this docs tree.
- The [Common Workflows](../140_reference-and-examples/030_common-workflows/) page for end‑to‑end scenarios.