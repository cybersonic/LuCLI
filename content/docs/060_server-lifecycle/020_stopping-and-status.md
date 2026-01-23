---
title: Stopping Servers and Checking Status
layout: docs
---

This page covers how to stop servers, inspect their status, and list all instances managed by LuCLI.

## Stopping servers

### Stop the server for the current directory

From a project directory with a running server:

```bash
lucli server stop
```

LuCLI will:

- Locate the running server associated with the current project
- Send it a graceful shutdown signal
- Wait briefly for the process to exit
- Remove the PID file and, for sandbox servers, delete the sandbox instance directory

If no server is running for that project, you will see an informational message and nothing is changed.

### Stop a server by name

You can stop any managed server by its name (regardless of the current directory):

```bash
lucli server stop --name my-app
```

This looks up the instance under `~/.lucli/servers/my-app`, reads its PID, and stops that process if it is still running.

### Stop all running servers

To stop every running LuCLI‑managed server at once:

```bash
lucli server stop --all
```

LuCLI will iterate over all instances, attempt to stop each running server, and report which ones succeeded or failed.

## Checking server status

### Status for the current directory

To see whether a server is running for the current project and on which port:

```bash
lucli server status
```

Typical output (formatted by LuCLI):

```text
Server status for: /path/to/project
✅ Server is RUNNING
   Server Name: my-app
   Process ID:  12345
   Port:        8080
   URL:         http://localhost:8080
```

If no matching server is running you will see a clear "NOT RUNNING" message instead.

### Status for a specific server name

You can also query by name, independent of directory:

```bash
lucli server status --name my-app
```

In addition to the basic status information, LuCLI will show:

- The effective server name, including environment tag when present (for example `my-app [env: prod]`)
- The PID and port
- The webroot and server directory when known

This is especially helpful when managing multiple servers across different projects.

## Listing all servers

To see all LuCLI‑managed server instances (running and stopped):

```bash
lucli server list
```

Sample output (CLI mode):

```text
Server instances:

NAME                 STATUS     PID      PORT      WEBROOT                                  SERVER DIR
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
my-app [prod]        RUNNING    12345    80        /projects/my-app                         ~/.lucli/servers/my-app
my-app-dev           STOPPED    -        -         /projects/my-app                         ~/.lucli/servers/my-app-dev
blog                 RUNNING    23456    8080      /projects/blog                           ~/.lucli/servers/blog
```

Notes:

- `STATUS` is either `RUNNING` or `STOPPED`.
- `PID` and `PORT` are populated only for running servers.
- If a server was started with an environment (for example `--env=prod`), the environment is shown in square brackets next to the name.

To show only running servers:

```bash
lucli server list --running
```

This is useful for quickly seeing which instances are live without the noise of stopped servers.

## Restarting and pruning

### Restarting

To restart a server in one step:

```bash
# Restart by name
lucli server restart --name my-app

# Restart the server for the current directory
lucli server restart
```

Under the hood this is equivalent to a `stop` followed by a `start`, with the same configuration.

### Pruning stopped servers

Over time you may accumulate stopped servers whose instance directories you no longer need. Use `prune` to clean them up:

```bash
# Prune the server for the current directory (if stopped)
lucli server prune

# Prune a specific stopped server by name
lucli server prune --name my-app

# Prune all stopped servers (with confirmation)
lucli server prune --all
```

Pruning permanently deletes the server instance directories under `~/.lucli/servers/` but does not touch your project files or `lucee.json`.
