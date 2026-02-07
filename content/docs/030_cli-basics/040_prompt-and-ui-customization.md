---
title: Prompt & UI Customization
layout: docs
---

LuCLI is designed to feel at home in your terminal. You can customize the interactive experience without touching any Java code.

This page covers:

- Prompt themes
- Emoji usage and terminal capabilities
- Global settings and where they live
- How this ties into LuCLI's placeholder system

## Prompt themes

In interactive terminal mode, the `prompt` command manages themes:

```bash
prompt              # Show current prompt and available themes
prompt zsh          # Switch to a zsh‑style theme
prompt colorful     # Switch to a colorful theme
prompt minimal      # Switch to a minimal theme
```

Prompt definitions are JSON templates that live under `~/.lucli/prompts/`. A simplified example:

```json
{
  "name": "my-custom",
  "description": "My custom prompt",
  "template": "🔥 [{time}] {path} {git}⚡ ",
  "showPath": true,
  "showTime": true,
  "showGit": true,
  "useEmoji": true
}
```

You normally manage prompts via the `prompt` command rather than editing these files directly, but knowing the location is helpful when backing up or sharing themes.

## Emoji and output style

LuCLI uses emojis for success, error, and informational messages when your terminal supports them, and falls back to plain text otherwise.

Examples:

- Emoji‑capable terminal: `✅ Server started successfully on port 8080`
- Legacy terminal: `[OK] Server started successfully on port 8080`

You can test and adjust emoji behavior from the terminal:

```bash
prompt emoji on       # Prefer emoji where possible
prompt emoji off      # Force plain-text symbols
prompt emoji test     # Show sample output
prompt terminal       # Show detected terminal capabilities
```

Behind the scenes, messages are built using placeholders such as `${EMOJI_SUCCESS}` and `${EMOJI_ERROR}` and then processed by LuCLI's `StringOutput` component.

For a list of available placeholders and what they expand to, see:

- [Placeholders & Output Processing](../140_reference-and-examples/060_placeholders-and-output/)

## Global settings (`~/.lucli/settings.json`)

Global user settings are stored in `~/.lucli/settings.json`. Typical options include:

- Default prompt theme
- Emoji preferences
- Other UI and behavior flags as LuCLI evolves

You usually do **not** need to edit `settings.json` by hand; prefer using LuCLI commands that change settings. But if something goes wrong or you want to reset, it is useful to know where the file lives.

## Per‑project behavior

Project‑specific behavior is controlled via `lucee.json` in each project directory. This covers:

- Server ports, webroot, and Lucee version
- URL rewriting and router files
- Monitoring and agents
- HTTPS configuration

Relevant reference pages:

- [Server Configuration](/docs/server-configuration/lucee-json-basics/)
- [Environment Variables in lucee.json](/docs/server-configuration/environment-variables/)
- [Environments and Configuration Overrides](/docs/server-configuration/environments-and-overrides/)
- [Starting Servers](/docs/server-lifecycle/starting-servers/)
- [URL Rewriting](/docs/https-and-routing/url-rewriting/)

Together, the global settings under `~/.lucli` and the per‑project `lucee.json` give you a clear separation between **how LuCLI behaves in your terminal** and **how each server is configured."