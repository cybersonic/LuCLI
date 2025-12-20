# Completed: Centralized Logging Methods

**Date:** 2025-12-20  
**Status:** ✅ Complete and Tested

---

## Summary

Added centralized logging helper methods to `LuCLI` class to replace scattered `if (verbose)` and `if (debug)` checks throughout the codebase.

---

## New Methods Added

```java
// Verbose logging (stdout)
public static void printVerbose(String message)

// Debug logging (stderr with [DEBUG] prefix)
public static void printDebug(String message)
public static void printDebug(String context, String message)

// Info logging (always shown)
public static void printInfo(String message)

// Error logging
public static void printError(String message)

// Stack trace printing (only in debug mode)
public static void printDebugStackTrace(Exception e)
```

---

## Benefits

### Before
```java
if (LuCLI.verbose) {
    System.out.println("Initializing Lucee CFML engine...");
}

if (LuCLI.debug) {
    System.err.println("[DEBUG CfmlCommand] cfmlCode: '" + cfmlCode + "'");
}

if (LuCLI.debug) {
    e.printStackTrace();
}
```

### After
```java
LuCLI.printVerbose("Initializing Lucee CFML engine...");

LuCLI.printDebug("CfmlCommand", "cfmlCode: '" + cfmlCode + "'");

LuCLI.printDebugStackTrace(e);
```

### Advantages
1. **Less boilerplate** - No need for if checks everywhere
2. **Consistent formatting** - Debug messages automatically get `[DEBUG]` prefix and context
3. **Centralized control** - Easy to add features like log levels, file logging, etc.
4. **Better for modules** - Modules can use `LuCLI.printVerbose()` instead of needing verbose flag passed
5. **Cleaner code** - Focus on the message, not the conditional logic

---

## Files Updated

### 1. LuCLI.java
- **Added**: 6 new logging methods (~50 lines)
- **Updated**: 10 usages to use new methods
  - printVerbose: 4 usages
  - printDebug: 3 usages
  - printDebugStackTrace: 3 usages

### 2. CfmlCommand.java
- **Updated**: 8 usages to use new methods
  - printVerbose: 2 usages
  - printDebug: 4 usages
  - printDebugStackTrace: 1 usage
  - Bonus: Removed duplicate context in debug messages

---

## Usage Patterns

### Verbose Messages
Use for informational messages that help users understand what's happening:
```java
LuCLI.printVerbose("Executing module shortcut: " + moduleName);
LuCLI.printVerbose("Lucee engine ready.");
```

### Debug Messages (Simple)
Use for debugging without specific context:
```java
LuCLI.printDebug("SET directive: " + key + " = " + value);
```

### Debug Messages (With Context)
Use when you need to identify which class/method is logging:
```java
LuCLI.printDebug("CfmlCommand", "cfmlCode: '" + cfmlCode + "'");
LuCLI.printDebug("CfmlCommand", "Wrapped script:\n" + script);
```

### Stack Traces
Use for exception details in debug mode:
```java
try {
    // ...
} catch (Exception e) {
    System.err.println("Error: " + e.getMessage());
    LuCLI.printDebugStackTrace(e);  // Only prints if debug=true
}
```

---

## Remaining Work

### Files with Outstanding `if (verbose)` / `if (debug)` patterns

These files still have manual checks that could be migrated:

1. **Terminal.java** - 5 usages
   - Lines 71, 93, 117, 320, 327

2. **ModuleCommand.java** - 4 usages
   - Lines 62, 234, 705, 739

3. **LuceeScriptEngine.java** - 4 usages
   - Lines 63, 74, 366, 1396

4. **ModuleRepositoryIndex.java** - 3 usages
   - Lines 80, 133, 139

5. **CFLintCommand.java** - 2 usages
   - Lines 240, 246

6. **UnifiedCommandExecutor.java** - 1 usage
   - Line 60

7. **LuCLICommand.java** - 2 usages
   - Lines 120, 145

8. **BuiltinVariableManager.java** - 1 usage
   - Line 80

9. **CfmlCompleter.java** - 1 usage
   - Line 113

**Total**: ~23 more usages to migrate

---

## Migration Strategy

### Low Priority (Can wait)
- Terminal.java - Uses terminal-specific output (terminal.writer())
- LuceeScriptEngine.java - Internal engine logging
- ModuleRepositoryIndex.java - Rare edge cases

### Medium Priority
- ModuleCommand.java - User-facing messages
- CFLintCommand.java - User-facing messages

### Can Be Done Anytime
The remaining files can be migrated incrementally as we touch them for other reasons.

---

## Design Notes

### Why stderr for debug?
Debug messages go to stderr so they don't interfere with command output that might be piped or captured.

### Why context parameter?
The context (class/method name) helps identify where debug messages come from in large codebases. Format: `[DEBUG Context]`.

### Why not a logging framework?
- **Simplicity** - No external dependencies
- **Lightweight** - Just a few methods
- **Sufficient** - Meets current needs
- **Extensible** - Can add log levels, file output, etc. later if needed

### Future Enhancements
Could add later:
- `printWarn(String message)` for warnings
- `setLogLevel(Level level)` for fine-grained control
- `setLogFile(Path file)` for file logging
- `printTiming(String label, long ms)` for performance logging

---

## Testing Results

### ✅ Compilation
```bash
$ mvn compile
[INFO] BUILD SUCCESS
```

### ✅ Unit Tests
```bash
$ mvn test
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

### ✅ Verbose Mode
```bash
$ java -jar lucli.jar --verbose --version
LuCLI 0.1.207-SNAPSHOT
Lucee Version: 7.0.1.93-SNAPSHOT
```

### ✅ Debug Mode
```bash
$ java -jar lucli.jar --debug cfml "1 + 1"
[DEBUG CfmlCommand] cfmlParts: [1 + 1]
[DEBUG CfmlCommand] cfmlCode: '1 + 1'
2
```

---

## Code Metrics

### Lines of Code
- **Added**: ~50 lines (new methods)
- **Changed**: ~30 lines (updated usages)
- **Net**: +20 lines (but much cleaner code)

### Method Count
- **Before**: 0 logging helpers
- **After**: 6 logging helpers

### Conditional Checks Eliminated
- **LuCLI.java**: 10 `if (verbose)` / `if (debug)` blocks removed
- **CfmlCommand.java**: 8 conditional blocks removed
- **Total so far**: 18 conditional blocks eliminated

---

## API for Modules

Since `LuCLI.verbose`, `LuCLI.debug`, and `LuCLI.timing` are public static, and now we have public static logging methods, **modules can use these directly**:

```cfml
// In a Module.cfc
function run() {
    // Check flags
    if (LuCLI.verbose) {
        LuCLI.printVerbose("Starting module execution...");
    }
    
    // Or just call directly (the method checks internally)
    LuCLI.printVerbose("Processing files...");
    LuCLI.printDebug("mymodule", "Found " & fileCount & " files");
}
```

This is especially useful for modules that want to respect the global verbose/debug flags without having them passed as parameters.

---

## Related Improvements

This follows the same philosophy as:
- **Version consolidation** - Centralize common functionality
- **PicocLI API** - Use built-in capabilities instead of manual patterns
- **Command migration** - Direct, clean implementations

All part of simplifying and standardizing the codebase.

---

## Status

✅ **Partial Complete** - Core functionality done
- Methods added and tested
- LuCLI.java and CfmlCommand.java migrated
- ~23 more usages can be migrated incrementally
- Ready to commit this phase

**Future work**: Migrate remaining files as they're touched for other reasons.
