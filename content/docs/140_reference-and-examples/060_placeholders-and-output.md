---
title: Placeholders & Output Processing
layout: docs
---

LuCLI uses a centralized output processor (`StringOutput`) that supports a set of placeholders. These are mainly used inside LuCLI's own messages, templates, and diagnostics rather than in user CFML code.

This page lists the most important placeholders and what they represent.

## Time and date

- `${NOW}` – current date‑time in an ISO‑like format.
- `${DATE}` – current date.
- `${TIME}` – current time.
- `${TIMESTAMP}` – detailed timestamp (often down to milliseconds).

## System information

- `${USER_NAME}` – current OS user.
- `${USER_HOME}` – home directory.
- `${WORKING_DIR}` – current working directory when the command was invoked.
- `${OS_NAME}` – operating system name.
- `${JAVA_VERSION}` – Java runtime version.

## LuCLI information

- `${LUCLI_VERSION}` – LuCLI application version.
- `${LUCLI_HOME}` – resolved LuCLI home directory (usually `~/.lucli`).

## Environment variables

LuCLI can expose selected environment variables via placeholders such as:

- `${ENV_PATH}` – contents of the `PATH` environment variable.
- `${ENV_<NAME>}` – other environment variables, depending on configuration and how templates are written.

Exact coverage may evolve; consult the main README and tests for the latest behavior.

## Emoji placeholders

These placeholders let LuCLI render expressive messages while still working on terminals without emoji support:

- `${EMOJI_SUCCESS}` – success indicator (✅ or `[OK]`).
- `${EMOJI_ERROR}` – error indicator (❌ or `[ERROR]`).
- `${EMOJI_WARNING}` – warning indicator (⚠️ or `[WARN]`).
- `${EMOJI_INFO}` – informational marker (ℹ️ or `[INFO]`).

Which concrete glyph or text is used depends on terminal capability detection in LuCLI's platform support classes.

## Usage notes

- Placeholders are resolved inside LuCLI's own output templates, not in arbitrary CFML output from your scripts.
- They are primarily relevant if you are contributing to LuCLI itself or reading the internal template files under `src/main/resources/`.
- For end users, they explain why certain messages show emojis or environment‑derived data.

## Related docs

- Root `README.md` – high‑level overview of LuCLI and its output behavior.
- `WARP.md` – architecture overview and implementation notes.
- The `script_engine` and other template resources under `src/main/resources/`.
- [Prompt & UI Customization](../030_cli-basics/040_prompt-and-ui-customization/) – how prompts and emoji behavior surface in the CLI.