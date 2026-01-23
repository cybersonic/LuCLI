---
title: Dependency Management
layout: docs
---

LuCLI can manage CFML libraries and Lucee extensions as dependencies for your project, using the `dependencies`, `devDependencies`, and `dependencySettings` sections in `lucee.json`. Installed dependencies are recorded in `lucee-lock.json`, which is also used during server startup.

This page focuses on how dependencies and extensions work, independent of the rest of the `lucee.json` configuration.

## Git dependency caching

LuCLI can manage CFML dependencies and Lucee extensions via the `dependencies`, `devDependencies`, and `dependencySettings` sections in `lucee.json`. Installed dependencies are recorded in `lucee-lock.json`, which is also used by server locking.

### Git dependency caching

Git-based dependencies (`source: "git"`) are installed by cloning their repositories and copying the requested `ref` (and optional `subPath`) into the project `installPath`. To speed up repeated installs, LuCLI maintains a shared git cache under:

- `~/.lucli/deps/git-cache` (or `${LUCLI_HOME}/deps/git-cache` when `LUCLI_HOME` / `-Dlucli.home` is set)

Behavior:

- The first time a given git dependency is installed, LuCLI clones the repository into the cache directory.
- Subsequent installs of the same dependency reuse the cached clone, running `git fetch --tags` to pull the latest refs before checking out the requested `ref`.
- Each cached repository is keyed by **name + URL hash**, so different dependencies that share the same name but use different URLs will not collide.
- If the cached clone becomes invalid (broken `.git` dir, fetch failure, etc.), LuCLI automatically deletes that cache entry and re-clones it once.

You can control this behavior via a user-level setting in `~/.lucli/settings.json`:

- `usePersistentGitCache` (boolean, default: `true`)
  - When `true`, LuCLI uses the shared cache under `~/.lucli/deps/git-cache`.
  - When `false`, LuCLI uses a throwaway clone per install and deletes it afterwards (slower, but leaves no cached git repositories on disk).

To manually clear the git cache, use:

```bash
lucli deps prune
```

This removes all cached git clones under `~/.lucli/deps/git-cache`.

At a high level:

- `dependencies` – production dependencies (CFML libraries, Lucee extensions, etc.).
- `devDependencies` – development-only dependencies.
- `dependencySettings` – controls how and where dependencies are installed.
- `lucee-lock.json` – records the resolved versions, install paths, and mappings.
- `LUCEE_EXTENSIONS` – built automatically from locked extension dependencies when the server starts.

### Declaring Lucee extensions as dependencies

You can declare Lucee extensions in `lucee.json` using `type: "extension"`. LuCLI supports several ways to identify the extension:

```json
{
  "dependencies": {
    "h2": {
      "type": "extension",
      "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
    },
    "redis": {
      "type": "extension",
      "slug": "redis"  
    },
    "my-local-ext": {
      "type": "extension",
      "path": "./extensions/my-local-ext.lex"
    },
    "custom-provider-ext": {
      "type": "extension",
      "url": "https://extensions.example.com/my-ext.lex"
    }
  }
}
```

Supported forms:

- **By ID**: `id` is a Lucee extension UUID.
- **By slug/name**: `slug` or name is resolved via `lucee-extensions.json` (see `src/main/resources/extensions/EXTENSION_REGISTRY.md`).
- **By local path**: `path` points to a `.lex` file on disk.
- **By URL**: `url` points to a `.lex` download URL.

### Installing dependencies (including extensions)

Use the dependency installer command from the project root:

```bash
# Install all dependencies and devDependencies
lucli deps install

# Production-only install (skip devDependencies)
lucli deps install --production

# Apply environment-specific dependencySettings & overrides
lucli deps install --env prod

# Preview what would be installed and the realized dependency config for the root project
lucli deps install --dry-run

# Preview dependencies for the root project AND any nested projects that contain a lucee.json
lucli deps install --dry-run --include-nested-deps
```

Behaviour:

- Reads `dependencies`, `devDependencies`, and `dependencySettings` from `lucee.json`.
- Applies environment overrides (when `--env` is used) before resolving dependencies.
- Installs supported dependency types:
  - `source: "git"` – CFML libraries installed from Git repositories.
  - `type: "extension"` – Lucee extensions installed via providers, URLs, or local `.lex` files.
- Writes a normalized record for each dependency into `lucee-lock.json` (including version, source, install path, and, for extensions, their Lucee ID).
- Prints the `LUCEE_EXTENSIONS` value that will be set when the server starts, built from all locked extension dependencies.

### Nested dependency projects

When a dependency's install directory contains its own `lucee.json`, LuCLI treats it as a nested project and can install its dependencies as well.

- `lucli deps install` always works from the current directory as the **root project**.
- For each installed (or reused) dependency with a directory install path:
  - If `<installPath>/lucee.json` exists, that directory is treated as a nested LuCLI project.
  - LuCLI runs the same resolution/install logic in that directory, writing a separate `lucee-lock.json` alongside the nested `lucee.json`.
- Nested installs are **recursive but depth-limited** to avoid cycles and infinite graphs.
- Each nested project manages its own `lucee-lock.json`; the root project's lock file is not flattened.
- Server startup still uses **only the root project's** `lucee-lock.json` to compute `LUCEE_EXTENSIONS`.

Dry-run semantics for nested projects:

- `lucli deps install --dry-run`
  - Shows only the root project's dependencies and realized `lucee.json` configuration.
  - Does **not** list nested projects, to keep the output compact by default.
- `lucli deps install --dry-run --include-nested-deps`
  - Shows the root project first.
  - Then prints an additional `"[Nested: <relative/path>] Would install:"` section for each nested project discovered from the current dependency graph, up to the depth limit.
  - Still avoids any file system changes (no clones, no lock writes).

Environment handling for nested projects:

- The root project uses strict environment application: `--env foo` will fail if `foo` is not defined under `environments` in the root `lucee.json`.
- Nested projects apply the same `--env` **leniently**:
  - If the nested `lucee.json` does not define that environment, LuCLI logs a warning and continues with the base configuration for that project instead of failing the whole run.

### How extensions are activated at server startup

When you start a server, LuCLI:

1. Reads `lucee-lock.json` for the project.
2. Collects all locked dependencies with `type: "extension"`.
3. Builds a comma-separated `LUCEE_EXTENSIONS` string from those locked entries.
4. Injects `LUCEE_EXTENSIONS` into the server process environment (along with any other `envVars`).
5. Lucee reads `LUCEE_EXTENSIONS` at startup and installs/activates the listed extensions.

This means:

- The **set of active extensions is driven by `lucee-lock.json`**, not by `lucee.json` alone.
- Running `lucli deps install` is the canonical way to update which extensions are installed and locked for a project.
- Server lock (`lucee-lock.json` → `serverLocks`) coexists with dependency locking; updating dependencies and updating server locks are separate, explicit steps.

For more about dependency structure, see `LuceeJsonConfig` and `DependencySettingsConfig` in the codebase, and for lock format see `LuceeLockFile`.

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
| `envFile`           | string  | `.env`                                    | Optional path to a project-specific env file used for `${VAR}` substitution in `lucee.json`. Relative paths are resolved against the project directory.                        |
| `envVars`           | object  | `{}`                                      | Additional environment variables to inject into the Tomcat process. Values support `${VAR}` / `${VAR:-default}` and are resolved from `envFile` + system environment.          |
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
