---
title: Server Configuration
layout: docs
---

This page describes all settings currently supported in `lucee.json` for LuCLI-managed Lucee/Tomcat servers. The example below is a **complete, working configuration** you can copy, paste, and then customize.

## Complete example `lucee.json`

```json
{
  "name": "my-project",
  "version": "6.2.2.91",
  "port": 8080,
  "shutdownPort": 9080,
  "webroot": "./",
  "monitoring": {
    "enabled": true,
    "jmx": {
      "port": 8999
    }
  },
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m",
    "additionalArgs": []
  },
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  },
  "admin": {
    "enabled": true
  },
  "enableLucee": true,
  "agents": {
    "luceedebug": {
      "enabled": false,
      "jvmArgs": [
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999"
      ],
      "description": "Lucee step debugger agent"
    }
  },
  "openBrowser": true,
  "openBrowserURL": "",
  "configuration": {},
  "configurationFile": "",
  "environments": {
    "prod": {
      "port": 80,
      "jvm": {
        "maxMemory": "2048m"
      },
      "admin": {
        "password": "secure_password"
      },
      "monitoring": {
        "enabled": false
      },
      "openBrowser": false
    },
    "dev": {
      "port": 8081,
      "monitoring": {
        "jmx": {
          "port": 9000
        }
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

- All fields are optional; sensible defaults are applied when omitted.
- For a minimal configuration you can safely delete keys you do not need.
- `configurationFile` and `configuration` work together: the external file is loaded first as a base config, then inline `configuration` values override the file values. This allows shared base configs with per-project overrides.

## Minimal HTTPS example

```json
{
  "https": { "enabled": true }
}
```

Optional custom hostname (used for default URLs and certificate SANs):

```json
{
  "host": "myproject.localhost",
  "https": { "enabled": true }
}
```

## Environment-Based Configuration

LuCLI supports environment-specific configuration overrides, allowing you to define different settings for production, development, staging, etc. within a single `lucee.json` file.

### Quick Start

```bash
# Start server with production environment
lucli server start --env=prod

# Start with development environment
lucli server start --env dev

# Preview merged configuration without starting
lucli server start --env=prod --dry-run
```

### Configuration Load Order

When using environments, configuration is loaded and merged in this order:

1. **LuCLI defaults** (built-in defaults)
2. **Configuration file** (if `"configurationFile"` is specified)
3. **Base configuration** (top-level settings in `lucee.json`)
4. **Environment overrides** (when `--env` flag is used)

### Deep Merge Behavior

Environment configurations are deep-merged with the base configuration:

- **Nested objects** are merged recursively (e.g., `jvm.maxMemory` can be overridden while keeping `jvm.minMemory`)
- **Array values** are replaced entirely (not merged)
- **Null values** in environments remove the corresponding base value
- **Scalar values** (strings, numbers, booleans) replace base values

### Example with Environments

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
      "admin": {
        "password": "secure_password"
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

**With this configuration:**

- **Base** (no `--env`): port 8080, maxMemory 512m, monitoring enabled
- **`--env=prod`**: port 80, maxMemory 2048m, minMemory 128m (inherited), monitoring disabled, password set, browser disabled
- **`--env=dev`**: port 8081, maxMemory 512m (inherited), JMX port 9000, monitoring enabled
- **`--env=staging`**: port 8082, maxMemory 1024m, minMemory 128m (inherited), other settings from base

### Environment Persistence

When a server starts with an environment, it's saved to `~/.lucli/servers/{name}/.environment` and displayed in server listings:

```bash
$ lucli server list
my-app [prod]    RUNNING    12345    80
other-app        RUNNING    12346    8080
```

```bash
$ lucli server status my-app
Server Name:   my-app [env: prod]
Status:        RUNNING
PID:           12345
Port:          80
```

### Error Handling

If you specify an invalid environment name, LuCLI will show an error with available options:

```bash
$ lucli server start --env=invalid
❌ Environment 'invalid' not found in lucee.json
Available environments: prod, dev, staging
```

### Use Cases

**Development vs. Production:**
- Lower memory limits for dev, higher for production
- Different ports to avoid conflicts
- JMX monitoring enabled in dev, disabled in production
- Browser auto-open in dev, disabled in production

**Security:**
- Admin passwords set only in production environments
- Debug agents enabled only in development
- HTTPS enforced in production, optional in dev

**Resource Management:**
- Different JVM heap sizes per environment
- Staging environment with moderate resources
- Production-like staging for realistic testing

---

## Settings reference

This section expands on the basic configuration reference and documents every available setting.

### Top-level settings

| Key                 | Type    | Default                                   | Description                                                                                                                                                                     |
| ------------------- | ------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `name`              | string  | project folder name                       | Human-friendly server name. Used as the directory name under `~/.lucli/servers/` and in CLI output.                                                                             |
| `version`           | string  | `6.2.2.91`                                | Lucee Express version to download and use for this server.                                                                                                                      |
| `port`              | integer | `8080` (auto-adjusted to avoid conflicts) | Primary HTTP port for Tomcat.                                                                                                                                                   |
| `shutdownPort`      | integer | `port + 1000`                             | Tomcat shutdown port. When omitted, LuCLI derives this from `port`.                                                                                                             |
| `webroot`           | string  | `"./"`                                    | Webroot/docBase for the Tomcat context. May be relative to the project directory or absolute.                                                                                   |
| `host`              | string  | `localhost`                               | Hostname used when constructing default URLs and (when HTTPS is enabled) generating a self-signed cert SAN.                                                                     |
| `openBrowser`       | boolean | `true`                                    | When `true`, LuCLI tries to open a browser after the server starts.                                                                                                             |
| `openBrowserURL`    | string  | (computed)                                | Optional custom URL to open instead of the default computed URL. Empty string means "use the default".                                                                         |
| `enableLucee`       | boolean | `true`                                    | When `false`, Lucee servlets and CFML mappings are removed from `web.xml` and Tomcat acts as a static HTTP file server (HTML/HTM, assets, etc.).                                |
| `enableRest`        | boolean | `false`                                   | When `true`, Rest servlets are enabled. Requires `enableLucee` to be `true`.                                                                                                    |
| `configurationFile` | string  | not set                                   | Path to an external CFConfig JSON file (base config). Relative paths are resolved against the project directory. This is loaded first as the foundation for Lucee CFConfig.     |
| `configuration`     | object  | `null`                                    | Inline Lucee CFConfig JSON that overrides/extends values from `configurationFile`. The final merged config is written to `lucee-server/context/.CFConfig.json` on server start. |
| `monitoring`        | object  | see below                                 | JMX/monitoring configuration.                                                                                                                                                   |
| `jvm`               | object  | see below                                 | JVM memory and extra arguments.                                                                                                                                                 |
| `urlRewrite`        | object  | see below                                 | URL rewriting / router configuration.                                                                                                                                           |
| `admin`             | object  | see below                                 | Lucee administrator exposure.                                                                                                                                                   |
|| `https`             | object  | disabled                                 | Optional HTTPS configuration. When enabled, LuCLI adds an HTTPS connector and generates a per-server PKCS12 keystore under the server instance directory.                        |
|| `agents`            | object  | `{}`                                      | Named Java agents (debuggers, JMX exporters, etc.), keyed by agent ID.                                                                                                          |
|| `environments`      | object  | `{}`                                      | Named environment configurations that can override base settings. See [Environment-Based Configuration](#environment-based-configuration) section above for details.            |

### `monitoring` settings

```json
"monitoring": {
  "enabled": true,
  "jmx": {
    "port": 8999
  }
}
```

| Key                   | Type    | Default | Description |
|-----------------------|---------|---------|-------------|
| `monitoring.enabled`  | boolean | `true`  | Enables JMX monitoring for this server. When `false`, no JMX system properties are added to the JVM. |
| `monitoring.jmx.port` | integer | `8999`  | JMX port used by the monitoring dashboard and external tools. Must not conflict with `port` or `shutdownPort`. |

### `jvm` settings

```json
"jvm": {
  "maxMemory": "512m",
  "minMemory": "128m",
  "additionalArgs": []
}
```

| Key                 | Type     | Default | Description |
|---------------------|----------|---------|-------------|
| `jvm.maxMemory`     | string   | `"512m"` | Maximum heap size, passed as `-Xmx` (e.g. `"1024m"`, `"2g"`). |
| `jvm.minMemory`     | string   | `"128m"` | Initial heap size, passed as `-Xms`. |
| `jvm.additionalArgs`| string[] | `[]`    | Extra JVM arguments appended to `CATALINA_OPTS` (e.g. GC tuning flags, `-D` system properties, or `-javaagent:` if you do not use `agents`). |

### `urlRewrite` settings

```json
"urlRewrite": {
  "enabled": true,
  "routerFile": "index.cfm"
}
```

| Key                    | Type    | Default       | Description |
|------------------------|---------|---------------|-------------|
| `urlRewrite.enabled`   | boolean | `true`        | Enables framework-style URL rewriting using `urlrewrite.xml`. When `false`, no UrlRewrite filter or configuration is installed. |
| `urlRewrite.routerFile`| string  | `"index.cfm"`| Central router script used by the URL rewrite rules for extensionless URLs. In static-only sites you may want to set this to `"index.html"`. |

### `admin` settings

```json
"admin": {
  "enabled": true
}
```

| Key              | Type    | Default | Description |
|------------------|---------|---------|-------------|
| `admin.enabled`  | boolean | `true`  | When `true`, Lucee administrator URLs are mapped under `/lucee/` in `web.xml`. When `false`, those mappings are removed. |

### `agents` settings

Agents let you define reusable sets of JVM arguments (typically `-javaagent:...` and related flags) under a stable identifier that can be toggled at `lucli server start` time.

```json
"agents": {
  "luceedebug": {
    "enabled": false,
    "jvmArgs": [
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999"
    ],
    "description": "Lucee step debugger agent"
  }
}
```

| Key                         | Type      | Default | Description |
|-----------------------------|-----------|---------|-------------|
| `agents`                    | object    | `{}`    | Map of agent IDs to agent configuration objects. |
| `agents.<id>.enabled`       | boolean   | `false` | Whether this agent is active by default when starting the server without overrides. |
| `agents.<id>.jvmArgs`       | string[]  | `[]`    | JVM arguments to append when this agent is active (e.g. `-javaagent:...`, `-agentlib:jdwp=...`). Each entry is a full token as it should appear on the JVM command line. |
| `agents.<id>.description`   | string    | `null`  | Optional human-readable description used in documentation and diagnostics. |

---

## Using `lucli server start` with configuration overrides

You can override any of the documented `lucee.json` keys at startup time using bare `key=value` arguments. When `lucee.json` does not exist yet, LuCLI will create a default configuration **and then apply your overrides** before starting the server.

Examples:

```bash
# Static HTML/HTM server (no Lucee, no URL rewrite, no monitoring)
lucli server start enableLucee=false monitoring.enabled=false urlRewrite.enabled=false

# Start with a custom port and disable browser auto-open
lucli server start port=8081 openBrowser=false
```

These overrides are persisted back into `lucee.json` so that subsequent `lucli server start` calls reuse the same configuration.
