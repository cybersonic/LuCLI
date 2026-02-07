---
title: Sever Naming and Multiple Servers
layout: docs
---

This page explains how LuCLI names servers, how those names relate to `lucee.json` and the `~/.lucli/servers/` directory, and how to work with multiple servers.

## Where server names come from

Each LuCLI‑managed server has a **name**, used for:

- The instance directory under `~/.lucli/servers/<name>`
- CLI commands that accept `--name`
- Output from `lucli server list` and `lucli server status`

### `name` in `lucee.json`

The primary source of the server name is the `name` field in your project’s `lucee.json`:

```json
{
  "name": "my-app",
  "port": 8080
}
```

In this case, LuCLI will create and manage an instance directory at:

```text
~/.lucli/servers/my-app
```

### When `name` is omitted

If `name` is missing or blank in `lucee.json`, LuCLI falls back to the last segment of the project directory path. For example, if your project lives at:

```text
/Users/alice/projects/blog
```

and `lucee.json` has no `name` field, LuCLI will behave as if you had set:

```json
{
  "name": "blog"
}
```

### When `lucee.json` does not yet exist

On the very first `lucli server start` in a folder without `lucee.json`, LuCLI creates a default configuration. The default `name` is again the directory name (for example `blog`), and a non‑conflicting port is chosen automatically.

You can later edit `lucee.json` or use `lucli server set name=my-new-name` to rename the server for future starts (note that existing instance directories are not automatically renamed).

## Overriding the name at startup

You can temporarily override the configured server name when starting or running a server:

```bash
# Start with a custom name
lucli server start --name my-app-dev

# Run in foreground with a custom name
lucli server run --name sandbox-1
```

This affects **only that start**:

- The running instance uses the custom name and lives under `~/.lucli/servers/<custom-name>`.
- Subsequent starts without `--name` revert to whatever is in `lucee.json` (or the project directory name).

If an instance directory with the same name already exists, LuCLI will:

- Refuse to start if a server with that name is currently running.
- Reuse or replace the instance depending on whether it matches the current project and whether `--force` was supplied.

If the name conflicts with an existing server from a *different* project and you did not pass `--force`, you will see a helpful error suggesting an alternative unique name.

## One server per project (normal mode)

For normal (non‑sandbox) servers, LuCLI enforces a simple rule:

> At most **one running server** per project directory at a time.

This means:

- If you already have a server running for a project, another `lucli server start` in that project will fail with an error pointing to the existing instance (name, PID, port).
- Using `--name` does **not** bypass this per‑project limit; it only affects the instance name and directory.

This constraint keeps server management predictable and avoids subtle conflicts where multiple instances fight over the same webroot and configuration.

## Multiple servers and environments

You can still have multiple servers that conceptually relate to the same application by combining:

- Different project directories (for example separate checkouts or copies)
- Different `name` values in each project’s `lucee.json`
- Different environments via `environments` and `--env` (such as `dev`, `staging`, `prod`)

When environments are used, `lucli server list` appends the current environment in square brackets after the name:

```text
my-app [prod]    RUNNING   12345   80
my-app [dev]     STOPPED           8081
```

The underlying instance directories remain `~/.lucli/servers/my-app` and so on; the environment tag is a display detail derived from metadata stored alongside the server.

## Sandbox servers

Sandbox servers provide another way to run multiple isolated instances without touching your main `lucee.json`:

```bash
# Background sandbox
lucli server start --sandbox

# Foreground sandbox
lucli server run --sandbox
```

Sandbox servers:

- Use a generated name based on the project directory, suffixed with `-sandbox` (for example `blog-sandbox`).
- Store their state under `~/.lucli/servers/<generated-name>`.
- Are marked with a `.sandbox` file so LuCLI can clean them up automatically when stopped or pruned.

You can run sandbox servers alongside your main project server, as they do not participate in the "one running server per project" rule used for the normal instance.

## Working with many servers

Here are some practical patterns when managing multiple servers:

- Use `lucli server list` to get an overview of all instances, their PIDs, ports, and webroots.
- Prefer descriptive names in `lucee.json` such as `my-app-dev`, `my-app-prod`, or `my-app-preview` when running the same codebase in different contexts.
- Use environments (`environments` + `--env`) when you want a **single** logical server per project with configuration that changes by environment, rather than separate server names.
- Use sandbox servers for short‑lived experiments so your main instances stay clean.

For details on where server directories live and how they relate to other LuCLI state, see [LuCLI Home Directory](../030_cli-basics/030_lucli_home_directory/).
