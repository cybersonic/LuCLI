# Unified Command Architecture

## Problem Statement

LuCLI had a significant architectural issue where **command implementations were duplicated** between two modes:

1. **CLI Mode**: Direct command execution (e.g., `java -jar lucli.jar server start`)
2. **Terminal Mode**: Interactive terminal commands (e.g., typing `server start` in the terminal)

This created several problems:
- **Feature parity gaps**: Terminal mode lacked commands like `server monitor` and `server log`
- **Maintenance burden**: Changes had to be made in two places 
- **Inconsistent behavior**: Different code paths could lead to different behaviors
- **Missing functionality**: The terminal mode didn't support the `--name` flag for server commands

## Solution: UnifiedCommandExecutor

We created a **single point of command execution** that both modes can use:

```java
org.lucee.lucli.commands.UnifiedCommandExecutor
```

### Architecture Overview

```
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│   CLI Mode      │    │                      │    │  Terminal Mode      │
│   (LuCLI.java)  │───▶│ UnifiedCommandExecutor│◀───│ (CommandProcessor)  │
└─────────────────┘    │                      │    └─────────────────────┘
                       └──────────────────────┘
                                 │
                                 ▼
                       ┌──────────────────────┐
                       │ Server Commands      │
                       │ Module Commands      │ 
                       │ Monitor Commands     │
                       │ Log Commands         │
                       └──────────────────────┘
```

### Key Features

#### 1. Mode-Aware Output Formatting
The executor detects whether it's running in CLI or Terminal mode and formats output accordingly:

- **CLI Mode**: Outputs directly to console and exits with appropriate exit codes
- **Terminal Mode**: Returns formatted strings for the terminal to display

#### 2. Consistent Command Support
Both modes now support the exact same commands:

✅ `server start [--force] [--name <name>] [--version <ver>]`  
✅ `server stop [--name <name>]`  
✅ `server status [--name <name>]`  
✅ `server list`  
✅ `server monitor`  
✅ `server log`  
✅ `modules [command]`

#### 3. Graceful Degradation
Interactive commands that don't work well in terminal mode (like `monitor` and `log`) show helpful guidance:

```
🖥️  Starting JMX monitoring dashboard...
⚠️  Note: This will exit the terminal session and start the interactive monitor.
💡 To start monitoring, use: java -jar lucli.jar server monitor
```

#### 4. Automatic Feature Parity
Any new commands added to the UnifiedCommandExecutor automatically work in both modes.

## Implementation Details

### Before (Duplicated Implementation)
```java
// In LuCLI.java
private static void handleServerCommand(String[] args) {
    // CLI implementation
}

// In CommandProcessor.java  
private String serverCommand(String[] args) {
    // Terminal implementation (different code!)
}
```

### After (Unified Implementation)
```java
// In LuCLI.java
private static void handleServerCommand(String[] args) {
    UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);
    executor.executeCommand("server", serverArgs);
}

// In CommandProcessor.java
private String serverCommand(String[] args) {
    UnifiedCommandExecutor executor = new UnifiedCommandExecutor(true, currentDir);
    return executor.executeCommand("server", args);
}
```

### Constructor Parameters
```java
new UnifiedCommandExecutor(isTerminalMode, currentWorkingDirectory)
```

- `isTerminalMode`: `false` for CLI, `true` for Terminal
- `currentWorkingDirectory`: The directory context for the command

## Benefits Achieved

1. **✅ Full Feature Parity**: Terminal now supports all CLI commands including `--name` flags
2. **✅ Reduced Code Duplication**: Single implementation instead of duplicate code paths  
3. **✅ Easier Maintenance**: Changes only need to be made in one place
4. **✅ Consistent Behavior**: Same logic ensures same behavior across modes
5. **✅ Future-Proof**: New commands automatically work in both modes
6. **✅ Mode-Appropriate UX**: Different output formatting for different contexts

## Testing

The architecture change preserves all existing functionality while adding the missing features:

### Terminal Mode Now Supports:
- `server stop --name myserver`
- `server status --name myserver` 
- `server monitor` (with helpful guidance)
- `server log` (with helpful guidance)

### CLI Mode Continues to Support:
- All existing commands with full functionality
- Interactive commands like monitor and log work as before

## Future Extensions

Adding new commands is now simple - just extend the UnifiedCommandExecutor:

```java
// Add to UnifiedCommandExecutor.executeCommand()
case "newcommand":
    return executeNewCommand(args);
```

Both CLI and Terminal modes will automatically support the new command.

## Migration Notes

The refactoring was designed to be **backwards compatible**:
- No breaking changes to existing command syntax
- All existing functionality preserved
- Performance characteristics maintained
- Error handling improved and standardized

This architectural improvement solves the fundamental design issue and provides a solid foundation for future command additions.
