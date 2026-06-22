---
title: Running Scripts and Components
layout: docs
---

▪  One-shot execution of .cfs and .cfm
▪  Examples per file type.
# Executing Commands and Scripts with LuCLI

Welcome! This guide shows you how to run one-shot commands and scripts directly from the command line.

## Overview

LuCLI gives you multiple ways to execute code and commands straight from the command line:

1. **CFML Scripts** - Execute `.cfm` and `.cfs` files
2. **Module Commands** - Execute component-based commands through `Module.cfc` entrypoints
3. **LuCLI Command Scripts** - Execute `.lucli` / `.luc` automation scripts (see dedicated page)

All of these execution modes support helpful global flags:
- `--verbose` / `-v` - See what's happening behind the scenes
- `--debug` / `-d` - Get detailed debug output when things go wrong
- `--timing` / `-t` - See exactly where time is being spent

## 1. Executing CFML Scripts

One of the simplest ways to use LuCLI is running CFML scripts directly - just point it at your file and go!

### Supported File Types

- **`.cfs`** - CFML script files (fully supported with ARGS array)
- **`.cfm`** - CFML template files  
- **`.cfml`** - CFML files (alternative extension)

### Basic Syntax

```bash
lucli <script-file> [arguments...]
```

### Examples

#### Simple script execution
```bash
lucli hello.cfs
```

#### With arguments
```bash
lucli process-data.cfs input.txt output.txt
```

#### With flags and arguments
```bash
lucli --verbose script.cfs arg1 arg2
lucli --debug --timing process.cfm "argument with spaces"
```

### Accessing Arguments in Scripts

#### For .cfs files

Good news - LuCLI automatically sets up an `ARGS` array for you with:
- `ARGS[1]` - The script filename
- `ARGS[2]` - First argument (if provided)
- `ARGS[3]` - Second argument (if provided)
- etc.

Example script (`hello.cfs`):
```cfml
writeOutput("Hello from CFML script!" & chr(10));

if (structKeyExists(variables, "ARGS") && isArray(ARGS)) {
    writeOutput("Arguments passed: " & arrayLen(ARGS) & chr(10));
    for (i = 1; i <= arrayLen(ARGS); i++) {
        writeOutput("  Arg " & i & ": " & ARGS[i] & chr(10));
    }
}
```

Run it:
```bash
lucli hello.cfs foo bar baz
```

Output:
```
Hello from CFML script!
Arguments passed: 4
  Arg 1: hello.cfs
  Arg 2: foo
  Arg 3: bar
  Arg 4: baz
```

#### For .cfm files

Arguments are available through the built-in `__arguments` variable:

```cfml
<cfif structKeyExists(variables, "__arguments") and isArray(__arguments)>
    <cfloop from="1" to="#arrayLen(__arguments)#" index="i">
        <cfoutput>Arg #i#: #__arguments[i]#<br></cfoutput>
    </cfloop>
</cfif>
```

### Built-in Variables

LuCLI provides several handy variables automatically in your scripts:

- `__cwd` - Current working directory
- `__scriptFile` - Script filename
- `__scriptPath` - Absolute path to script
- `__scriptDir` - Directory containing the script
- `__arguments` - Array of arguments passed to script
- `__argumentCount` - Number of arguments
- `__env` - System environment variables
- `__systemProps` - Java system properties
- `__lucliHome` - LuCLI home directory path

Example usage:
```cfml
writeOutput("Script: " & __scriptFile & chr(10));
writeOutput("Location: " & __scriptDir & chr(10));
writeOutput("Working Dir: " & __cwd & chr(10));
writeOutput("Arguments: " & __argumentCount & chr(10));
```

## 2. Using Modules for Component-Based Commands

Direct `.cfc` execution is intentionally not supported in one-shot mode (`lucli run file.cfc` or `lucli file.cfc`).

Use module entrypoints backed by `Module.cfc` instead:

```bash
lucli modules run my-module arg1 arg2
# or shortcut form
lucli my-module arg1 arg2
```

## 3. LuCLI Command Scripts (`.lucli` / `.luc`)

LuCLI command scripts now have a dedicated page, including environment-aware execution (`--env`, `LUCLI_ENV`, `#@env` blocks), `source`, and `--envfile`.

- [LuCLI Command Scripts (.lucli/.luc)](../lucli-command-scripts/)

## Command Precedence

When you run `lucli something`, LuCLI determines what to execute using this precedence:

1. **Known subcommands** - `server`, `modules`, `terminal`, `cfml`, `help`
2. **LuCLI command scripts** - Existing `.lucli` / `.luc` files  
3. **CFML files** - Existing `.cfs` or `.cfm` files (`.cfc` is blocked with guidance to use modules)
4. **Module shortcuts** - Modules in `~/.lucli/modules/`
5. **Error** - Show help if nothing matches

### Example Decision Flow

```bash
lucli server       # → Recognized subcommand, shows server help
lucli hello.cfs    # → Existing file, executes as CFML script
lucli deploy.lucli # → Existing file, executes as a LuCLI command script
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
Error: Unsupported script file extension for 'document.pdf'. Supported extensions are .cfm, .cfs, and .lucli
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

Here's something to keep in mind: the Lucee engine needs to start up each time you run a command (takes about 1-2 seconds). Once it's running, everything else is fast!

To get the best performance:
- **Batch operations** - Use LuCLI command scripts (`.lucli`) to batch multiple commands
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

## Best Practices

Here are some tips we've learned from building and using LuCLI:

### 1. Use appropriate file types
- **`.cfs`** for pure CFML script (best choice for command-line tools)
- **`.cfm`** for templates with mixed CFML/HTML
- **`Module.cfc`** in LuCLI modules for reusable component-based commands

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

### 6. Use LuCLI command scripts for automation
Multi-step workflows? LuCLI command scripts (`.lucli`) are your friend:
```bash
# build.lucli
cfformat *.cfs
run-tests
package-app
deploy-to-staging
```

LuCLI command scripts also support output redirection for command/module output:

```bash
# overwrite file
my-module generate > ./out/report.txt

# append to file
my-module stats >> ./out/report.txt
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

## Additional Resources

- **Module Development** - See module documentation for creating your own modules
- **Help System** - Use `lucli --help` and `lucli <command> --help` for built-in help
- **Examples** - Check `~/.lucli/modules/` for module examples after running `lucli modules list`
- **Server Mode** - See server documentation for web application development

## Summary Quick Reference

```bash
# CFML Scripts
lucli script.cfs arg1 arg2
lucli template.cfm param1 param2
# Module-backed component commands
# (direct .cfc execution is not supported)
lucli modules run module-name args...
lucli module-name args...

# LuCLI Command Scripts
lucli script.lucli
chmod +x script.lucli && ./script.lucli

# With Global Flags
lucli --verbose --debug --timing script.cfs
```

