---
title: Secrets Management
layout: docs
---

LuCLI includes an encrypted secrets store and a `secrets` command namespace to help you keep sensitive values (database passwords, API keys, etc.) out of your source files.

This guide covers:

- The local encrypted secrets store
- Managing secrets via the `lucli secrets` commands
- Referencing secrets from `lucee.json` and CFConfig
- Current limitations and best practices

## Overview

Secrets in LuCLI are:

- **Stored locally** in an **encrypted file** under `~/.lucli/secrets/local.json`
- **Protected by a passphrase** you choose when you run `lucli secrets init`
- **Referenced by name** (e.g. `db.password`) instead of hard-coding raw values in `lucee.json`

In this first phase, LuCLI supports a single provider:

- `local` – a PBKDF2 + AES-GCM encrypted file on disk

The CLI surface is designed to be provider‑agnostic so more providers can be added later without breaking existing commands.

---

## Quick Start

### 1. Initialize the local secret store

```bash
lucli secrets init
```

What this does:

- Creates `~/.lucli/secrets/local.json` (if it doesn’t already exist)
- Prompts you to create and confirm a **master passphrase**
- Encrypts all future secrets with a key derived from that passphrase

If the store already exists and you want to start over:

```bash
lucli secrets init --reset
```

> ⚠️ `--reset` is **destructive** – it re-creates the store and **deletes all existing secrets**.

### 2. Add a secret

```bash
lucli secrets set db.password --description "Primary database password"
```

You’ll be prompted for:

1. The secrets passphrase (to unlock the store)
2. The secret value (entered with no echo)

### 3. List secrets

```bash
lucli secrets list
```

Example output:

```text
- db.password : Primary database password
- stripe.apiKey
```

Only **names and descriptions** are shown. Values are never printed here.

### 4. Remove a secret

```bash
lucli secrets rm db.password
```

You’ll be asked to confirm. To skip the prompt:

```bash
lucli secrets rm db.password -f
```

---

## Commands Reference

### `lucli secrets`

Top-level entrypoint for secrets management. Running it without a subcommand shows help.

```bash
lucli secrets --help
```

### `lucli secrets init`

Initialize (or reinitialize) the local encrypted store.

```bash
lucli secrets init
lucli secrets init --reset
```

Options:

- `--reset` – Re-create the store file and delete all stored secrets.

### `lucli secrets set <name>`

Create or update a secret.

```bash
lucli secrets set db.password
lucli secrets set stripe.apiKey --description "Stripe secret key"
```

Arguments:

- `NAME` – Logical name for the secret (e.g. `db.password`, `redis.password`, `api.someServiceKey`).

Options:

- `--description` – Optional human-friendly description shown in `secrets list`.

Behavior:

- Prompts for the store passphrase if needed
- Prompts securely for the secret value (no echo)
- Creates or updates the encrypted entry

### `lucli secrets list`

List stored secrets without revealing values.

```bash
lucli secrets list
```

Shows one line per secret:

```text
- <name>[: description]
```

Use this to see what secrets exist and how they’re labeled.

### `lucli secrets rm <name>`

Remove a secret from the store.

```bash
lucli secrets rm db.password
lucli secrets rm db.password -f
```

Options:

- `-f` – Skip the interactive confirmation prompt.

### `lucli secrets get <name>`

Retrieve a secret value.

```bash
lucli secrets get db.password --show
```

By default **this does not print values**. You must explicitly opt in:

- `--show` – Print the secret value to stdout.

> ⚠️ Be careful with `--show` – your shell history, logs, or surrounding tools may capture the output. Prefer using secrets via configuration placeholders (see below) instead of copying them manually.

### `lucli secrets provider list`

Show available secret providers.

```bash
lucli secrets provider list
```

Example output:

```text
Available secret providers:
- local (encrypted file under ~/.lucli/secrets/local.json)

More providers coming soon.
```

For now, `local` is the only implemented provider.

---

## Using Secrets in `lucee.json`

You can reference secrets by name in your server configuration using the `${secret:...}` syntax.

### Basic syntax

```json
{
  "admin": {
    "password": "${secret:admin.password}"
  }
}
```

When LuCLI **starts or locks a server configuration** (for example `lucli server start` or `lucli server lock`), it:

1. Loads your config
2. Applies environment variable substitution (see `ENVIRONMENT_VARIABLES.md`)
3. Scans for any `${secret:NAME}` placeholders
4. **Only if placeholders are present**, opens the local secret store and resolves them

Read‑only commands that merely inspect configuration (e.g. `lucli server status`, `lucli server stop`, `lucli server list`, `lucli server config get`) do **not** resolve secrets and therefore will **not** prompt for the secrets passphrase just because `${secret:...}` is present in `lucee.json`.

If a referenced secret cannot be found, or if the store/passphrase is unavailable when resolution is required, the command fails with a clear error instead of silently leaving the placeholder.

### Example: Database password in CFConfig

You can also use `${secret:...}` inside the `configuration` (CFConfig) section of `lucee.json`:

```json
{
  "configuration": {
    "datasources": {
      "primary": {
        "host": "db.internal",
        "database": "myapp",
        "username": "myapp_user",
        "password": "${secret:db.password}"
      }
    }
  }
}
```

At runtime, LuCLI replaces `${secret:db.password}` with the decrypted value from the secret store **before** writing the final CFConfig JSON.

Secrets are resolved **in memory only** – LuCLI does **not** write the resolved plaintext back into `lucee.json` or other files.

### Combining with environment variables

Environment variables and secrets can be used together. The resolution order is:

1. `.env` file and OS environment variables (`${VAR}` / `${VAR:-default}`)
2. Secret placeholders (`${secret:NAME}`)

This lets you mix patterns, for example:

```json
{
  "name": "${APP_NAME:-my-app}",
  "admin": {
    "password": "${secret:admin.password}"
  }
}
```

Use environment variables for non-sensitive configuration that may change per environment (ports, hostnames, feature flags), and secrets for long‑lived credentials.

---

## Security Model

### Storage

- Secrets are stored in a single JSON file: `~/.lucli/secrets/local.json`.
- The file contains **only encrypted values** plus minimal metadata (name, description, timestamps).
- The encryption key is derived from your passphrase using PBKDF2‑HMAC‑SHA256 with a random salt.

### Access

- Commands that need secrets (`secrets set`, `secrets list`, `secrets rm`, `secrets get`, and config resolution) prompt for the passphrase when needed.
- For server/config usage you can run non‑interactively by setting `LUCLI_SECRETS_PASSPHRASE` to the store passphrase.
- The key is kept in memory for the lifetime of the LuCLI process; it is not written to disk.

### Output

- `secrets list` never prints values.
- `secrets get` requires `--show` to print the value.
- Configuration resolution replaces placeholders in memory and avoids logging resolved secrets.

---

## Limitations and Notes

- Only the **local** provider is implemented today.
- Secrets are scoped to the current user account (via `~/.lucli`).
- LuCLI currently prompts on the terminal for the passphrase; non‑interactive use is limited.
- If the passphrase is forgotten there is no recovery mechanism – you will need to `lucli secrets init --reset` and recreate secrets.

---

## Best Practices

1. **Use placeholders wherever possible**
   - Prefer `${secret:...}` in `lucee.json` and CFConfig instead of hard‑coding passwords.

2. **Keep `.env` for non‑secret configuration**
   - Use environment variables for ports, hostnames, and feature flags.
   - Use secrets for anything you wouldn’t commit to source control.

3. **Prefer `LUCLI_SECRETS_PASSPHRASE` for headless use**
   - In CI or services, set `LUCLI_SECRETS_PASSPHRASE` so servers can start without interactive prompts.

4. **Avoid `secrets get --show` in automation**
   - When you need automation, prefer configuration‑driven secrets (placeholders) over shell pipelines that print secrets.

5. **Back up source of truth, not the encrypted file**
   - Treat `~/.lucli/secrets/local.json` as an implementation detail; your real source of truth should be whatever system you use to regenerate secrets if needed.

