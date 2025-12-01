# Lucee Server Configuration (`lucee.json`)

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
  "configurationFile": ""
}
```

- All fields are optional; sensible defaults are applied when omitted.
- For a minimal configuration you can safely delete keys you do not need.
- `configuration` and `configurationFile` are mutually exclusive in practice; typically you use one or the other.

---

## Settings reference

This section expands on the basic configuration reference and documents every available setting.

### Top-level settings

| Key                | Type      | Default        | Description |
|--------------------|-----------|----------------|-------------|
| `name`             | string    | project folder name | Human-friendly server name. Used as the directory name under `~/.lucli/servers/` and in CLI output. |
| `version`          | string    | `6.2.2.91`     | Lucee Express version to download and use for this server. |
| `port`             | integer   | `8080` (auto-adjusted to avoid conflicts) | Primary HTTP port for Tomcat. |
| `shutdownPort`     | integer   | `port + 1000`  | Tomcat shutdown port. When omitted, LuCLI derives this from `port`. |
| `webroot`          | string    | `"./"`        | Webroot/docBase for the Tomcat context. May be relative to the project directory or absolute. |
| `openBrowser`      | boolean   | `true`         | When `true`, LuCLI tries to open a browser after the server starts. |
| `openBrowserURL`   | string    | (computed from `port`) | Optional custom URL to open instead of the default `http://localhost:PORT/`. Empty string means "use the default". |
| `enableLucee`      | boolean   | `true`         | When `false`, Lucee servlets and CFML mappings are removed from `web.xml` and Tomcat acts as a static HTTP file server (HTML/HTM, assets, etc.). |
| `configuration`    | object    | `null`         | Inline Lucee CFConfig JSON, written to `lucee-server/context/.CFConfig.json` on start when present. |
| `configurationFile`| string    | not set        | Path to an external CFConfig JSON file. Relative paths are resolved against the project directory. Ignored when `configuration` is present. |
| `monitoring`       | object    | see below      | JMX/monitoring configuration. |
| `jvm`              | object    | see below      | JVM memory and extra arguments. |
| `urlRewrite`       | object    | see below      | URL rewriting / router configuration. |
| `admin`            | object    | see below      | Lucee administrator exposure. |
| `agents`           | object    | `{}`           | Named Java agents (debuggers, JMX exporters, etc.), keyed by agent ID. |

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
