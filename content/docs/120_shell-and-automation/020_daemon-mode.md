---
title: Daemon Mode and Terminal Integration
layout: docs
---

LuCLI's **daemon mode** lets you run LuCLI as a long-lived background process that accepts JSON commands over a local TCP socket. This is ideal for editor integrations, scripts, and tools that want to reuse a warm JVM instead of spawning a new `lucli` process each time.

## 1. Starting the daemon from the terminal

```bash
# Start daemon on default port 10000 (blocks this terminal)
lucli daemon

# Start daemon on a custom port (e.g. 11000)
lucli daemon --port 11000
```

Notes:
- The daemon listens only on `127.0.0.1` (localhost).
- It is single-threaded and handles **one request per TCP connection**.
- It runs until you stop it with `Ctrl+C` or kill the process.

For long‑running setups, you may want to run it under a process supervisor (e.g. `systemd`, `supervisord`, or your editor plugin).

## 2. Request/response protocol

Each client connection sends exactly **one line of JSON** (UTF‑8) describing the command to run:

```json
{"id":"1","argv":["modules","list"]}
```

Fields:
- `id` (string, optional) – Correlation ID that will be echoed back in the response.
- `argv` (string array, required) – The arguments you would normally type after `lucli` on the command line.

The daemon executes the command through the normal Picocli pipeline and responds with a single JSON line:

```json
{"id":"1","exitCode":0,"output":"..."}
```

Fields:
- `id` – The same value you sent (or `null` if omitted).
- `exitCode` – Numeric exit code from the command (0 = success).
- `output` – Combined stdout and stderr of the command as a single string.

## 3. Using the daemon from a shell (bash/zsh)

You can talk to the daemon from the terminal using standard tools like `nc` (netcat).

### 3.1 One‑off request with `nc`

```bash
# Send a single request to a daemon listening on port 10000
printf '{"id":"1","argv":["server","list"]}\n' | nc 127.0.0.1 10000
```

This will print a JSON response similar to:

```json
{"id":"1","exitCode":0,"output":"..."}
```

### 3.2 Helper function in your shell

Add a helper to your `~/.bashrc` or `~/.zshrc`:

```bash
lucli_daemon() {
  local port=${1:-10000}
  shift || true

  # Build a JSON argv array from remaining arguments
  local json_argv
  json_argv=$(printf '"%s",' "$@" | sed 's/,$//')

  printf '{"id":"shell","argv":[%s]}\n' "$json_argv" | nc 127.0.0.1 "$port"
}
```

Usage:

```bash
# Assuming daemon is listening on default port 10000
lucli_daemon 10000 server list
lucli_daemon 10000 modules list
```

This sends the command to the daemon and prints the JSON response to your terminal.

## 4. When to use daemon mode

Daemon mode is most useful when:
- You are calling LuCLI frequently from tools or scripts and want to avoid JVM startup cost on every call.
- You are building an editor/IDE integration that needs to run many small commands.
- You want a simple, language‑agnostic JSON/TCP API for LuCLI.

If you only run LuCLI occasionally from the terminal, the normal CLI (`lucli ...`) is usually sufficient.

## 5. Troubleshooting

- **No response / connection refused**
  - Ensure the daemon is running: `ps aux | grep lucli` or re-run `lucli daemon`.
  - Verify the port: match `--port` with what your client uses.
- **"Invalid JSON request" errors**
  - Make sure you send a single valid JSON line ending with `\n`.
  - Verify that `argv` is a non-empty array of strings.
- **Unexpected command behavior**
  - Remember that the daemon shares the same Picocli pipeline as `lucli` on the command line; test the same `argv` directly with `lucli` to compare results.
