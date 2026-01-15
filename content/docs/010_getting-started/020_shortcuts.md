---
title: Shortcuts & Quick Commands
layout: docs
---

# LuCLI Command Shortcuts

LuCLI now supports convenient shortcuts for executing modules and CFML files without requiring the full command syntax.

## Module Shortcuts

Instead of writing the full command:
```bash
lucli modules run test-module arg1 arg2
```

You can now use the shortcut:
```bash
lucli test-module arg1 arg2
```

### How Module Shortcuts Work

1. When LuCLI encounters an unrecognized command, it first checks if the argument is a flag (starts with `-`)
2. If it's not a flag and the argument doesn't match an existing file, it attempts to execute it as a module
3. The shortcut automatically translates to `modules run <module-name> [args...]`
4. If the module doesn't exist, it shows a helpful list of available modules

### Examples

```bash
# Run a module with no arguments
lucli lint

# Run a module with arguments  
lucli lint file=file1.cfs

# Show verbose output while running module
lucli --verbose test-module arg1 arg2
```

## CFML File Shortcuts

Instead of requiring a separate command, you can now execute CFML files directly:

```bash
lucli script.cfs arg1 arg2 "arg with spaces"
```

### Supported File Types

- `.cfs` files (CFML script files) - **Fully supported with ARGS array**
- `.cfm` files (CFML template files) - Uses existing execution path
- `.cfc` files (CFML component files) - Uses existing execution path

### How CFML File Shortcuts Work

1. When LuCLI encounters an unrecognized command, it checks if the argument is an existing file
2. If the file exists and has a CFML extension (`.cfs`, `.cfm`, or `.cfc`), it executes the file
3. For `.cfs` files, an `ARGS` array is automatically set up with the filename and all arguments
4. Arguments are passed properly, including those with spaces when quoted

### ARGS Array (CFS files)

For `.cfs` files, the `ARGS` array is automatically created with:
- `ARGS[1]` = the script filename  
- `ARGS[2]` = first argument (if provided)
- `ARGS[3]` = second argument (if provided)
- etc.

### Examples

```bash
# Execute a simple script
lucli script.cfs

# Execute with arguments
lucli process-data.cfs input.txt output.txt

# Execute with verbose output
lucli --verbose script.cfs arg1 arg2

# Execute with debug information
lucli --debug script.cfs "argument with spaces"
```

## Error Handling

### Module Not Found
If a module doesn't exist, LuCLI shows:
- An error message
- A list of available modules
- Returns exit code 1

### File Not Found  
If neither a module nor a file matches the argument, LuCLI falls back to showing the standard help/usage information.

### Invalid File Type
Only files with CFML extensions (`.cfs`, `.cfm`, `.cfc`) can be executed via shortcuts. Other file types are ignored and fall back to help.

## Flag Support

Both shortcuts support global flags:
- `--verbose` / `-v` - Enable verbose output
- `--debug` / `-d` - Enable debug output  
- `--timing` / `-t` - Enable timing information

Flags can be placed anywhere in the command line:
```bash
lucli --verbose test-module arg1
lucli test-module arg1 --debug
lucli script.cfs --verbose arg1 arg2
```

## Implementation Details

### Parameter Exception Handler
The shortcuts are implemented in the Picocli parameter exception handler in `LuCLI.java`. When an unmatched argument exception occurs:

1. Extract any debug/verbose flags from the argument list
2. Find the first non-flag argument  
3. Check if it's an existing CFML file → execute as CFML file shortcut
4. If not a file, try to execute as module shortcut
5. If both fail, fall back to normal error handling

### CFML File Processing
For `.cfs` files, the shortcut:
1. Reads the file content
2. Prepares an ARGS array with the filename and arguments
3. Wraps the file content with the ARGS setup
4. Executes the wrapped script using `LuceeScriptEngine.eval()`

For `.cfm` and `.cfc` files, it uses the existing `LuceeScriptEngine.executeScript()` method.

### Module Processing  
Module shortcuts create a new command line and execute it as:
```
["modules", "run", moduleName, ...args]
```

This maintains full compatibility with the existing module system while providing the convenience of shortcuts.