---
title: Environments and Configuration Overrides
layout: docs
weight: 30
---

LuCLI supports multiple environments (such as `dev`, `staging`, and `prod`) inside a single `lucee.json`. Environment blocks let you override the base configuration without duplicating everything.

This page explains how environments work, how values are merged, and how to use them safely.

## Base vs. environment configuration

A typical `lucee.json`:

```json
{
  "name": "my-app",
  "port": 8080,
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m"
  },
  "monitoring": {
    "enabled": true,
    "jmx": { "port": 8999 }
  },
  "environments": {
    "prod": {
      "port": 80,
      "jvm": {
        "maxMemory": "2048m"
      },
      "monitoring": {
        "enabled": false
      },
      "openBrowser": false
    },
    "dev": {
      "port": 8081,
      "monitoring": {
        "jmx": { "port": 9000 }
      }
    },
    "staging": {
      "port": 8082,
      "jvm": {
        "maxMemory": "1024m"
      }
    }
  }
}
```

- Top‑level keys define the **base** configuration.
- Each entry under `environments` contains **overrides** for that environment.

When you start a server with `--env`, LuCLI merges the chosen environment onto the base config.

## Starting with an environment

Use the `--env` flag:

```bash
# Production configuration
lucli server start --env=prod

# Development configuration
lucli server start --env=dev

# Staging configuration
lucli server start --env=staging
```

You can also **preview** the merged configuration:

```bash
lucli server start --env=prod --dry-run
```

This shows the final, environment‑aware configuration LuCLI will use before actually starting the server.

## Configuration load order

When an environment is specified, LuCLI builds the effective configuration in this order:

1. **LuCLI defaults** (built‑in).
2. **External configuration file** (if `configurationFile` is set).
3. **Base `lucee.json`** (top‑level keys).
4. **Environment overrides** from `environments.<env>`.

Each step overrides the previous one, following the deep‑merge rules below.

## Deep merge behaviour

Environment entries are **deep‑merged** with the base config:

- **Objects** (like `jvm`, `monitoring`) are merged recursively.
- **Scalars** (strings, numbers, booleans) replace the base value.
- **Arrays** are replaced as a whole (not merged entry‑by‑entry).
- **Null** removes the corresponding base value.

### Example

Given the config above:

- **Base (no `--env`)**
  - `port = 8080`
  - `jvm.maxMemory = 512m`, `jvm.minMemory = 128m`
  - `monitoring.enabled = true`, `monitoring.jmx.port = 8999`

- **`--env=prod`**
  - `port = 80` (overrides 8080)
  - `jvm.maxMemory = 2048m` (overrides), `jvm.minMemory = 128m` (inherited)
  - `monitoring.enabled = false` (overrides), `monitoring.jmx.port = 8999` (inherited)
  - `openBrowser = false` (overrides base value or default)

- **`--env=dev`**
  - `port = 8081` (overrides 8080)
  - `jvm` unchanged from base (512m / 128m)
  - `monitoring.enabled = true` (inherited)
  - `monitoring.jmx.port = 9000` (overrides `8999`)

- **`--env=staging`**
  - `port = 8082`
  - `jvm.maxMemory = 1024m` (overrides), `jvm.minMemory = 128m` (inherited)
  - `monitoring` block inherited from base

## Environment persistence & status

When you start a server with an environment, LuCLI remembers it for that server instance. You’ll see it in listings:

```bash
lucli server list
```

Example output:

```text
my-app [prod]    RUNNING   12345   80
my-app [dev]     STOPPED            8081
other-app        RUNNING   12346   8080
```

And in status:

```bash
lucli server status my-app
```

Which includes:

```text
Server Name:   my-app [env: prod]
Status:        RUNNING
Port:          80
```

This makes it obvious which environment a running server is using.

## Invalid environments

If you pass an environment name that does not exist in `lucee.json`, LuCLI fails fast with a clear error and lists the available environments:

```bash
lucli server start --env=invalid
```

You’ll see:

```text
❌ Environment 'invalid' not found in lucee.json
Available environments: prod, dev, staging
```

## Typical workflows

### Development vs. production

- Keep **base** settings close to your local development defaults.
- Use `environments.prod` to:
  - Change ports (e.g. 80 instead of 8080).
  - Increase memory limits.
  - Disable auto‑opening the browser.
  - Disable monitoring or debugging features.

### Staging / pre‑production

Add a `staging` environment that:

- Uses ports and memory closer to production.
- Keeps some debug or logging features enabled.
- Shares most other settings with `prod` via inheritance.

### CI / automation

In CI pipelines:

- Use `--env` to test the exact configuration that will run in a given environment.
- Combine with `--dry-run` to validate configuration without starting a server:

```bash
lucli server start --env=prod --dry-run --include-all
```

This is ideal for configuration validation steps in your pipeline.

---

For the full list of available settings, see  
[Server Configuration](../050_server-configuration/010_lucee-json-basics/).  
For information on how environments interact with locking, see  
[Server Locking](../110_secure-configuration/020-server-lock/).
