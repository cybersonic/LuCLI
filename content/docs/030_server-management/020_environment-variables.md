---
title: Environment Variables
layout: docs
---

# Environment Variables in LuCLI

LuCLI supports environment variable substitution throughout your `lucee.json` configuration, allowing you to externalize sensitive data and make your configurations portable across different environments.

## Overview

Environment variables can be used in any string field within `lucee.json` using standard substitution syntax. Variables are resolved when the configuration is loaded, enabling dynamic server configuration.

## Syntax

### Basic Variable Substitution

Use `${VARIABLE_NAME}` to substitute the value of an environment variable:

```json
{
  "port": "${HTTP_PORT}",
  "version": "${LUCEE_VERSION}",
  "jvm": {
    "maxMemory": "${JVM_MAX_MEMORY}"
  }
}
```

### Default Values

Use `${VARIABLE_NAME:-default_value}` to provide a fallback value when the variable is not set:

```json
{
  "port": "${HTTP_PORT:-8080}",
  "version": "${LUCEE_VERSION:-6.2.2.91}",
  "jvm": {
    "maxMemory": "${JVM_MAX_MEMORY:-512m}",
    "minMemory": "${JVM_MIN_MEMORY:-128m}"
  }
}
```

## Variable Sources

Variables are resolved in the following order of precedence:

1. **`.env` file variables** (highest priority) - loaded from `.env` in the project directory
2. **System environment variables** - from `echo $VAR_NAME`
3. **Default values** - specified in the config using `${VAR:-default}`
4. **Placeholder** - if none of the above, the placeholder remains unchanged (e.g., `${UNKNOWN}`)

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

Environment variable substitution is supported in:

- **Top-level string fields:** `version`, `name`, `webroot`, `openBrowserURL`, `configurationFile`
- **JVM configuration:** `jvm.maxMemory`, `jvm.minMemory`, `jvm.additionalArgs`
- **URL Rewrite:** `urlRewrite.routerFile`
- **Entire `configuration` object:** All strings, arrays, and nested objects within the CFConfig can use variables

## Example Configuration

```json
{
  "name": "${APP_NAME:-my-app}",
  "port": "${HTTP_PORT:-8080}",
  "version": "${LUCEE_VERSION:-6.2.2.91}",
  "webroot": "${PROJECT_WEBROOT:-.\/}",
  "jvm": {
    "maxMemory": "${JVM_MAX_MEMORY:-512m}",
    "minMemory": "${JVM_MIN_MEMORY:-128m}",
    "additionalArgs": ["${JVM_ARG1:-}", "${JVM_ARG2:-}"]
  },
  "configuration": {
    "datasources": {
      "primary": {
        "host": "${DB_HOST:-localhost}",
        "port": "${DB_PORT:-3306}",
        "database": "${DB_NAME:-myapp}",
        "username": "${DB_USER:-admin}",
        "password": "${DB_PASSWORD:-password}"
      }
    },
    "mappings": {
      "/api": "${API_MAPPING_PATH:-/var/api}",
      "/static": "${STATIC_PATH:-.\/static}"
    },
    "settings": {
      "timeout": "${REQUEST_TIMEOUT:-30000}",
      "enableCache": "${ENABLE_CACHE:-true}",
      "logLevel": "${LOG_LEVEL:-info}"
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
lucli server config set port='${HTTP_PORT:-8080}' version='${LUCEE_VERSION:-6.2.2.91}'

# Preview changes without saving
lucli server config set port='${HTTP_PORT:-8080}' --dry-run
```

## Future: Java System Properties

> ⚠️ **This feature is currently under consideration and not yet implemented.**

**Proposed syntax** for Java system properties:

```json
{
  "appName": "${java:app.name}",
  "version": "${java:app.version}"
}
```

This would allow access to Java system properties set via JVM arguments:

```bash
java -Dapp.name=myapp -Dapp.version=1.0 -jar lucli.jar ...
```

### Design

The proposed `${java:...}` syntax provides:
- Clear distinction from environment variables (which use `${VAR}`)
- Access to Java system properties without conflict
- Standard Java property notation

Implementation would require:
1. Detecting the `java:` prefix in variable substitution
2. Resolving via `System.getProperty()` instead of `System.getenv()`
3. Updated documentation and examples

## Best Practices

1. **Use `.env` for development:** Keep sensitive data in `.env` which can be added to `.gitignore`
2. **Use environment variables for CI/CD:** Set via deployment scripts or container orchestration
3. **Provide sensible defaults:** Always use `${VAR:-default}` unless a value is required
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

## Troubleshooting

### Variable Not Substituted

If you see `${VARIABLE_NAME}` in your output instead of the value:

1. Check that the variable is defined in `.env` or system environment
2. Verify the variable name matches exactly (case-sensitive)
3. Use `--dry-run` to preview resolved values: `lucli server start --dry-run`
4. Use `lucli server config get <key>` to inspect current values

### Variables in .env Not Loading

- Ensure `.env` is in the same directory as `lucee.json`
- Check file permissions (should be readable)
- Verify file format: `KEY=VALUE` (one per line)
- Check console output for warning messages about `.env` loading

## See Also

- [CONFIGURATION.md](CONFIGURATION.md) - Complete configuration reference
- [EXECUTING_COMMANDS.md](EXECUTING_COMMANDS.md) - Running servers and commands
