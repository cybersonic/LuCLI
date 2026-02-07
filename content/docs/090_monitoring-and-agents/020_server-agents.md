---
title: Server Agents
layout: docs
---

This document explains how to configure **Java agents** for Lucee servers started via LuCLI.

Java agents are JVM plugins used for:
- Remote debugging (e.g. JDWP / luceedebug)
- Metrics and monitoring (e.g. Prometheus JMX exporter)
- Profiling and instrumentation

LuCLI exposes a first-class `agents` section in `lucee.json` plus dedicated `server start` flags so you can:
- Declare named agents once per project
- Toggle them on/off at startup without editing `lucee.json`

---

## 1. Configuring agents in `lucee.json`

Agents are configured in the top-level `agents` object of your project `lucee.json`:

```json
{
  "name": "my-project",
  "version": "7.0.0.123",
  "port": 8080,
  "webroot": "./",
  "monitoring": {
    "enabled": true,
    "jmx": { "port": 8999 }
  },
  "jvm": {
    "maxMemory": "1024m",
    "minMemory": "256m"
  },
  "agents": {
    "luceedebug": {
      "enabled": false,
      "jvmArgs": [
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999",
        "-javaagent:${LUCLI_HOME}/dependencies/luceedebug.jar=jdwpHost=localhost,jdwpPort=9999,debugHost=0.0.0.0,debugPort=10000,jarPath=${LUCLI_HOME}/dependencies/luceedebug.jar"
      ],
      "description": "Lucee step debugger agent"
    },
    "prometheus": {
      "enabled": false,
      "jvmArgs": [
        "-javaagent:${LUCLI_HOME}/dependencies/jmx_prometheus_javaagent-1.0.1.jar=9404:${LUCLI_HOME}/prometheus/jmx-exporter.yaml"
      ],
      "description": "Prometheus JMX exporter agent"
    }
  }
}
```

### 1.1 Schema

- `agents` (object, optional)
  - Keys are **agent IDs** (e.g. `"luceedebug"`, `"prometheus"`).
  - Values are **AgentConfig** objects:
    - `enabled` (boolean, default `false`)
      - Whether the agent is active by default when you run `lucli server start` with no agent flags.
    - `jvmArgs` (string array, default empty)
      - Exact JVM argument tokens to append to `CATALINA_OPTS` when this agent is active.
      - Typical entries include `-javaagent:...` or `-agentlib:jdwp=...`.
      - Tokens are not parsed or modified by LuCLI.
    - `description` (string, optional)
      - Free-form description for documentation and future tooling.

### 1.2 Environment placeholders

You can reference environment-related placeholders inside `jvmArgs`, for example:

- `${LUCLI_HOME}` – LuCLI home directory (e.g. `~/.lucli`)
- Standard shell-style paths (`/opt/agents/...`, relative paths, etc.)

LuCLI does not resolve system environment variables inside `jvmArgs` itself; any `${...}` tokens are passed to the JVM as-is. Use them only where the JVM or your agent understands them.

---

## 2. Startup flags for agents

Agents can be toggled at startup using `lucli server start` flags. These **do not** modify `lucee.json`; they apply only for the current run.

### 2.1 Flags

- `--agents <list>`
  - Comma-separated list of agent IDs to enable **for this start only**.
  - When provided, it defines the **exact** set of active agents.
  - The `enabled` flags in `lucee.json` are ignored for this run.
  - Example:
    - `lucli server start --agents luceedebug,prometheus`

- `--enable-agent <id>` (repeatable)
  - Turn on a specific agent for this start only, in addition to any agents already enabled in `lucee.json`.
  - Example:
    - `lucli server start --enable-agent luceedebug`

- `--disable-agent <id>` (repeatable)
  - Turn off a specific agent for this start only, even if it is `enabled` in `lucee.json`.
  - Example:
    - `lucli server start --disable-agent prometheus`

- `--no-agents`
  - Disable **all** agents for this start only.
  - Overrides `enabled` flags in `lucee.json`.

### 2.2 Activation rules

Given a `lucee.json` with an `agents` block:

1. **If `--agents` is provided**
   - Active agents = intersection of the provided list with keys under `agents`.
   - `enabled` flags are ignored.

2. **Else (no `--agents`)**
   - Start from all agents where `enabled == true` in `lucee.json`.
   - If `--no-agents` is present, the active set becomes empty.
   - Apply `--enable-agent <id>`: add those IDs to the active set if they exist in `agents`.
   - Apply `--disable-agent <id>`: remove those IDs from the active set.

3. The computed active set controls which agents' `jvmArgs` are appended to `CATALINA_OPTS`.

---

## 3. Examples

### 3.1 Luceedebug step debugger

`lucee.json`:

```json
{
  "agents": {
    "luceedebug": {
      "enabled": false,
      "jvmArgs": [
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:9999",
        "-javaagent:${LUCLI_HOME}/dependencies/luceedebug.jar=jdwpHost=localhost,jdwpPort=9999,debugHost=0.0.0.0,debugPort=10000,jarPath=${LUCLI_HOME}/dependencies/luceedebug.jar"
      ],
      "description": "Lucee step debugger agent"
    }
  }
}
```

Start with debugger **off by default**, but enable for a single run:

```bash
lucli server start --enable-agent luceedebug
```

Start with **only** luceedebug enabled (ignoring any other default agents):

```bash
lucli server start --agents luceedebug
```

Temporarily disable **all** agents, even if some are enabled in `lucee.json`:

```bash
lucli server start --no-agents
```

### 3.2 Prometheus JMX exporter

`lucee.json` (excerpt):

```json
{
  "monitoring": {
    "enabled": true,
    "jmx": { "port": 8999 }
  },
  "agents": {
    "prometheus": {
      "enabled": true,
      "jvmArgs": [
        "-javaagent:${LUCLI_HOME}/dependencies/jmx_prometheus_javaagent-1.0.1.jar=9404:${LUCLI_HOME}/prometheus/jmx-exporter.yaml"
      ],
      "description": "Prometheus JMX exporter agent"
    }
  }
}
```

- JMX must be enabled (`monitoring.enabled=true`) so Tomcat exposes JMX endpoints.
- The Prometheus agent will listen on port `9404` and use the supplied YAML config.

Start with Prometheus exporter on by default (because `enabled: true`):

```bash
lucli server start
```

Turn it off for a single run:

```bash
lucli server start --disable-agent prometheus
```

---

## 4. Interaction with `jvm.additionalArgs`

The JVM options for a server are built in this order:

1. Memory settings (`-Xms`, `-Xmx`) from `jvm.minMemory` / `jvm.maxMemory`.
2. JMX system properties (when `monitoring.enabled` is true).
3. `jvmArgs` from each **active agent** (per the rules above).
4. Any `jvm.additionalArgs` from `lucee.json`.

This means you can still use `jvm.additionalArgs` for advanced tuning, and it will be applied **after** agent arguments.

---

## 5. Troubleshooting

- If the server fails to start after enabling an agent:
  - Check the server logs: `lucli server log --type tomcat`.
  - Verify that all JAR paths and config files in `jvmArgs` are correct and readable.
- If metrics or debugging ports are not reachable:
  - Confirm the ports are open and not blocked by a firewall.
  - Check for typos in ports/hosts in your `jvmArgs`.
- To quickly rule out agents as the cause of an issue:
  - Start with: `lucli server start --no-agents`.
