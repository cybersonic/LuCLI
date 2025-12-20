# Naming Conventions for Refactored Architecture

## Terminal Naming
✅ **DONE**: Consolidated to simple, clear names

- ~~`InteractiveTerminal`~~ → DELETED
- ~~`InteractiveTerminalV2`~~ → **`Terminal`**

**Rationale**: We have ONE terminal. No need for qualifiers like "Interactive" or "V2".

---

## Command Naming Pattern

### For PicocLI @Command Classes

Commands that are ONLY PicocLI annotations with no actual logic should NOT have `Impl` suffix:

```java
// ✅ CORRECT - Pure PicocLI command (container for subcommands)
@Command(name = "server", subcommands = {...})
public class ServerCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        // Just show help or delegate to subcommands
    }
}
```

### For Command Implementations

When a command has actual implementation logic, use `*Impl` pattern:

```java
// ✅ CORRECT - Has real implementation
@Command(name = "start")
public class StartCommandImpl implements Callable<Integer> {
    @Option(names = {"--port"})
    private int port;
    
    @Override
    public Integer call() {
        // ACTUAL IMPLEMENTATION HERE
        LuceeServerManager manager = new LuceeServerManager();
        manager.startServer(port, ...);
        return 0;
    }
}
```

---

## Current State After Consolidation

### Files to Keep As-Is (No `Impl` needed)
These are simple commands or command containers:

- `ServerCommand.java` - Container for server subcommands
- `ModulesCommand.java` - Container for module subcommands  
- `CfmlCommand.java` - Simple CFML executor
- `ParrotCommand.java` - Proof of concept
- `CompletionCommand.java` - Shell completion
- `VersionsListCommand.java` - Simple version lister

### Pattern Going Forward

When migrating commands from UnifiedCommandExecutor:

**Before** (in UnifiedCommandExecutor):
```java
private String handleServerStart(LuceeServerManager manager, String[] args) {
    // Complex logic here
}
```

**After** (as command implementation):
```java
@Command(name = "start")
public class StartCommandImpl implements Callable<Integer> {
    @Option(names = {"--port"})
    private int port;
    
    @Override
    public Integer call() {
        // Move the complex logic here
    }
}
```

---

## Examples

### ✅ Good Naming

```
ParrotCommand.java - Simple echo-style command
ServerCommand.java - Container for server subcommands
    ├─ StartCommandImpl - Complex server start logic
    ├─ StopCommandImpl - Server stop logic
    ├─ ListCommandImpl - List servers logic
Terminal.java - The one and only terminal
```

### ❌ Bad Naming

```
ParrotCommandImpl.java - Too complex for simple command
ServerCommandV2.java - No versioning
InteractiveTerminal.java - Redundant qualifier
ServerStartCommand.java - Missing Impl for complex logic
```

---

## When to Use `Impl`

Use `*Impl` suffix when:
1. ✅ Command has substantial implementation logic
2. ✅ Command directly manipulates server/system state
3. ✅ Command has complex option parsing and validation
4. ✅ Command is more than just delegating to another class

Don't use `Impl` when:
1. ❌ Command is just a container for subcommands
2. ❌ Command is trivial (like parrot/echo)
3. ❌ Command just delegates immediately to another service

---

## Migration Checklist

When migrating a command from UnifiedCommandExecutor:

1. [ ] Create `*CommandImpl.java` (if complex logic)
2. [ ] Add `@Command` annotation with proper options
3. [ ] Move logic from UnifiedCommandExecutor into `call()` method
4. [ ] Remove String[] parsing - use `@Option` and `@Parameters`
5. [ ] Test in both CLI and terminal modes
6. [ ] Delete corresponding method from UnifiedCommandExecutor

---

## Summary

**Simple rule**: 
- Container commands or trivial commands → No suffix
- Commands with real implementation → `*Impl`
- Only one of anything → No version numbers or qualifiers

This keeps names clear, predictable, and maintainable.
