---
title: Logs and Opening Servers
layout: docs
---

This page explains how to inspect server logs with `lucli server log` and how LuCLI opens running servers in your browser, including how host, port, and HTTPS settings affect the URL.

## Viewing logs with `lucli server log`

The `server log` subcommand lets you view Tomcat and Lucee log files for a running server.

Basic usage from a project directory:

```bash
lucli server log
```

By default, this shows recent **Tomcat** logs (for the server associated with the current directory).

### Log types

Use `--type` (or `-t`) to select which kind of logs to view:

- `tomcat` – Tomcat logs under `logs/` (default; includes `catalina.YYYY-MM-DD.log`, etc.)
- `server` – Lucee **server** logs under `lucee-server/context/logs/` (for example `application.log`, `out.log`)
- `web` – Lucee **web context** logs under `lucee-web/logs/`

Examples:

```bash
# Tomcat (default)
lucli server log

# Lucee server logs
lucli server log --type server

# Web-application logs
lucli server log --type web
```

If the selected log file does not exist yet (for example, the web context has never been hit), LuCLI will explain why and suggest how to generate logs.

### Following logs (`--follow`)

To follow a log stream in real time (like `tail -f`):

```bash
lucli server log --follow
lucli server log --type server --follow
```

LuCLI uses the underlying `tail` command, so Ctrl+C stops following and returns you to the shell.

### Selecting log files and line counts

The log viewer supports selecting specific files and controlling how many lines are shown. The help text documents the options:

```bash
lucli server log --help
```

You can:

- Use `--log-name` (or `-l`) to choose a specific log file under the selected log directory.
- Use `--lines` (or `-n`) to control how many lines are printed (default: 50).

Examples:

```bash
# Show Lucee server application.log (default for server logs)
lucli server log --type server

# Show a specific Lucee server log file
lucli server log --type server --log-name out.log

# Show the last 100 lines of Tomcat logs
lucli server log --lines 100

# Show and follow server logs, starting with the last 200 lines
lucli server log --type server --follow --lines 200
```

If no server is currently running for the project, `server log` will fail with a clear message and suggest starting a server first.

## Opening servers in your browser

LuCLI can open a browser for a running server in two ways:

1. Automatically, right after `lucli server start`
2. On demand, using `lucli server open`

Both behaviours are driven by the `openBrowser`, `openBrowserURL`, `host`, `port`, and `https` settings in `lucee.json`.

### Automatic browser opening on start

By default, background starts attempt to open your browser when the server is ready:

```bash
lucli server start
```

Control this behaviour per run:

```bash
# Explicitly enable browser opening (even if disabled in lucee.json)
lucli server start --open-browser

# Disable browser auto-open just for this run
lucli server start --disable-open-browser

# Persistently disable auto-open for the project
lucli server start openBrowser=false
```

When auto-open is enabled, LuCLI computes the URL as follows:

1. If `openBrowserURL` is a non-empty string in `lucee.json`, that value is used as-is.
2. Otherwise, it constructs a URL from:
   - `host` (defaults to `localhost` when omitted)
   - The effective HTTP or HTTPS port
   - The enabled HTTPS configuration

Concretely:

- If HTTPS is **enabled**, LuCLI opens:
  
  ```text
  https://<host>:<httpsPort>/
  ```

- If HTTPS is **disabled**, LuCLI opens:
  
  ```text
  http://<host>:<httpPort>/
  ```

The HTTP port is the server’s main `port`. The HTTPS port is either `https.port` from `lucee.json` or the default (typically `8443`) when not explicitly set.

### `lucli server open`

Use `server open` to open an already running server in your browser:

```bash
# Open the server for the current directory
lucli server open

# Open a specific server by name
lucli server open --name my-app
```

Behaviour:

- With no `--name`, LuCLI looks for a running server whose project directory matches the current directory.
- With `--name`, LuCLI looks up the instance under `~/.lucli/servers/<name>` and, if it is running, opens it.
- If no matching running server is found, LuCLI exits with an error explaining what it could not find.

The URL that `server open` uses is computed using the **same rules** as automatic browser opening:

- `openBrowserURL` takes precedence when set.
- Otherwise, LuCLI chooses `http://` or `https://` based on whether HTTPS is enabled in `lucee.json`, using the effective HTTP/HTTPS ports and `host` value.

> Note: both automatic opening and `lucli server open` respect the `openBrowser` flag from `lucee.json`. If `openBrowser` is `false`, LuCLI will not attempt to open a browser unless you override the setting (for example by starting the server with `openBrowser=true`).

### HTTPS and redirects

When HTTPS is enabled in `lucee.json` (`https.enabled: true`):

- Tomcat is configured with an HTTPS connector on the configured or default HTTPS port.
- If `https.redirect` is enabled (default when HTTPS is on), HTTP requests to the HTTP port are redirected to HTTPS.
- The browser URL opened by LuCLI points **directly** at the HTTPS endpoint.

This means that once HTTPS is enabled, both `lucli server start` (with auto-open) and `lucli server open` will take you to an `https://` URL using the correct port and host.

For more detail on HTTPS configuration and redirect behaviour, see [HTTPS and Routing](../080_https-and-routing/010_url-rewriting/).
