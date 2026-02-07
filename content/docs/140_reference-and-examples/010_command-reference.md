---
title: Command Reference
layout: docs
---

Auto-generated reference for all LuCLI commands and their options.

## Global Options

These options work with any command:

| Option | Short | Description |
|--------|-------|-------------|
| `--verbose` | `-v` | Enable verbose output |
| `--debug` | `-d` | Enable debug output |
| `--timing` | `-t` | Enable timing output for performance analysis |
| `--help` | `-h` | Show this help message and exit |
| `--version` |  | Show application version |
| `--lucee-version` |  | Show Lucee version |

## Commands

### `lucli server`

Manage Lucee server instances

**Usage:**
```bash
lucli server [OPTIONS] [COMMAND]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-h, --help` | Show this help message and exit. |
| `-V, --version` | Print version information and exit. |

**Subcommands:**

- **`start`** - Start a Lucee server instance
- **`start`** - Start a Lucee server instance
- **`run`** - Run a Lucee server in foreground (Ctrl+C to stop)
- **`stop`** - Stop a Lucee server instance
- **`restart`** - Show status of server instances
- **`status`** - Show status of server instances
- **`list`** - List all server instances
- **`prune`** - Remove stopped server instances
- **`get`** - Get configuration values from lucee.json
- **`set`** - Set configuration values in lucee.json
- **`log`** - View server logs
- **`monitor`** - Monitor server performance via JMX

#### `lucli start`

Start a Lucee server instance

**Usage:**
```bash
lucli start [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-v, --version` | Lucee version to use (e.g., 6.2.2.91) |
| `-n, --name` | Custom name for the server instance |
| `-p, --port` | Port number for the server (e.g., 8080) |
| `-f, --force` | Force replace existing server with same name |
| `-c, --config` | Configuration file to use (defaults to lucee.json) |
| `--env, --environment` | Environment to use (e.g., prod, dev, staging) |
| `--dry-run` | Show configuration without starting the server |
| `--include-lucee` | Include Lucee CFConfig in dry-run output |
| `--include-tomcat-web` | Include Tomcat web.xml in dry-run output |
| `--include-tomcat-server` | Include Tomcat server.xml in dry-run output |
| `--include-https-keystore-plan` | Include HTTPS keystore plan in dry-run output |
| `--include-https-redirect-rules` | Include HTTPS redirect rules in dry-run output |
| `--include-all` | Include all available dry-run previews |
| `--no-agents` | Disable all Java agents |
| `--agents` | Comma-separated list of agent IDs to include |
| `--enable-agent` | Enable a specific agent by ID (repeatable) |
| `--disable-agent` | Disable a specific agent by ID (repeatable) |
| `--open-browser` | Open browser after server starts (default: true) |
| `--disable-open-browser` | Disable automatic browser opening |

---

#### `lucli start`

Start a Lucee server instance

**Usage:**
```bash
lucli start [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-v, --version` | Lucee version to use (e.g., 6.2.2.91) |
| `-n, --name` | Custom name for the server instance |
| `-p, --port` | Port number for the server (e.g., 8080) |
| `-f, --force` | Force replace existing server with same name |
| `-c, --config` | Configuration file to use (defaults to lucee.json) |
| `--env, --environment` | Environment to use (e.g., prod, dev, staging) |
| `--dry-run` | Show configuration without starting the server |
| `--include-lucee` | Include Lucee CFConfig in dry-run output |
| `--include-tomcat-web` | Include Tomcat web.xml in dry-run output |
| `--include-tomcat-server` | Include Tomcat server.xml in dry-run output |
| `--include-https-keystore-plan` | Include HTTPS keystore plan in dry-run output |
| `--include-https-redirect-rules` | Include HTTPS redirect rules in dry-run output |
| `--include-all` | Include all available dry-run previews |
| `--no-agents` | Disable all Java agents |
| `--agents` | Comma-separated list of agent IDs to include |
| `--enable-agent` | Enable a specific agent by ID (repeatable) |
| `--disable-agent` | Disable a specific agent by ID (repeatable) |
| `--open-browser` | Open browser after server starts (default: true) |
| `--disable-open-browser` | Disable automatic browser opening |

---

#### `lucli run`

Run a Lucee server in foreground (Ctrl+C to stop)

**Usage:**
```bash
lucli run [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-v, --version` | Lucee version to use (e.g., 6.2.2.91) |
| `-n, --name` | Custom name for the server instance |
| `-p, --port` | Port number for the server (e.g., 8080) |
| `-f, --force` | Force replace existing server with same name |
| `-c, --config` | Configuration file to use (defaults to lucee.json) |
| `--env, --environment` | Environment to use (e.g., prod, dev, staging) |
| `--no-agents` | Disable all Java agents |
| `--agents` | Comma-separated list of agent IDs to include |
| `--enable-agent` | Enable a specific agent by ID (repeatable) |
| `--disable-agent` | Disable a specific agent by ID (repeatable) |

---

#### `lucli stop`

Stop a Lucee server instance

**Usage:**
```bash
lucli stop [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-n, --name` | Name of the server instance to stop |
| `--all` | Stop all running servers |

---

#### `lucli restart`

Show status of server instances

**Usage:**
```bash
lucli restart [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-n, --name` | Name of the server instance to restart |

---

#### `lucli status`

Show status of server instances

**Usage:**
```bash
lucli status [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-n, --name` | Name of specific server to check |

---

#### `lucli list`

List all server instances

**Usage:**
```bash
lucli list [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-r, --running` | List only running server instances |

---

#### `lucli prune`

Remove stopped server instances

**Usage:**
```bash
lucli prune [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-a, --all` | Remove all stopped servers |
| `-n, --name` | Remove a specific stopped server by name |
| `-f, --force` | Skip confirmation prompt |

---

#### `lucli get`

Get configuration values from lucee.json

**Usage:**
```bash
lucli get [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-d, --directory` | Project directory (defaults to current directory) |

---

#### `lucli set`

Set configuration values in lucee.json

**Usage:**
```bash
lucli set [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--dry-run` | Show what would be set without actually saving |
| `-d, --directory` | Project directory (defaults to current directory) |

---

#### `lucli log`

View server logs

**Usage:**
```bash
lucli log [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-n, --name` | Name of server instance |
| `-f, --follow` | Follow log output (tail -f) |
| `-t, --type` | Log type (server, access, error) |

---

#### `lucli monitor`

Monitor server performance via JMX

**Usage:**
```bash
lucli monitor [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-n, --name` | Name of server instance to monitor |
| `-h, --host` | JMX host (default: localhost) |
| `-p, --port` | JMX port (default: 8999) |
| `-r, --refresh` | Refresh interval in seconds (default: 3) |
| `--help` | Show help for monitor command |

---

---

### `lucli modules`

Manage LuCLI modules

**Usage:**
```bash
lucli modules [COMMAND]
```

**Subcommands:**

- **`list`** - List available modules
- **`init`** - Initialize a new module
- **`run`** - Run a module
- **`install`** - Install a module
- **`uninstall`** - Uninstall (remove) a module
- **`update`** - Update a module from git

#### `lucli list`

List available modules

**Usage:**
```bash
lucli list
```

---

#### `lucli init`

Initialize a new module

**Usage:**
```bash
lucli init [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--git` | Initialize a git repository in the new module directory |
| `--no-git` | Do not initialize git and do not prompt |

---

#### `lucli run`

Run a module

**Usage:**
```bash
lucli run
```

---

#### `lucli install`

Install a module

**Usage:**
```bash
lucli install [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-u, --url` | Git URL to install from (e.g. https://github.com/user/repo.git[#ref]] |
| `-f, --force` | Overwrite existing module if it already exists |

---

#### `lucli uninstall`

Uninstall (remove) a module

**Usage:**
```bash
lucli uninstall
```

---

#### `lucli update`

Update a module from git

**Usage:**
```bash
lucli update [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-u, --url` | Git URL to update from (e.g. https://github.com/user/repo.git[#ref]] |
| `-f, --force` | Overwrite existing module if it already exists |

---

---

### `lucli modules`

Manage LuCLI modules

**Usage:**
```bash
lucli modules [COMMAND]
```

**Subcommands:**

- **`list`** - List available modules
- **`init`** - Initialize a new module
- **`run`** - Run a module
- **`install`** - Install a module
- **`uninstall`** - Uninstall (remove) a module
- **`update`** - Update a module from git

#### `lucli list`

List available modules

**Usage:**
```bash
lucli list
```

---

#### `lucli init`

Initialize a new module

**Usage:**
```bash
lucli init [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--git` | Initialize a git repository in the new module directory |
| `--no-git` | Do not initialize git and do not prompt |

---

#### `lucli run`

Run a module

**Usage:**
```bash
lucli run
```

---

#### `lucli install`

Install a module

**Usage:**
```bash
lucli install [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-u, --url` | Git URL to install from (e.g. https://github.com/user/repo.git[#ref]] |
| `-f, --force` | Overwrite existing module if it already exists |

---

#### `lucli uninstall`

Uninstall (remove) a module

**Usage:**
```bash
lucli uninstall
```

---

#### `lucli update`

Update a module from git

**Usage:**
```bash
lucli update [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-u, --url` | Git URL to update from (e.g. https://github.com/user/repo.git[#ref]] |
| `-f, --force` | Overwrite existing module if it already exists |

---

---

### `lucli deps`

Manage project dependencies

**Usage:**
```bash
lucli deps [COMMAND]
```

**Subcommands:**

- **`install`** - Install dependencies from lucee.json
- **`help`** - %nWhen no COMMAND is given, the usage help for the main command is displayed.

#### `lucli install`

Install dependencies from lucee.json

**Usage:**
```bash
lucli install [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--env` | Environment (prod, dev, staging) |
| `--production` | Install only production dependencies |
| `--force` | Force reinstall |
| `--dry-run` | Show what would be installed |

---

#### `lucli help`

%nWhen no COMMAND is given, the usage help for the main command is displayed.

**Usage:**
```bash
lucli help [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-h, --help` | Show usage help for the help command and exit. |

---

---

### `lucli deps`

Manage project dependencies

**Usage:**
```bash
lucli deps [COMMAND]
```

**Subcommands:**

- **`install`** - Install dependencies from lucee.json
- **`help`** - %nWhen no COMMAND is given, the usage help for the main command is displayed.

#### `lucli install`

Install dependencies from lucee.json

**Usage:**
```bash
lucli install [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--env` | Environment (prod, dev, staging) |
| `--production` | Install only production dependencies |
| `--force` | Force reinstall |
| `--dry-run` | Show what would be installed |

---

#### `lucli help`

%nWhen no COMMAND is given, the usage help for the main command is displayed.

**Usage:**
```bash
lucli help [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-h, --help` | Show usage help for the help command and exit. |

---

---

### `lucli install`

Install dependencies from lucee.json

**Usage:**
```bash
lucli install [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--env` | Environment (prod, dev, staging) |
| `--production` | Install only production dependencies |
| `--force` | Force reinstall |
| `--dry-run` | Show what would be installed |

---

### `lucli cfml`

Execute CFML expressions or code

**Usage:**
```bash
lucli cfml [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `-v, --verbose` | Enable verbose output |
| `-d, --debug` | Enable debug output |
| `-t, --timing` | Enable timing output |

---

### `lucli completion`

Generate shell completion scripts for bash or zsh

**Usage:**
```bash
lucli completion [COMMAND]
```

**Subcommands:**

- **`bash`** - Generate bash completion script
- **`zsh`** - Generate zsh completion script
- **`md`** - Generate markdown documentation from command structure

#### `lucli bash`

Generate bash completion script

**Usage:**
```bash
lucli bash
```

---

#### `lucli zsh`

Generate zsh completion script

**Usage:**
```bash
lucli zsh
```

---

#### `lucli md`

Generate markdown documentation from command structure

**Usage:**
```bash
lucli md
```

---

---

### `lucli versions-list`

List available Lucee versions (for shell completion use)

**Usage:**
```bash
lucli versions-list [OPTIONS]
```

**Options:**

| Option | Description |
|--------|-------------|
| `--no-cache` | Bypass cache and fetch fresh versions |

---

### `lucli parrot`

Repeat back the provided text (proof of concept command)

**Usage:**
```bash
lucli parrot [OPTIONS]
```

**Options:**

|| Option | Description |
||--------|-------------|
|| `-h, --help` | Show this help message and exit. |
|| `-V, --version` | Print version information and exit. |

---

### `lucli daemon`

Run LuCLI in daemon mode, either in simple JSON mode or as an LSP (Language Server Protocol) endpoint.

**Usage:**
```bash
lucli daemon [--port <port>] [--lsp] [--module <name>]
```

**Options:**

||| Option | Description |
|||--------|-------------|
||| `--port` | Port to listen on (default: `10000`, localhost only) |
||| `--lsp` | Run in Language Server Protocol (LSP) mode instead of JSON mode |
||| `--module <name>` | CFML module to use as the LSP endpoint (e.g. `LuceeLSP`) |

**JSON mode protocol:**

In the default JSON mode, the daemon listens on `127.0.0.1:<port>` and expects a single JSON line per TCP connection, for example:

```json
{"id":"1","argv":["modules","list"]}
```

Each request runs the same Picocli command pipeline as the normal CLI and returns a JSON response containing the original `id` (if provided), the exit code, and the combined stdout/stderr:

```json
{"id":"1","exitCode":0,"output":"..."}
```

**LSP mode:**

When `--lsp` is specified, the daemon instead speaks the standard Language Server Protocol over TCP using the configured CFML module as its endpoint. In this mode, requests and responses follow LSP framing (`Content-Length` headers + JSON-RPC 2.0 messages) and the module is responsible for implementing all LSP methods.

---

## Quick Reference

### Most Common Commands

```bash
# Start server
lucli server start

# Stop server
lucli server stop

# Execute script
lucli script.cfs

# Interactive mode
lucli

# Get help
lucli --help
lucli help server
```
