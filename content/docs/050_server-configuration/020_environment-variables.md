---
title:  Using Environment Variables
layout: docs
---

LuCLI supports environment variable substitution throughout your `lucee.json` configuration, allowing you to externalize sensitive data and make your configurations portable across different environments.

## Overview

This page focuses on how environment variables and `.env` files are resolved into your configuration. For how environment blocks (`environments.<name>`) are merged with the base config (including load order and deep‑merge rules), see [Environments and Configuration Overrides](../050_server-configuration/030_environments-and-overrides/).

Environment variables can be used in any string field within `lucee.json` using the `#env:VAR#` substitution syntax. Variables are resolved when the configuration is loaded, enabling dynamic server configuration.

## Syntax

### Basic Variable Substitution

Use `#env:VARIABLE_NAME#` to substitute the value of an environment variable:

```json
{
  "port": "#env:HTTP_PORT#",
  "version": "#env:LUCEE_VERSION#",
  "jvm": {
    "maxMemory": "#env:JVM_MAX_MEMORY#"
  }
}
```

### Default Values

Use `#env:VARIABLE_NAME:-default_value#` to provide a fallback value when the variable is not set:

```json
{
  "port": "#env:HTTP_PORT:-8080#",
  "version": "#env:LUCEE_VERSION:-6.2.2.91#",
  "jvm": {
    "maxMemory": "#env:JVM_MAX_MEMORY:-512m#",
    "minMemory": "#env:JVM_MIN_MEMORY:-128m#"
  }
}
```

> **Why `#env:VAR#` instead of `${VAR}`?**  
> Lucee and the JVM also use `${VAR}` for their own runtime variable resolution (e.g. in `.CFConfig.json` and JVM system properties). Using `#env:VAR#` for LuCLI substitution avoids ambiguity — you always know which tool will resolve a given placeholder. The `env:` prefix also makes it explicit that this is an environment variable lookup, distinguishing it from `#secret:NAME#` and potential future CFML expression support.

## Variable Sources

Variables are resolved in the following order of precedence:

1. **`.env` file variables** (highest priority) - loaded from `.env` in the project directory
2. **System environment variables** - from `echo $VAR_NAME`
3. **Default values** - specified in the config using `#env:VAR:-default#`
4. **Placeholder** - if none of the above, the placeholder remains unchanged (e.g., `#env:UNKNOWN#`)

## .env File

Create a `.env` file in the same directory as `lucee.json` to define local environment variables that will be automatically loaded:

```bash
# .env
HTTP_PORT=9090
LUCEE_VERSION=7.0.0.346
DB_HOST=localhost
DB_PORT=3306
DB_USER=admin
DB_PASSWORD="secret123"
JVM_MAX_MEMORY=2g
ENABLE_CACHE=true
```

### .env File Format

- **Key-Value pairs:** `KEY=VALUE`
- **Comments:** Lines starting with `#`
- **Quoted values:** Both single (`'value'`) and double (`"value"`) quotes are supported
- **Empty lines:** Ignored

Example:
```bash
# Database configuration
DB_HOST=localhost
DB_PORT=3306
DB_USER="admin"
DB_PASSWORD='my-secure-pass'

# JVM Settings
JVM_MAX_MEMORY=2g
JVM_MIN_MEMORY=512m
```

## Supported Fields

The `#env:VAR#` substitution is supported in all string fields within `lucee.json`:

- **Top-level string fields:** `version`, `name`, `webroot`, `openBrowserURL`, `configurationFile`
- **JVM configuration:** `jvm.maxMemory`, `jvm.minMemory`, `jvm.additionalArgs`
- **URL Rewrite:** `urlRewrite.routerFile`
- **Runtime configuration:** `runtime.type`, `runtime.installPath`, `runtime.variant`, `runtime.catalinaHome`, `runtime.catalinaBase`, `runtime.jettyHome`, `runtime.image`, `runtime.dockerfile`, `runtime.context`, `runtime.tag`, `runtime.containerName`, `runtime.runMode`
- **`configuration` object:** `#env:VAR#` placeholders are resolved; `${VAR}` is preserved for Lucee runtime

### Protected zones

Some fields pass through to Lucee or the JVM, where `${...}` has its own meaning. In these zones, LuCLI only processes `#env:VAR#` and leaves `${VAR}` untouched:

- **`configuration`** — written to `.CFConfig.json`, where Lucee may resolve `${...}` at runtime
- **`environments.<env>.configuration`** — same as above, after environment merge
- **`jvm.additionalArgs`** — the JVM resolves `${...}` system properties at runtime

This means you can safely mix LuCLI variables and Lucee/JVM variables:

```json
{
  "configuration": {
    "inspectTemplate": "#env:LUCLI_INSPECT:-once#",
    "password": "${LUCEE_RUNTIME_PASSWORD}"
  },
  "jvm": {
    "additionalArgs": [
      "-Dfile.encoding=#env:MY_ENCODING:-UTF-8#",
      "-Djava.io.tmpdir=${java.io.tmpdir}/lucli"
    ]
  }
}
```

## Example Configuration

```json
{
  "name": "#env:APP_NAME:-my-app#",
  "port": "#env:HTTP_PORT:-8080#",
  "version": "#env:LUCEE_VERSION:-6.2.2.91#",
  "webroot": "#env:PROJECT_WEBROOT:-.\/# ",
  "jvm": {
    "maxMemory": "#env:JVM_MAX_MEMORY:-512m#",
    "minMemory": "#env:JVM_MIN_MEMORY:-128m#",
    "additionalArgs": ["#env:JVM_ARG1:-#", "#env:JVM_ARG2:-#"]
  },
  "configuration": {
    "datasources": {
      "primary": {
        "host": "#env:DB_HOST:-localhost#",
        "port": "#env:DB_PORT:-3306#",
        "database": "#env:DB_NAME:-myapp#",
        "username": "#env:DB_USER:-admin#",
        "password": "#env:DB_PASSWORD:-password#"
      }
    },
    "mappings": {
      "/api": "#env:API_MAPPING_PATH:-/var/api#",
      "/static": "#env:STATIC_PATH:-.\/static#"
    },
    "settings": {
      "timeout": "#env:REQUEST_TIMEOUT:-30000#",
      "enableCache": "#env:ENABLE_CACHE:-true#",
      "logLevel": "#env:LOG_LEVEL:-info#"
    }
  }
}
```

## Usage Examples

### Using Environment Variables at Runtime

```bash
# Set environment variables
export HTTP_PORT=9090
export LUCEE_VERSION=7.0.0.346
export DB_HOST=db.example.com

# Start server with substituted variables
lucli server start

# Preview the resolved configuration
lucli server start --dry-run
```

### Using .env File

```bash
# Create .env in project directory
cat > .env << EOF
HTTP_PORT=9090
LUCEE_VERSION=7.0.0.346
DB_HOST=db.example.com
DB_USER=admin
DB_PASSWORD=secret123
EOF

# Variables are automatically loaded and applied
lucli server start
```

### Getting and Setting Configuration Values

```bash
# Get a configuration value
lucli server config get port
lucli server config get jvm.maxMemory

# Set configuration values (with or without variables)
lucli server config set port=8080
lucli server config set version=6.2.2.91
lucli server config set port=8080 admin.enabled=false

# Use variables when setting config
lucli server config set port='#env:HTTP_PORT:-8080#' version='#env:LUCEE_VERSION:-6.2.2.91#'

# Preview changes without saving
lucli server config set port='#env:HTTP_PORT:-8080#' --dry-run
```

## Best Practices

1. **Use `.env` for development:** Keep sensitive data in `.env` which can be added to `.gitignore`
2. **Use environment variables for CI/CD:** Set via deployment scripts or container orchestration
3. **Provide sensible defaults:** Always use `#env:VAR:-default#` unless a value is required
4. **Document required variables:** List all expected variables in a README or `.env.example`
5. **Don't commit `.env`:** Add `.env` to `.gitignore` to prevent accidental commits of sensitive data

### Example .env.example

```bash
# Copy this file to .env and fill in your actual values
# The .env file is automatically loaded by LuCLI

# Server Configuration
HTTP_PORT=8080
LUCEE_VERSION=6.2.2.91

# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=myapp
DB_USER=admin
DB_PASSWORD=change_me

# JVM Settings
JVM_MAX_MEMORY=512m
JVM_MIN_MEMORY=128m

# Application
APP_NAME=my-app
ENABLE_CACHE=true
LOG_LEVEL=info
```

## Deprecated Syntax

### `${VAR}` syntax (deprecated)

The `${VAR}` and `${VAR:-default}` syntax for LuCLI variable substitution is **deprecated**. It still works outside protected zones but will emit a warning and will be removed in a future release.

Note: `${VAR}` inside `configuration` and `jvm.additionalArgs` blocks is **not** deprecated there — it was never substituted by LuCLI in those zones and is left for Lucee/JVM runtime.

### Bare `#VAR#` syntax (deprecated)

The bare `#VAR#` syntax (without the `env:` prefix) is also **deprecated**. It still works for backward compatibility but will emit a one-time warning suggesting you use `#env:VAR#` instead.

If you see a deprecation warning, update your `lucee.json`:

```json
// Before (deprecated)
"password": "${ADMIN_PW}"
"port": "#HTTP_PORT#"

// After (preferred)
"password": "#env:ADMIN_PW#"
"port": "#env:HTTP_PORT#"
```

## Troubleshooting

### Variable Not Substituted

If you see `#env:VARIABLE_NAME#` in your output instead of the value:

1. Check that the variable is defined in `.env` or system environment
2. Verify the variable name matches exactly (case-sensitive)
3. Use `--dry-run` to preview resolved values: `lucli server start --dry-run`
4. Use `lucli server config get <key>` to inspect current values

### Variables in .env Not Loading

- Ensure `.env` is in the same directory as `lucee.json`
- Check file permissions (should be readable)
- Verify file format: `KEY=VALUE` (one per line)
- Check console output for warning messages about `.env` loading
