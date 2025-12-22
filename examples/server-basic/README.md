# Basic Server Configuration

Demonstrates the minimal configuration needed to run a Lucee server with LuCLI.

## What This Shows

- Minimal `lucee.json` configuration
- Starting and stopping a Lucee server
- Default server settings and behavior
- Server information and status

## Prerequisites

- LuCLI installed
- Java 17+ installed

## Quick Start

```bash
cd examples/server-basic

# Start the server
lucli server start

# Open http://localhost:8888 to see the demo

# Stop the server
lucli server stop
```

## What Happens

1. **Config** - LuCLI reads the minimal `lucee.json` configuration
2. **Download** - If needed, downloads Lucee 6.2.2.91
3. **Start** - Starts Tomcat with Lucee on port 8888
4. **Deploy** - Deploys your webroot to the server

## Key Files

- **lucee.json** - Minimal server configuration (name and port only)
- **index.cfm** - Simple demo page showing server info

## Configuration Defaults

When you provide minimal configuration, LuCLI uses these defaults:

```json
{
  "version": "6.2.2.91",
  "webroot": "./",
  "monitoring": { "enabled": true, "jmx": { "port": 8999 } },
  "jvm": { "maxMemory": "512m", "minMemory": "128m" },
  "urlRewrite": { "enabled": true, "routerFile": "index.cfm" },
  "admin": { "enabled": true, "password": "" },
  "enableLucee": true,
  "enableREST": false,
  "openBrowser": true
}
```

## Server Commands

```bash
# Start server
lucli server start

# Start with specific version
lucli server start --version 6.2.2.91

# Stop server
lucli server stop

# Check server status
lucli server status

# List all servers
lucli server list

# View server configuration (dry-run)
lucli server start --dry-run
```

## Expected Output

When you visit http://localhost:8888, you should see:
- Server name and version
- Current working directory
- Lucee administrator link
- Server status information

## Clean Up

```bash
lucli server stop
```

Server files are stored in `~/.lucli/servers/server-basic-example/` and can be removed if desired.

## Learn More

- [Server Management Documentation](../../WARP.md#server-management)
- [lucee.json Schema](../../schemas/v1/lucee.schema.json)
