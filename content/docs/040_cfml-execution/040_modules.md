---
title: Running Modules
layout: docs
---
Modules are reusable CFML commands that live under your LuCLI home directory and can be run like first‑class commands.

### Module Components

By convention a module is:

- A directory under `~/.lucli/modules/<module-name>/`
- With an entrypoint `Module.cfc` (usually `component extends="modules.BaseModule"`)
- Optionally a `module.json` metadata file and `README.md`

You normally create this structure with:

```bash
lucli modules init my-awesome-module
# edits files under ~/.lucli/modules/my-awesome-module/
```

Once created, modules can be listed, installed, updated and removed via the `modules` commands (see **Module Management** below).

## Module Commands

Modules let you package CFML into named commands you can run with either the explicit `modules run` syntax or handy shortcuts.

### Full syntax

```bash
lucli modules run <module-name> [arguments...]
```

This works in both one‑shot CLI mode and the interactive terminal.

### Shortcut syntax

```bash
lucli <module-name> [arguments...]
```

The shortcut form is equivalent to:

```bash
lucli modules run <module-name> [arguments...]
```

so you can think of `lucli <module-name>` as sugar for `lucli modules run <module-name>`.

### Examples

#### Full syntax
```bash
lucli modules run hello-world
lucli modules run cfformat file1.cfs file2.cfs
```

#### Shortcut syntax (equivalent)
```bash
lucli hello-world
lucli cfformat file1.cfs file2.cfs
```

#### With global flags
```bash
lucli --verbose test-module arg1 arg2
lucli --debug midnight
```

(Global flags like `--verbose`, `--debug`, `--timing` are handled by LuCLI itself before your module runs.)

## Passing arguments to modules

Under the hood, LuCLI normalizes module arguments into a **subcommand** plus a named argument collection.

### Subcommands

If the first argument **does not** contain `=` and does **not** start with `-` or `--`, it is treated as a subcommand name; otherwise the subcommand defaults to `main`.

```bash
# Calls main()
lucli my-module

# Calls cleanup() subcommand inside Module.cfc
lucli my-module cleanup

# Subcommand with additional arguments
lucli my-module cleanup target=/tmp keepDays=7
```

Inside your module this is exposed as the `subCommand` variable and you can implement matching functions like `function main()`, `function cleanup()`, etc.

### Positional arguments

Any argument that doesn’t contain `=` after the (optional) subcommand is treated as a positional value:

```bash
lucli my-module arg1 arg2 arg3
```

These are made available as `arg1`, `arg2`, `arg3`, … in the argument collection, so your CFML can access them via the `arguments` struct or the `argCollection` map.

### Named arguments: `arg=1` vs `--arg`

Named arguments are passed with `key=value` syntax. LuCLI also understands flag-style arguments and normalizes them for you:

```bash
# Plain key=value
lucli reports generate year=2025 format=csv

# Long flags with values (equivalent to the above)
lucli reports generate --year=2025 --format=csv

# Boolean flags
lucli my-module --force          # force=true
lucli my-module --no-force       # force=false
```

Rules:

- `arg=1` → key `arg`, value `"1"`
- `--arg=1` or `-a=1` → key `arg`, value `"1"`
- `--arg` → key `arg`, value `"true"`
- `--no-arg` → key `arg`, value `"false"`
- bare values (no `=`) after the subcommand become `arg1`, `arg2`, …

Inside your `Module.cfc`, these end up as normal CFML arguments. For example:

```cfml
function main(required string year, string format="json", boolean force=false) {
    // year, format, force populated from CLI
}
```

will be populated by any of the CLI forms shown above (`year=2025`, `--year=2025`, `--force`, etc.).

## Module Management

```bash
# List available modules (installed under ~/.lucli/modules)
lucli modules list

# Create a new module from the template
lucli modules init my-awesome-module

# Install from a git/HTTP URL or known repository entry
lucli modules install my-awesome-module --url=https://github.com/example/my-awesome-module.git

# Update or uninstall a module
lucli modules update my-awesome-module
lucli modules uninstall my-awesome-module

# Get detailed help
lucli modules --help
```

## How Shortcuts Work

When you run an unrecognized command, LuCLI uses smart detection:

1. First, it checks if it's a known built‑in subcommand (like `server`, `modules`, `terminal`).
2. Next, it checks if it's an existing CFML file (`.cfs`, `.cfm`, `.cfc`).
3. Then it tries to run it as a **module shortcut** (looking under `~/.lucli/modules/<name>/Module.cfc`).
4. If nothing matches, it shows you what’s available.

This means you can just type `lucli hello-world` instead of the longer `lucli modules run hello-world`—the shortcut form is usually what you’ll use day‑to‑day.
