---
title: Interactive Terminal and REPL
layout: docs
---

LuCLI includes a full interactive terminal with history, completion and a built‑in CFML REPL (read–eval–print loop). This page explains how to start it and what you can do inside it.

## Starting the interactive terminal

You can start the terminal in a few equivalent ways:

```bash
# If using the JAR directly
java -jar lucli.jar

# If using the binary
lucli

# Explicitly request terminal mode
lucli terminal
```

If you run `java -jar lucli.jar` or `lucli` **without arguments**, LuCLI automatically starts the interactive terminal. You’ll see a banner similar to:

```text
🚀 LuCLI Terminal x.y.z  Type 'exit' or 'quit' to leave.
📁 Working Directory: /path/to/current/dir
```

From here you can run all the usual LuCLI commands (`server`, `modules`, etc.) plus a set of terminal‑only commands.

## CFML REPL with `cfml <expression>`

The simplest REPL feature is the inline CFML expression command:

```bash
cfml now()
cfml 1 + 2
cfml dateFormat(now(), 'yyyy-mm-dd')
```

Behaviour:

- The first time you use `cfml`, LuCLI **initializes the Lucee engine** for this terminal session.
- Each `cfml` command wraps your expression in a small CFML template that prints the result.
- Output is written directly to the terminal; complex values use Lucee’s standard display.

If you forget to pass an expression:

```bash
cfml
```

LuCLI responds with a helpful error and a usage hint.

> Tip: use the terminal for quick experiments while developing modules or scripts—no need to create a file for every small check.

## Using LuCLI commands inside the terminal

Everything you can do from the normal CLI is also available in the terminal. For example:

```bash
# Server management
server start
server status
server log --follow

# Module management
modules list
modules run my-module arg1 arg2

# CFML command-line helpers
cfml 'now()'
```

Under the hood, the terminal delegates these to the same Picocli commands used in one‑shot mode, so behaviour and options stay consistent.

## File system commands & working directory tracking

The terminal includes a small set of built‑in commands that behave like familiar shell tools but operate through LuCLI’s **FileSystemState**, so the current working directory is tracked consistently across commands:

- Navigation: `cd`, `pwd`
- Listing: `ls`, `dir`
- Files & directories: `mkdir`, `rmdir`, `rm`, `cp`, `mv`, `touch`
- Viewing content: `cat`, `head`, `tail`
- Searching: `find`, `wc`
- UI helpers: `prompt` (manage themes), `edit` (open files via configured editor)

Examples:

```bash
# Check where you are
pwd

# Change directory relative to current working directory
cd src
ls

# Create and inspect files
touch notes.txt
cat notes.txt

# Search for CFML files
find . *.cfs
```

The current working directory is also shown in the prompt (see [Prompt & UI Customization](../030_cli-basics/040_prompt-and-ui-customization/)).

## History and completion

The terminal is built on JLine3 and provides a modern interactive experience:

### Command history

- Press **↑ / ↓** to move through previous commands.
- History is **persisted** between sessions in `~/.lucli/history`.
- Blank lines are not added to history.

This makes it easy to repeat or tweak previous `cfml`, `server`, or module commands.

### Tab completion

Press **Tab** to get completions for:

- Top‑level commands (`server`, `modules`, `cfml`, `terminal`, etc.)
- Subcommands (`start`, `stop`, `status`, `list`, `run`, …)
- Options and flags (such as `--version`, `--port`, `--env`)
- File paths for many file‑oriented commands

Completions come from a combination of Picocli’s command metadata and LuCLI’s own terminal completer.

## Prompt, colours and emoji

The terminal prompt is themeable and emoji‑aware:

- Themes are managed with the `prompt` command (for example `prompt zsh`, `prompt minimal`).
- LuCLI detects whether your terminal supports emoji and falls back to text symbols when needed.
- Status symbols (success, error, info) in many messages adapt to your terminal capabilities.

For full details and theme customization, see [Prompt & UI Customization](../030_cli-basics/040_prompt-and-ui-customization/).

## Exiting and interrupts

You can leave the terminal in several ways:

```bash
exit
quit
```

Or via control keys:

- **Ctrl+D** – send EOF, which cleanly exits the terminal.
- **Ctrl+C** – interrupt the current command; LuCLI prints `^C` and returns you to the prompt without closing the session.

On exit, LuCLI closes the terminal and returns you to your normal shell.
