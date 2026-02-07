---
title: LuCLI Home Directory
layout: docs
draft: false
---

The LuCLI home directory is a special folder where LuCLI keeps **all of its runtime state**:

- Server instances and logs
- Downloaded Lucee distributions
- Dependency caches
- Global settings and history
- Secrets and prompts

Understanding this directory makes it easier to debug issues, clean up old data, and customise LuCLI behaviour.

## Default location

By default, LuCLI uses a directory under your home folder:

```text
~/.lucli
```

This directory is **per user**. Different users on the same machine will have separate LuCLI homes.

## Directory structure

The exact contents may evolve, but you can expect a structure like:

```text
~/.lucli/
    ├── servers
    ├── dependencies
    ├── deps
    ├── express
    ├── modules
    ├── prompts
    ├── secrets
    └── settings.json
```


### `servers/`

Each LuCLI‑managed Lucee server gets its own directory under `servers/`:

```text
~/.lucli/servers/
  my-app/
  another-app/
```

Inside each server folder you’ll find:

- Tomcat/Lucee configuration
- Logs (use `lucli server log` to view them)
- Generated HTTPS keystores and rewrite configs (when enabled)

You normally don’t edit these files by hand; instead use LuCLI commands and your project’s `lucee.json`.

### `express/`

Cached Lucee Express distributions live here. LuCLI downloads them the first time a version is needed, then reuses them for new servers.

It’s safe to delete old versions in `express/` if you need disk space; LuCLI will re‑download them when required.

### `deps/`

Holds dependency‑related caches, for example:

- `deps/git-cache/` – shared git clones used when installing CFML or extension dependencies.

You can clear these with:

```bash
lucli deps prune
```

or manually delete the cache directories. This only affects performance, not your actual project code.

### `secrets/`

The encrypted secrets store lives here, typically:

```text
~/.lucli/secrets/local.json
```

This file contains **encrypted** secret values managed via `lucli secrets ...`. Do not edit it manually. If you delete or reset it, you will need to recreate your secrets.

### `history`

A command history file used by LuCLI’s interactive terminal. It lets you recall previous commands across sessions.

You can safely delete it if you want to clear history; LuCLI will recreate it as needed.

### `prompts/`

Stores custom prompt templates and related configuration for LuCLI’s interactive terminal.

### `settings.json`

A JSON file holding **user‑level settings**, such as:

- Feature toggles (e.g. use of persistent git cache)
- Default behaviours that apply across all projects for this user

This is separate from per‑project configuration in `lucee.json`.

### `modules/`

When LuCLI installs command modules globally, they are placed under `~/.lucli/modules/`. For example, the `markspresso` module lives in:

```text
~/.lucli/modules/markspresso/
```

## Customising the LuCLI home location

You can override the default `~/.lucli` location in two ways:

### 1. `LUCLI_HOME` environment variable

Set `LUCLI_HOME` before running LuCLI:

```bash
export LUCLI_HOME="$HOME/.lucli-dev"
lucli server start
```

All state (servers, downloads, caches, etc.) will now be stored under the new directory.

### 2. JVM system property `-Dlucli.home`

If you’re launching LuCLI via `java -jar` and want a custom home per process, you can use:

```bash
java -Dlucli.home=/tmp/lucli-test -jar target/lucli.jar server start
```

This is useful for running multiple isolated environments on the same machine (for example, CI builds or test harnesses).

## When to interact with the home directory

Common reasons to look inside the LuCLI home directory:

- **Inspect server state or logs** when diagnosing issues.
- **Free up disk space** by removing old Lucee downloads or dependency caches.
- **Back up or migrate secrets** (`secrets/local.json`) and user settings (`settings.json`).
- **Debug modules** you’ve installed under `~/.lucli/modules/`.

As a rule of thumb:

- It’s safe to delete **caches and downloads** (`express/`, `deps/git-cache/`) – LuCLI will recreate them.
- Be careful when deleting **servers/** – that removes LuCLI’s managed instances and their configs.
- Handle **`secrets/`** with extra care; if you lose the encrypted store and its passphrase, LuCLI cannot recover those values for you.
