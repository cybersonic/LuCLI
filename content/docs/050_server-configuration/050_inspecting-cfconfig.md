---
title: Inspecting Lucee CFConfig
layout: docs
---

LuCLI can print the resolved Lucee configuration JSON (`.CFConfig.json`) so you can inspect what is currently on disk.

## Command

```bash
lucli system inspect --lucee
```

This reads:

```text
~/.lucli/lucee-server/lucee-server/context/.CFConfig.json
```

and prints it as pretty-formatted JSON.

## Optional custom path

Use `--path` if you want to inspect a different file:

```bash
lucli system inspect --lucee --path /path/to/.CFConfig.json
```

## Help behavior

`lucli system` is help-only and does not perform inspection by itself.

```bash
lucli system
```

Use `inspect --lucee` when you want the JSON output.
