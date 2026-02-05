# Picocli Enhancements and Code Refactoring

**Priority:** Medium  
**Effort:** 2-4 hours  
**Status:** Planned

## Overview
Leverage additional picocli features and complete remaining code refactoring for cleaner, more maintainable codebase.

## Picocli Features to Add

### High Priority

#### 1. Usage Help Auto-Width
```java
@Command(
    name = "lucli",
    usageHelpAutoWidth = true,  // Auto-adjust to terminal width
    showDefaultValues = true,   // Show default values in help
    sortOptions = false         // Keep custom order
)
```
**Benefit:** Better formatted help that adapts to terminal size

#### 2. Default Value Provider
```java
@Command(
    name = "lucli",
    defaultValueProvider = PropertiesDefaultProvider.class
)
```

Allow users to set preferences in `~/.lucli.properties`:
```properties
verbose=true
timing=false
preserveWhitespace=true
```

**Benefit:** Persistent user preferences without environment variables

#### 3. @-files Support
Enable reading arguments from files:
```bash
lucli @args.txt  # Reads arguments from file
```

Built into picocli, just needs activation.

### Medium Priority

#### 4. Mixins for Common Options
Instead of parent command references, use mixins:
```java
class StandardOptions {
    @Option(names = "-v", "--verbose") boolean verbose;
    @Option(names = "-d", "--debug") boolean debug;
    @Option(names = "-t", "--timing") boolean timing;
}

@Command(name = "subcommand")
class SubCommand {
    @Mixin StandardOptions options;
}
```

**Benefit:** DRY principle, easier to maintain common options

#### 5. Interactive Options for Secrets
Replace manual password prompts:
```java
@Option(names = "--password", interactive = true)
char[] password;
```

**Benefit:** Picocli handles secure input automatically

#### 6. Argument Groups
Group related options:
```java
@ArgGroup(heading = "Output Options%n")
OutputOptions output;

static class OutputOptions {
    @Option(names = "-v", "--verbose") boolean verbose;
    @Option(names = "-d", "--debug") boolean debug;
}
```

**Benefit:** Better organized help output

### Low Priority

#### 7. Custom Type Converters
```java
@Option(names = "--version", converter = LuceeVersionConverter.class)
LuceeVersion luceeVersion;
```

#### 8. Completion Script Auto-Generation
Enhance existing completion command with picocli's built-in generator.

## Code Refactoring Tasks

### High Priority - Extract Script Executor

**File:** `LuCLI.java` (~430 lines of script execution code)  
**Target:** `org.lucee.lucli.script.LucliScriptExecutor`

**Methods to Extract:**
- `executeLucliScript()` - Main script executor (200+ lines)
- `executeLucliScriptCommand()` - Single command dispatcher
- `handleCommandSubstitutionAssignment()` - Variable assignment
- Secret management methods:
  - `resolveSecretsInScriptLine()`
  - `initializeLucliScriptSecretStore()`
  - `preResolveSecretsInScript()`
- Helper utilities:
  - `isExecEvaluation()`, `getExecEvaluationInnerCommand()`
  - `isExitCommand()`, `getExitCodeFromExitCommand()`
  - `isEmptyLucliExecLine()`

**Related State:**
- `lucliScript`, `lucliScriptSecretStore`
- `lucliScriptSecretsInitialized`, `LUCLI_SECRET_PATTERN`

**Benefit:** 
- LuCLI.java becomes focused solely on CLI routing
- Script execution logic is encapsulated and testable
- Reduces LuCLI.java from ~1400 to ~970 lines

### Medium Priority

#### Replace Manual if-checks with New Output Methods
Find and replace throughout codebase:
```java
// Before
if (LuCLI.verbose) {
    System.out.println("message");
}

// After
LuCLI.verbose("message");
```

**Files to Update:**
- Terminal.java (6 occurrences)
- CfmlCompleter.java
- ModuleCommand.java
- ModulesInitCommandImpl.java
- And others (~12 files total)

#### Extract Output Utilities
**Target:** `org.lucee.lucli.util.OutputUtil`

Move output methods from LuCLI to dedicated utility class:
- `verbose()`, `debug()`, `debugStack()`
- `info()`, `error()`

**Benefit:** Reduces coupling, easier testing

### Low Priority

#### Extract Version Utilities
**Target:** `org.lucee.lucli.util.VersionUtil`
- `getVersion()`
- `getVersionInfo()`

#### Extract Result History
**Target:** `org.lucee.lucli.script.ResultHistory`
- `recordLucliResult()`
- `getLucliResult()`
- Related state and constants

## Implementation Order

1. ✅ Add picocli enhancements (quick wins)
2. Extract script executor (biggest impact)
3. Replace manual if-checks
4. Extract utility classes (if still valuable)

## Notes

- Keep backward compatibility during refactoring
- Ensure all tests pass after each change
- Update WARP.md with new patterns
- Consider creating example configs for default value provider

## Related Work

- Branch: `refactor/lucli-main-cleanup` (current work)
- Previous refactoring reduced code by ~112 lines
- New output API already in place (commit: fe9f86c)

## Success Criteria

- [ ] Picocli features implemented and tested
- [ ] LuCLI.java under 1000 lines
- [ ] Script execution extracted to separate class
- [ ] Help output improved with auto-width
- [ ] User preferences work via .lucli.properties
- [ ] All existing tests still pass
