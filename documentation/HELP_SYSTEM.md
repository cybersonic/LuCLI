# LuCLI Help System Documentation

This document describes the comprehensive help system built into LuCLI, covering all commands, subcommands, modules, and interactive help features.

## Table of Contents

- [Overview](#overview)
- [Help Command Patterns](#help-command-patterns)
- [Root Level Help](#root-level-help)
- [Command-Specific Help](#command-specific-help)
- [Module System Help](#module-system-help)
- [Interactive Terminal Help](#interactive-terminal-help)
- [Help for Shortcuts](#help-for-shortcuts)
- [Error Messages and Suggestions](#error-messages-and-suggestions)
- [Examples and Use Cases](#examples-and-use-cases)

## Overview

LuCLI features a hierarchical help system built on top of [Picocli](https://picocli.info/), providing context-sensitive help at every level of the application. The help system supports:

- **Root-level help**: General application overview
- **Command help**: Specific command documentation
- **Subcommand help**: Detailed options for specific operations
- **Module help**: Information about available modules
- **Interactive help**: Terminal-mode assistance
- **Error-driven help**: Suggestions when commands fail

## Help Command Patterns

### Basic Help Invocation

All help commands follow consistent patterns:

```bash
# Root application help
lucli --help
lucli -h
lucli help

# Command-specific help
lucli <command> --help
lucli help <command>

# Subcommand-specific help  
lucli <command> <subcommand> --help
lucli help <command> <subcommand>
```

### Exit Codes

Help commands may exit with codes 0, 1, or 2, all of which are considered successful help display:

- **Exit 0**: Help displayed successfully
- **Exit 1**: Help displayed for subcommands (Picocli convention)
- **Exit 2**: Help displayed for root command (Picocli convention)

## Root Level Help

### Command: `lucli --help`

Displays the main application help with:

- **Usage syntax**: Shows available global options
- **Application description**: Brief overview of LuCLI
- **Global options**: Debug, verbose, timing, version flags
- **Available commands**: List of all top-level commands
- **Configuration**: Information about LuCLI directories and environment variables
- **Examples**: Common usage patterns

**Output:**
```
Usage: lucli [-dtv] [--lucee-version] [--version] [COMMAND]
🚀 LuCLI - A terminal application with Lucee CFML integration
  -d, --debug           Enable debug output
      --lucee-version   Show Lucee version
  -t, --timing          Enable timing output for performance analysis
  -v, --verbose         Enable verbose output
      --version         Show application version
Commands:
  server   Manage Lucee server instances
  modules  Manage LuCLI modules
  cfml     Execute CFML expressions or code
  help     Display help information about the specified command.

Configuration:
  Lucee server files are stored in ~/.lucli/lucee-server by default
  Override with LUCLI_HOME environment variable or -Dlucli.home system property

Examples:
  lucli                           # Start interactive terminal
  lucli --verbose --version       # Show version with verbose output
  lucli --timing script.cfs       # Execute script with timing analysis
  lucli script.cfs arg1 arg2      # Execute CFML script with arguments
  lucli cfml 'now()'              # Execute CFML expression
  lucli server start --version 6.2.2.91  # Start server with specific Lucee version
  lucli modules list              # List available modules
```

### Alternative Forms

- `lucli -h`: Short form help
- `lucli help`: Explicit help command
- `lucli`: No arguments (starts interactive terminal with help available)

## Command-Specific Help

### Server Command Help

#### `lucli server --help`

Shows server management overview:

```
Usage: lucli server [COMMAND]
Manage Lucee server instances
Commands:
  start    Start a Lucee server instance
  stop     Stop a Lucee server instance
  status   Show status of server instances
  list     List all server instances
  log      View server logs
  monitor  Monitor server performance via JMX
```

#### Server Subcommand Help

**`lucli server start --help`**:
```
Usage: lucli server start [-f] [-n=<name>] [-p=<port>] [-v=<version>] [[PROJECT_DIR]]
Start a Lucee server instance
      [[PROJECT_DIR]]       Project directory (defaults to current directory)
  -f, --force               Force replace existing server with same name
  -n, --name=<name>         Custom name for the server instance
  -p, --port=<port>         Port number for the server (e.g., 8080)
  -v, --version=<version>   Lucee version to use (e.g., 6.2.2.91)
```

**Other server subcommands** (`stop`, `status`, `list`, `log`, `monitor`) each have their own detailed help with specific options and parameters.

### CFML Command Help

#### `lucli cfml --help`

Shows CFML expression execution help:

```
Usage: lucli cfml [-dv] EXPRESSION...
Execute CFML expressions or code
      EXPRESSION...   CFML expression or code to execute
  -d, --debug         Enable debug output
  -v, --verbose       Enable verbose output

Examples:
  lucli cfml 'now()'                    # Execute CFML expression
  lucli cfml 'writeDump(server.lucee)'  # Dump server information
  lucli cfml '1 + 2'                    # Simple expression
  lucli cfml 'arrayNew(1)'              # Create array
```

## Module System Help

### Module Overview

#### `lucli modules --help`

Shows module management commands:

```
Usage: lucli modules [COMMAND]
Manage LuCLI modules
Commands:
  list  List available modules
  init  Initialize a new module
  run   Run a module
```

### Module Subcommand Help

#### `lucli modules run --help`

Shows module execution help:

```
Usage: lucli modules run MODULE_NAME [ARGS...]
Run a module
      MODULE_NAME   Name of the module to run
      [ARGS...]     Arguments to pass to the module
```

### Module Shortcut Help

LuCLI supports module shortcuts where you can run a module directly:

```bash
# These are equivalent:
lucli modules run my-module arg1 arg2
lucli my-module arg1 arg2  # Shortcut form
```

When using shortcuts, if the module doesn't exist or has issues, LuCLI will provide helpful error messages and suggest using the full `modules run` syntax.

## Interactive Terminal Help

When you start LuCLI in interactive terminal mode:

```bash
lucli          # Starts interactive terminal
lucli terminal # Explicit terminal mode
```

The terminal provides its own help system:

- **`help`**: Shows available terminal commands
- **`help <command>`**: Shows help for specific terminal commands
- **Auto-completion**: Tab completion for commands and file paths
- **Command history**: Access to previous commands
- **Context-sensitive help**: Help adapted to current working directory

## Help for Shortcuts

### CFML File Shortcuts

LuCLI can execute CFML files directly:

```bash
lucli script.cfs arg1 arg2  # Direct execution
```

If there are issues with file execution, LuCLI provides helpful error messages explaining:

- File not found errors
- Permission issues
- CFML syntax errors
- Available alternatives

### Module Shortcuts

When using module shortcuts:

```bash
lucli unknown-module  # If module doesn't exist
```

LuCLI will:

1. Attempt to run as a module
2. Provide error message if module not found
3. Suggest using `lucli modules list` to see available modules
4. Suggest using `lucli modules run <name>` syntax

## Error Messages and Suggestions

### Command Not Found

When an invalid command is used:

```bash
lucli invalid-command
```

LuCLI provides:

- Clear error message
- List of available commands
- Suggestion to use `--help` for more information
- Context about what was attempted

### Option Errors

For invalid options:

```bash
lucli server start --invalid-option
```

LuCLI shows:

- Specific error about the invalid option
- List of valid options for the command
- Suggestion to use `--help` for complete option list

### Missing Required Arguments

When required arguments are missing:

```bash
lucli modules run  # Missing MODULE_NAME
```

LuCLI displays:

- Clear error about missing argument
- Correct usage syntax
- Examples of proper usage

## Examples and Use Cases

### Discovery Workflow

For new users discovering LuCLI:

1. **Start with root help**: `lucli --help`
2. **Explore commands**: `lucli server --help`
3. **Learn subcommands**: `lucli server start --help`
4. **Try interactive mode**: `lucli` (then `help`)

### Development Workflow

For developers using specific features:

1. **Quick reference**: `lucli cfml --help`
2. **Module development**: `lucli modules init --help`
3. **Server management**: `lucli server start --help`
4. **Debug assistance**: `lucli --debug cfml 'expression'`

### Administration Workflow

For system administrators:

1. **Server management**: `lucli server --help`
2. **Monitoring**: `lucli server monitor --help`
3. **Log analysis**: `lucli server log --help`
4. **Configuration**: Check `~/.lucli/` directory setup

## Implementation Details

### Architecture

The help system is built using:

- **Picocli Framework**: Provides annotation-based command definition
- **Hierarchical Commands**: Nested command structure with `@Command` annotations
- **Custom Exception Handling**: Smart error messages and help suggestions
- **Template-Based Output**: Consistent formatting across all help commands

### Code Organization

- **Root Command**: `LuCLICommand.java` - Main command with global options
- **Server Commands**: `ServerCommand.java` - Server management subcommands  
- **Module Commands**: `ModulesCommand.java` - Module management subcommands
- **CFML Command**: `CfmlCommand.java` - CFML expression execution
- **Exception Handling**: `LuCLI.java` - Smart command parsing and error handling

### Consistency Features

- **Uniform Syntax**: All commands follow `lucli <command> <subcommand> [options]`
- **Standard Options**: Common flags (`-v`, `-d`, `--help`) work consistently
- **Predictable Output**: Similar formatting and information structure
- **Cross-Platform**: Identical behavior on Linux, macOS, and Windows

## Best Practices for Users

### Getting Help Efficiently

1. **Start broad, get specific**: Begin with `lucli --help`, then drill down
2. **Use tab completion**: In terminal mode, tab completes commands and paths
3. **Check examples**: Most help includes practical usage examples
4. **Use verbose mode**: Add `-v` or `--verbose` for more detailed output
5. **Read error messages**: They often contain helpful suggestions

### Common Help Patterns

```bash
# Quick command discovery
lucli --help

# Learn about server management
lucli server --help

# Get specific option details
lucli server start --help

# Explore modules
lucli modules --help
lucli modules list

# CFML expression help
lucli cfml --help

# Interactive exploration
lucli  # Then use 'help' command
```

The LuCLI help system is designed to be discoverable, consistent, and helpful at every level of interaction, making it easy for both new users and experienced developers to quickly find the information they need.