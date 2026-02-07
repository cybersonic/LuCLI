---
title: CLI Basics
layout: docs
---
LuCLI uses a consistent command-line shape across all features. This page introduces the basic patterns so you can read and write LuCLI commands comfortably.

## Command shape

Most commands follow this pattern:

```bash
lucli <group> <command> [options] [arguments]
```

Examples:

```bash
lucli server start
lucli server status
lucli secrets list
lucli deps install --dry-run
```

Some commands are top-level flags without a group:

```bash
lucli --help
lucli --version
```

## Global options

These options can usually appear before the subcommand and apply to the whole invocation:

- `--help` – show help for the current command or subcommand.
- `--version` – print LuCLI version information.
- Verbosity / diagnostics flags (depending on build):
  - `--verbose` / `-v` – more detailed output.
  - `--debug` / `-d` – debug-level information.
  - `--timing` / `-t` – show where time is spent.

Use them like this:

```bash
lucli --verbose server start
lucli --debug deps install
```

## Discovering commands

You don’t need to memorize everything. Use the built-in help:

```bash
lucli help
lucli server --help
lucli secrets --help
lucli server start --help
```

The help output shows:

- Available command groups (e.g. `server`, `secrets`, `deps`).
- Subcommands under each group.
- Options and arguments for each command.



## Making Scripts Executable

Here's a neat trick - you can make `.lucli` scripts run directly without typing `lucli` first:

#### Step 1: Add a shebang line

Add this as the first line of your script:
```bash
#!/usr/bin/env lucli
```

#### Step 2: Make the file executable

```bash
chmod +x script.lucli
```

#### Step 3: Run directly

```bash
./script.lucli
```

### Complete Example

Create a file called `deploy.lucli`:

```bash
#!/usr/bin/env lucli
# Deployment automation script
echo "Starting deployment process..."
lint folder=src/ format=json
# Start server
server start --name production --port 8080

server status
```

Make it executable and run:
```bash
chmod +x deploy.lucli
./deploy.lucli
```

Or run directly:
```bash
lucli deploy.lucli
```

### Script Features

- **Comments**: Use `#` for comments (just like shell scripts)
- **Command execution**: Each line runs as if you typed it yourself
- **Error handling**: The script keeps going even if one command fails
- **Sequential execution**: Commands run in order, top to bottom
- **No interactive prompts**: Scripts run non-interactively, so they won't pause for input

### Flags with LuCLI Scripts

You can pass global flags when executing `.lucli` scripts:

```bash
lucli --verbose script.lucli
lucli --debug --timing deploy.lucli
```

These flags apply to the script execution environment.

## Command Precedence

When you run `lucli something`, LuCLI determines what to execute using this precedence:

1. **Known subcommands** - `server`, `modules`, `terminal`, `cfml`, `help`
2. **LuCLI scripts** - Existing `.lucli` files  
3. **CFML files** - Existing `.cfs`, `.cfm`, or `.cfc` files
4. **Module shortcuts** - Modules in `~/.lucli/modules/`
5. **Error** - Show help if nothing matches

### Example Decision Flow

```bash
lucli server       # → Recognized subcommand, shows server help
lucli hello.cfs    # → Existing file, executes as CFML script
lucli deploy.lucli # → Existing file, executes as LuCLI batch script
lucli hello-world  # → No file found, tries module shortcut
lucli unknown      # → Nothing found, shows error and available options
```

## Error Handling

### File Not Found

```bash
$ lucli nonexistent.cfs
Error: File not found: nonexistent.cfs
```

### Invalid File Type

```bash
$ lucli document.pdf
Error: 'document.pdf' is not a CFML file (.cfm, .cfc, or .cfs)
```

### Module Not Found

```bash
$ lucli unknown-module
Error: Module 'unknown-module' not found

Available modules:
  - hello-world
  - cfformat
  - test-runner
```

### Script Execution Error

```bash
$ lucli broken.cfs
Error executing CFML script 'broken.cfs': Invalid CFML syntax at line 5
```

Use `--debug` flag to see detailed stack traces:
```bash
lucli --debug broken.cfs
```

## Performance Considerations

### Engine Initialization

Here's something to keep in mind: the Lucee engine needs to start up each time you run a command (takes about 0.8 seconds). Once it's running, everything else is fast!

To get the best performance:
- **Batch operations** - Use `.lucli` scripts to batch multiple commands
- **Module development** - Modules stay within one engine instance
- **Server mode** - For web applications, use `lucli server start` instead of one-shot execution
- **Timing analysis** - Use `--timing` flag to identify bottlenecks

### Timing Example

```bash
$ lucli --timing process.cfs data.txt

⏱️  Timing Results:
  Lucee Engine Initialization: 891ms
  Script Preparation: 12ms
  Script Execution: 145ms
  CFML File Execution: 1,048ms
```


### Adding timing in Modules

(We will go into more detail in the Modules documentation.)
When modules are instantiated we pass in a Timing object into the init method, which is then available for use throughout the module via `variables.timing`.

If there is some code you want to time, you can do something like this:
```cfm
variables.timing.start("my-custom-timer");
// ... your code here ...
variables.timing.stop("my-custom-timer");

```

This will now be shown in the overall timing report when the module command completes.

## Best Practices

Here are some tips we've learned from building and using LuCLI:

### 1. Use appropriate file types
- **`.cfs`** for pure CFML script (best choice for command-line tools)
- **`.cfm`** for templates with mixed CFML/HTML
- **`.cfc`** for reusable components

### 2. Handle arguments properly
It's always a good idea to check if arguments exist before using them:
```cfml
if (structKeyExists(variables, "ARGS") && isArray(ARGS) && arrayLen(ARGS) > 1) {
    inputFile = ARGS[2]; // First actual argument (ARGS[1] is script name)
} else {
    writeOutput("Usage: script.cfs <input-file>" & chr(10));
    // In a real script, you'd exit here
}
```

### 3. Provide usage information
Your future self (and others) will thank you for including helpful usage text:
```cfml
if (__argumentCount == 0) {
    writeOutput("Usage: process.cfs <input> <output>" & chr(10));
    writeOutput("  input  - Input file path" & chr(10));
    writeOutput("  output - Output file path" & chr(10));
    return;
}
```

### 4. Use built-in variables
Leverage LuCLI's built-in variables for file paths:
```cfml
// Use __scriptDir for relative file paths
dataFile = __scriptDir & "/data/input.json";

// Use __cwd for current working directory
outputFile = __cwd & "/results.txt";
```

### 5. Create modules for reusable code
Running the same script over and over? Time to make it a module!
```bash
lucli modules init my-utility
# Edit ~/.lucli/modules/my-utility/Module.cfc
lucli my-utility arg1 arg2
```

### 6. Use .lucli scripts for automation
Multi-step workflows? `.lucli` scripts are your friend:
```bash
# build.lucli
cfformat *.cfs
run-tests
package-app
deploy-to-staging
```

## Exit Codes

LuCLI follows standard Unix exit code conventions:

- **0** - Success! Everything worked
- **1** - General error (file not found, execution error, etc.)
- **2** - Invalid command or arguments

You can check these in your shell scripts:
```bash
if lucli process.cfs input.txt; then
    echo "Processing succeeded"
else
    echo "Processing failed"
fi
```

## Summary Quick Reference

```bash
# CFML Scripts
lucli script.cfs arg=hello bla=world
lucli script.cfs
lucli template.cfm param1 param2

# CFML Components  
lucli Component.cfc arg=hello bla=world

# Module Commands (full syntax)
lucli modules run module-name args...

# Module Shortcuts
lucli module-name args...

# LuCLI Batch Scripts
lucli script.lucli
chmod +x script.lucli && ./script.lucli

# With Global Flags
lucli --verbose --debug --timing script.cfs
```
