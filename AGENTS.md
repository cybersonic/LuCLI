# AGENTS.md

This file provides guidance to Cursor when working with code in this repository. (See also [WARP.md](WARP.md) for Warp-specific guidance.)

## Project Overview

LuCLI is a Command Line Interface for Lucee CFML, providing both interactive terminal mode and one-shot command execution. It integrates the Lucee CFML engine into a modern terminal interface with features like server management, JMX monitoring, module management, and CFML script execution.

## Command Format

Use this pattern for commands:

```
lucli <action> <subcommand> [options] [parameters]
```

Examples: `lucli server start --version 6.2.2.91`, `lucli server log --type server --follow`, `lucli modules list`.

## Running and Building

- **Quick dev run (no JAR build):** `./dev-lucli.sh [args]` — runs LuCLI via `mvn exec:java` and passes all arguments through. Use for fast iteration (e.g. `./dev-lucli.sh server start`, `./dev-lucli.sh --version`).
- **Full build (JAR + binary):** `./build.sh` — uses SDKMAN/.sdkmanrc if present, runs `mvn clean package` (tests skipped for speed), then creates the self-executing `target/lucli` and verifies JAR and binary. Optional: `./build.sh install` to copy `target/lucli` to `~/bin/lucli`, `./build.sh docker` to build and push the multi-platform Docker image, `./build.sh dockerlocal` for a local-only Docker image, `./build.sh clear` to purge Maven dependencies.
- **Build (Maven only):** `mvn clean package` (binary profile: `mvn clean package -Pbinary`).
- **Run:** `java -jar target/lucli.jar` or `./target/lucli` after build. Prefer the JAR for one-off runs: `java -jar target/lucli.jar server start`.
- **Tests:** `./tests/test.sh`, `./tests/test-simple.sh`

## Key Directories and Components

- **Server management:** `org.lucee.lucli.server` — `LuceeServerManager`, `LuceeServerConfig`, `TomcatConfigGenerator`, `TomcatServerXmlPatcher`
- **Templates:** `src/main/resources/tomcat_template/`, `src/main/resources/script_engine/`
- **Config:** Project config is `lucee.json`; LuCLI home is `~/.lucli/` (or `LUCLI_HOME`)

## Conventions

- Java 17+. Maven for build.
- Use `demo_servers/` for test server instances.
- Environment overrides: `lucli server start --env=prod` (see WARP.md for config load order and deep merge).

## Configuration (lucee.json)

Supports `name`, `version`, `host`, `port`, `https` (e.g. `enabled`, `port`, `redirect`), `monitoring`, `jvm`, `urlRewrite`, `admin`, `agents`, `configurationFile`, `configuration`, `dependencies`, `environments`, etc. See WARP.md for full schema and examples.
