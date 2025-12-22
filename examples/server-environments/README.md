# Environment-Based Server Configuration

Demonstrates how to use environment-specific configuration overrides for different deployment scenarios (production, development, staging).

## What This Shows

- Multiple environment configurations in a single `lucee.json`
- Deep-merge behavior between base and environment configs
- Environment persistence across server restarts
- Dry-run mode to preview merged configuration
- Different settings per environment (ports, memory, monitoring)

## Prerequisites

- LuCLI installed
- Java 17+ installed

## Quick Start

```bash
cd examples/server-environments

# Start with development environment
lucli server start --env dev

# View at http://localhost:8891

# Stop and start with production environment
lucli server stop
lucli server start --env prod

# View at http://localhost:8080

# Stop server
lucli server stop
```

## Configuration Overview

The `lucee.json` defines a base configuration plus three environments:

### Base Configuration
- Port: 8890
- Memory: 512m max
- Monitoring: enabled (JMX port 9001)

### Environments

**Production (`--env prod`)**
- Port: 8080
- Memory: 2048m (overridden)
- Monitoring: disabled
- Admin password: secured
- Browser: doesn't auto-open

**Development (`--env dev`)**
- Port: 8891
- Memory: 512m (inherited from base)
- Monitoring: enabled with JMX port 9002
- Browser: auto-opens

**Staging (`--env staging`)**
- Port: 8892
- Memory: 1024m (overridden)
- Admin password: secured

## Deep Merge Behavior

When you specify an environment, LuCLI performs a deep merge:

1. **LuCLI defaults** (built-in)
2. **Base configuration** (from `lucee.json`)
3. **Environment overrides** (from `environments.{env}`)

### Merge Rules

- Nested objects merge recursively
- Scalar values replace completely
- Arrays replace completely (not merged)
- `null` values remove the corresponding base value

### Example Merge

Base config has:
```json
{
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m"
  }
}
```

Production environment has:
```json
{
  "jvm": {
    "maxMemory": "2048m"
  }
}
```

Result: `maxMemory` is overridden to 2048m, `minMemory` remains 128m (inherited).

## Server Commands

```bash
# Start with specific environment
lucli server start --env=prod
lucli server start --env dev
lucli server start --env=staging

# Preview merged configuration (dry-run)
lucli server start --env=prod --dry-run

# Check which environment is running
lucli server list
# Output: server-environments-example [prod]    RUNNING    12345    8080

lucli server status
# Output: Server Name:   server-environments-example [env: prod]

# Start without environment (uses base config)
lucli server start
# Runs on port 8890 with base settings

# Stop server
lucli server stop
```

## Environment Persistence

When you start a server with an environment, LuCLI saves it to `~/.lucli/servers/{name}/.environment`. The environment is displayed in:
- `lucli server list` - Shows `[env]` next to server name
- `lucli server status` - Shows which environment is active

## Error Handling

If you specify an invalid environment:
```bash
lucli server start --env invalid
```

Output:
```
❌ Environment 'invalid' not found in lucee.json
Available environments: prod, dev, staging
```

## Use Cases

### Development
```bash
lucli server start --env dev
# Higher logging, monitoring enabled, browser opens automatically
```

### Testing/CI
```bash
lucli server start --env staging
# Moderate resources, secured admin, suitable for integration tests
```

### Production
```bash
lucli server start --env prod
# Maximum memory, monitoring disabled, secured, no browser opening
```

## Key Files

- **lucee.json** - Multi-environment server configuration
- **index.cfm** - Demo page showing active environment
- **README.md** - This file

## Expected Output

When you visit the running server, you'll see:
- Active environment name
- Merged configuration values
- Port and memory settings
- JMX monitoring status

## Clean Up

```bash
lucli server stop
```

Server files are stored in `~/.lucli/servers/server-environments-example/` and can be removed if desired.

## Learn More

- [Environment Configuration Documentation](../../WARP.md#environment-based-configuration)
- [Server Management](../../WARP.md#server-management)
- [lucee.json Schema](../../schemas/v1/lucee.schema.json)
