---
title: Starting Servers
layout: docs
---

This page explains how to start Lucee servers with LuCLI, how `lucee.json` is used (or created), and how to apply one‑shot overrides at the command line.

Most commands follow the standard pattern:

```bash
lucli server <subcommand> [options] [PROJECT_DIR] [key=value overrides]
```

For starting servers, the main subcommands are:

- `lucli server start` – start a background server and return to the shell
- `lucli server run` – start a foreground server (Ctrl+C to stop)

## Quick start

From a project directory:

```bash
lucli server start
```

What happens:

- If `lucee.json` exists, LuCLI loads it and starts a Lucee/Tomcat server with those settings.
- If `lucee.json` does **not** exist, LuCLI creates a sensible default configuration and saves it, then starts the server.
- A server instance directory is created under `~/.lucli/servers/<name>` where `<name>` comes from the `name` field in `lucee.json` (or the project folder name by default).

You can also explicitly name the project directory:

```bash
lucli server start /path/to/project
```

In all cases the **project directory** is where LuCLI looks for (or writes) `lucee.json` and where the webroot is resolved from by default.

## With and without `lucee.json`

### When `lucee.json` is present

If a `lucee.json` file exists in the project directory:

- LuCLI **reads** the configuration
- Applies any `--env` environment overrides
- Resolves environment variables and secrets
- Starts (or reuses) the server with the resulting settings

The `name` field in `lucee.json` determines the server’s identity and instance directory. If `name` is omitted, LuCLI uses the last part of the project path (e.g. `my-app`).

### When `lucee.json` is missing

If `lucee.json` is missing, LuCLI:

1. Creates a **default configuration** in memory, using the project folder name as `name` and picking an available HTTP port.
2. Writes that configuration to `lucee.json` in the project directory.
3. Starts the server with that configuration.

This makes `lucli server start` safe to run in a new folder: you always end up with a concrete, persisted configuration you can inspect and edit later.

For more detail on available settings, see [Server Configuration](../050_server-configuration/010_lucee-json-basics/).

## Inline configuration overrides (`key=value`)

You can override any `lucee.json` setting at startup by appending `key=value` pairs **after** the command. These overrides are applied on top of the loaded configuration and then persisted back to `lucee.json` so that future starts use the new values.

Examples:

```bash
# Change the HTTP port
lucli server start port=8081

# Start a static file server (no Lucee CFML engine)
lucli server start enableLucee=false

# Disable monitoring in this project
lucli server start monitoring.enabled=false

# Combine multiple overrides
lucli server start port=8081 openBrowser=false enableLucee=false
```

Notes:

- Nested keys use dot notation (e.g. `monitoring.enabled`, `jvm.maxMemory`).
- Boolean values use `true`/`false`.
- Numeric values are parsed as numbers where appropriate (e.g. `port=8081`).
- Overrides are **saved** back into `lucee.json` so you do not need to repeat them every time.

## Choosing Lucee version

Use `--version` to choose a Lucee Express version for this server:

```bash
lucli server start --version 6.2.2.91
```

The chosen version is recorded in `lucee.json` under `version`. LuCLI downloads the engine the first time it is needed, then reuses the cached copy for subsequent starts.

## Environments (`--env`)

You can define environments (such as `dev`, `staging`, `prod`) in the `environments` section of `lucee.json` and select one at startup:

```bash
# Use the "prod" environment overrides
lucli server start --env=prod

# Use the "dev" environment
lucli server start --env dev
```

LuCLI deep‑merges the selected environment onto the base configuration. See [Environments and Configuration Overrides](../050_server-configuration/030_environments-and-overrides/) for details.

## Foreground vs background: `start` vs `run`

`lucli server start` launches Tomcat in the background and returns you to the shell once the server is up.

`lucli server run` keeps the server attached to your terminal:

```bash
# Run server in foreground
lucli server run

# Run with a specific port and environment
lucli server run --env=dev --port=8082
```

Foreground mode is useful when you want to see Tomcat output live or when running temporary/sandbox servers during development.

## Sandbox servers

For quick experiments you can start **sandbox** servers that do not write or reuse `lucee.json` and are automatically cleaned up when stopped or pruned.

```bash
# Background sandbox server
lucli server start --sandbox

# Foreground sandbox server
lucli server run --sandbox
```

Sandbox servers:

- Use a generated name based on the project folder (e.g. `my-app-sandbox`)
- Store their files under `~/.lucli/servers/` with a `.sandbox` marker
- Do **not** modify your project’s `lucee.json`

They are ideal when you want to try a different port, webroot, or Lucee version without touching the main server instance.

## Controlling browser opening

By default LuCLI tries to open your browser after a background server starts (`openBrowser: true` in `lucee.json`). You can control this per start:

```bash
# Explicitly enable browser opening
lucli server start --open-browser

# Disable browser auto-open for this run
lucli server start --disable-open-browser

# Persistently disable it via inline override
lucli server start openBrowser=false
```

The exact URL that LuCLI opens (HTTP vs HTTPS, host, ports) is covered in [Logs and Opening Servers](../060_server-lifecycle/030_logs-and-open/).
