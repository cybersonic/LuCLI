---
title: Running Modules
layout: docs
---
▪  Concept of CFML “modules” in LuCLI
▪  How to execute them

### Module Components

Components in the `~/.lucli/modules/` directory are treated specially as modules and can be executed with simplified syntax (see Module Commands section below).

## 3. Module Commands

Modules are where LuCLI really shines! They're reusable commands you can run with either the full syntax or handy shortcuts.

### Full Syntax

```bash
lucli modules run <module-name> [arguments...]
```

### Shortcut Syntax

```bash
lucli <module-name> [arguments...]
```

The shortcut syntax automatically translates to `modules run <module-name>` behind the scenes.

### Examples

#### Full syntax
```bash
lucli modules run hello-world
lucli modules run cfformat file1.cfs file2.cfs
```

#### Shortcut syntax (equivalent)
```bash
lucli hello-world
lucli cfformat file1.cfs file2.cfs
```

#### With flags
```bash
lucli --verbose test-module arg1 arg2
lucli --debug midnight
```

### Module Arguments

Modules support both positional and named arguments:

#### Positional arguments
```bash
lucli my-module arg1 arg2 arg3
```

#### Named arguments (key=value pairs)
```bash
lucli my-module input=file.txt output=result.txt verbose=true
```

#### Subcommands
Modules can define subcommands - if the first argument doesn't contain `=`, it's treated as a subcommand:

```bash
lucli my-module subcommand arg1 arg2
lucli my-module process input=file.txt
```

### Module Management

```bash
# List available modules
lucli modules list

# Create a new module
lucli modules init my-awesome-module

# Get help on modules
lucli modules --help
```

### How Shortcuts Work

When you run an unrecognized command, LuCLI uses smart detection:

1. First, checks if it's a known subcommand (like `server`, `modules`, `terminal`)
2. Next, checks if it's an existing CFML file (`.cfs`, `.cfm`, `.cfc`)
3. Then tries to run it as a module shortcut
4. If nothing matches, shows you what's available

This means you can just type `lucli hello-world` instead of the longer `lucli modules run hello-world` - convenient!
