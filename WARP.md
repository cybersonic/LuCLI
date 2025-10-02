# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

LuCLI is a Command Line Interface for Lucee CFML, providing both interactive terminal mode and one-shot command execution. It integrates the Lucee CFML engine into a modern terminal interface with features like server management, JMX monitoring, module management, and CFML script execution.

## Command Format Guidelines

When implementing new commands, follow the consistent format pattern:

```
lucli <action> <subcommand> [options] [parameters]
```

Examples of this pattern:
- `lucli server start --version 6.2.2.91`
- `lucli server log --type server --follow`
- `lucli modules list`
- `lucli modules init my-new-module`

This maintains consistency across all LuCLI commands and provides a predictable user experience.

## Development Commands

### Building and Testing

```bash
# Build the JAR (standard build)
mvn clean package

# Build the JAR with binary profile (creates self-executing binary)
mvn clean package -Pbinary

# Run comprehensive test suite
./test.sh

# Quick development cycle (build and run binary)
./dev-lucli.sh

# Run simple tests
./test/test-simple.sh
```

### Running the Application

```bash
# Using the JAR
java -jar target/lucli.jar                    # Interactive terminal mode
java -jar target/lucli.jar --version          # Show version
java -jar target/lucli.jar script.cfs         # Execute CFML script

# Using the self-executing binary (after mvn package -Pbinary)
./target/lucli                                # Interactive terminal mode
./target/lucli --version                      # Show version
./target/lucli server start                   # Start Lucee server
```

### Server Management

```bash
# Start a server for current directory
lucli server start

# Start with specific version
lucli server start --version 6.2.2.91

# Stop server
lucli server stop

# Monitor server via JMX
lucli server monitor

# View server logs
lucli server log
lucli server log --type server
lucli server log --follow

# List all servers
lucli server list
```

### Test Server Development

When creating test servers for development and testing purposes, always use the `demo_servers/` directory:

```bash
# Navigate to the demo_servers directory first
cd demo_servers/

# Create test servers here
lucli server start --name test-server-1
```

This helps keep test server instances organized and separate from production environments.

## Architecture Overview

### Core Components

- **LuCLI.java**: Main entry point handling command-line argument parsing and dispatching
- **InteractiveTerminal.java**: Interactive terminal mode with JLine integration
- **LuceeScriptEngine.java**: Lucee CFML engine integration wrapper with externalized script templates
- **StringOutput.java**: Centralized output post-processor with emoji handling and placeholder substitution
- **CommandProcessor.java**: Internal command processing (file operations, system commands)
- **ExternalCommandProcessor.java**: External command execution with enhanced features

### Server Management (org.lucee.lucli.server)

- **LuceeServerManager.java**: Core server lifecycle management
- **LuceeServerConfig.java**: Configuration parsing from lucee.json files
- **TomcatConfigGenerator.java**: Template-based Tomcat configuration generation
- **ServerConflictException.java**: Handles server name conflicts with suggestions

### Monitoring (org.lucee.lucli.monitoring)

- **JmxConnection.java**: JMX connectivity for server metrics
- **CliDashboard.java**: ASCII dashboard rendering with progress bars
- **MonitorCommand.java**: Interactive monitoring interface

### Terminal Features

- **PromptConfig.java**: Configurable prompt styles (14 different themes)
- **Settings.java**: User settings persistence
- **StringOutput.java**: Centralized output post-processing with emoji/placeholder support
- **WindowsSupport.java**: Smart emoji detection and terminal capability analysis
- **CfmlCompleter.java & LucliCompleter.java**: Command auto-completion
- **CfmlSyntaxHighlighter.java**: Syntax highlighting for CFML
- **FileSystemState.java**: Working directory tracking

## Key Design Patterns

### Command Consistency Architecture

Both CLI mode (`java -jar lucli.jar [command]`) and terminal mode (`lucli terminal`) support the same core commands:
- Version commands (`--version`, `--lucee-version`)
- Server management (`server start`, `server stop`, `server status`, `server list`, `server monitor`)
- Help system (`help`, `--help`)

### Template-Based Configuration

Server configurations use templates in `src/main/resources/tomcat_template/` which are dynamically populated with project-specific settings.

### Template-Based Script Processing

Lucee script generation has been externalized to template files in `src/main/resources/script_engine/`:
- **cfmlOutput.cfs**: CFML expression evaluation template
- **componentWrapper.cfs**: CFC component execution wrapper
- **moduleDirectExecution.cfs**: Direct module execution template
- **componentToScript.cfs**: Component-to-script conversion template
- **lucliMappings.cfs**: LuCLI component mapping setup
- **executeComponent.cfs**: General component execution template

These templates use placeholder substitution and are post-processed through StringOutput for consistent emoji and variable handling.

### Dual Execution Modes

1. **One-shot CLI**: `java -jar lucli.jar command args` - exits after command execution
2. **Interactive Terminal**: `java -jar lucli.jar` or `java -jar lucli.jar terminal` - persistent session

## Configuration

### Project Configuration (lucee.json)

Each project can have a `lucee.json` file defining server settings:
```json
{
  "name": "my-project",
  "version": "6.2.2.91",
  "port": 8080,
  "webroot": "./",
  "monitoring": {
    "enabled": true,
    "jmx": { "port": 8999 }
  },
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m"
  }
}
```

### Global Configuration

- LuCLI home: `~/.lucli/` (customizable via `LUCLI_HOME` env var or `-Dlucli.home`)
- Server instances: `~/.lucli/servers/`
- Lucee Express downloads: `~/.lucli/express/`
- Command history: `~/.lucli/history`
- Prompt templates: `~/.lucli/prompts/`

### Output Processing System

StringOutput provides centralized output handling with:
- **Emoji Processing**: Smart emoji handling based on terminal capabilities
- **Placeholder Substitution**: Dynamic variables like `${EMOJI_SUCCESS}`, `${NOW}`, `${LUCLI_VERSION}`
- **Environment Integration**: Access environment variables via `${ENV_PATH}` syntax
- **Terminal Detection**: Automatic fallback to text symbols on unsupported terminals
- **Custom Placeholders**: Extensible system for adding new placeholder types

## Testing Strategy

The test suite (`test.sh`) covers:
- Basic functionality (help, version commands)
- CFML script execution
- File system operations
- Server management commands
- JMX monitoring integration
- Command consistency between modes
- Binary executable functionality
- Configuration handling
- Error scenarios

## Java Requirements

- Java 17+ (specified in pom.xml)
- Maven 3.x for building
- Cross-platform support (Linux, macOS, Windows)

## Dependencies

Key dependencies managed via Maven:
- **JLine 3.26.3**: Terminal handling and command-line interface
- **Lucee 7.0.0.242-RC**: CFML engine integration
- **Jackson 2.15.2**: JSON configuration parsing
- **Jakarta Servlet API**: Required by Lucee engine

## Development Notes

### Binary Generation

The `-Pbinary` profile creates a self-executing shell script by concatenating:
1. Shell bootstrap script (`src/bin/lucli.sh`)
2. The JAR file (`target/lucli.jar`)

This enables `./lucli` execution without explicit Java commands.

### Monitoring Integration

JMX monitoring connects to Lucee servers on port 8999 (configurable) and provides real-time metrics:
- Memory usage (heap/non-heap)
- Thread counts and GC statistics
- System load and CPU metrics
- Lucee-specific performance data

### Version Management

Version bumping is automated via Maven profiles, incrementing patch versions and updating both JAR and binary artifacts.
