---
title: AI Prompting, Rules, and Skills
layout: docs
---

This page documents how to use `lucli ai` for provider configuration and one-shot prompts, including how instructions, rules, and skills are merged.

## Quick start

```bash
# 1) Add a provider (guided)
lucli ai config add --guided

# 2) Configure a default provider
lucli ai config defaults --default-endpoint OpenAI

# 3) Run a simple prompt
lucli ai prompt --text "Summarize this commit message in one sentence."

# 4) Run with rules
lucli ai prompt \
  --rules-file ./rules/review.md \
  --text "Review this payload for compliance."

# 5) Run with images
lucli ai prompt \
  --text "Describe this image" \
  --image ./screenshot-1.png \
  --image ./screenshot-2.png
```

## Overview

`lucli ai` has two main workflows:

- Configure and inspect providers/endpoints
- Send prompts (text and/or images) through a configured provider

Top-level commands:

- `lucli ai config` (alias: `lucli ai configure`)
- `lucli ai prompt`
- `lucli ai list`
- `lucli ai test`
- `lucli ai skill`

## Provider configuration

Recommended flow:

```bash
# 1) Add/update a provider
lucli ai config add --guided

# 2) Verify provider entries (secret keys are masked by default)
lucli ai config list

# 3) Pick the default provider/model for ai prompt
lucli ai config defaults --default-endpoint OpenAI
lucli ai config defaults --default-model gpt-4o

# 4) Verify saved defaults
lucli ai config defaults --show
```

Configure defaults used by prompt commands:

```bash
lucli ai config defaults --default-endpoint OpenAI
lucli ai config defaults --default-model gpt-4o
lucli ai config defaults --show
```

Create/update provider entries in Lucee config:

```bash
lucli ai config add --name OpenAI --type openai --model gpt-4o --secret-key '#env:OPENAI_API_KEY#'
lucli ai config list
```

To reveal full secret values explicitly (use with care):

```bash
lucli ai config list --show
```

Use guided setup:

```bash
lucli ai config add --guided
```

## Prompting basics

Minimal prompt:

```bash
lucli ai prompt --text "Summarize this in one sentence"
```

Prompt with explicit endpoint:

```bash
lucli ai prompt --endpoint OpenAI --text "Write a short release note"
```

Prompt with image attachments:

```bash
lucli ai prompt --text "Describe this image" --image ./screenshot.png
```

`--image` is repeatable.

## Instructions vs rules vs task text

LuCLI builds a final prompt context from three parts:

1. **System instructions** (`--system` and/or skill `system`)
2. **Rules content** (`--rules-file` and `--rules-folder`)
3. **Task text** (`--text` and/or skill `text`)

Effective model input is:

- instruction context = system + rules
- task context = text

Rules are appended into the instruction context; they are not treated as image or binary attachments.

Examples:

```bash
lucli ai prompt \
  --system "Be strict and concise." \
  --rules-file ./docs/review-rules.md \
  --text "Review this payload for compliance."
```

```bash
lucli ai prompt \
  --rules-folder ./rules \
  --text "Check this release summary against the rules."
```

## Skills in `lucli ai prompt`

In LuCLI prompt, a **skill** is a local JSON prompt profile, not an MCP skill/tool.

Use:

```bash
lucli ai prompt --skill weekly-review --text "Review this week"
```

or direct path:

```bash
lucli ai prompt --skill ./skills/weekly-review.json --text "Review this week"
```

### Skill format

Skill files are JSON and may define:

- `name`
- `system`
- `text`
- `model`
- `temperature`
- `timeoutMillis`

Example:

```json
{
  "name": "weekly-review",
  "system": "You are a strict engineering reviewer.",
  "text": "Review the provided weekly payload and produce action items.",
  "model": "gpt-4o",
  "temperature": 0.2,
  "timeoutMillis": 10000
}
```

### Precedence

When both skill values and CLI flags are present:

- CLI flags win
- then skill values
- then configured defaults (for endpoint/model where applicable)

### Skill lookup order

When `--skill` is a name (not explicit file path), LuCLI resolves in order:

1. `--skills-path` directories (left-to-right)
2. project default `.lucli/skills`
3. global skill paths (`lucli ai skill path ...`)
4. named entries in `~/.lucli/ai/skills.json`

## Related commands

Skill path management:

```bash
lucli ai skill path add ./skills
lucli ai skill path list
lucli ai skill list
```

Endpoint metadata/test:

```bash
lucli ai list --name OpenAI
lucli ai test --endpoint OpenAI
```
