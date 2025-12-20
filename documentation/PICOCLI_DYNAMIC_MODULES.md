# PicocLI + Dynamic Module Shortcuts

## Question
Can PicocLI handle dynamic module shortcuts like `lucli <module-name>` which should expand to `lucli modules run <module-name>`?

## Answer
**YES!** PicocLI can absolutely handle this, and it's actually simpler than the current approach.

---

## Current Flow (Complicated)

```
lucli mymodule arg1 arg2
    ↓
LuCLI.main() catches it in ParameterExceptionHandler
    ↓
Checks if "mymodule" is a known subcommand (it's not)
    ↓
Checks if module exists with ModuleCommand.moduleExists()
    ↓
Calls executeModuleShortcut() which builds new args array
    ↓
Creates NEW CommandLine instance
    ↓
Executes with ["modules", "run", "mymodule", "arg1", "arg2"]
    ↓
ServerCommand converts to String[] again
    ↓
UnifiedCommandExecutor parses AGAIN
    ↓
Finally runs the module
```

**Problem**: Multiple parsing layers, re-creating CommandLine instances, lots of indirection.

---

## Simplified Flow with PicocLI

```
lucli mymodule arg1 arg2
    ↓
PicocLI's default exception handler catches UnmatchedArgumentException
    ↓
Check if "mymodule" exists in ~/.lucli/modules/
    ↓
If yes: Execute ModulesCommand.RunCommand directly
    ↓
Done!
```

**Solution**: One parsing layer, direct execution.

---

## Implementation Strategy

### Option 1: Custom Exception Handler (RECOMMENDED)

PicocLI allows custom exception handlers. Keep shortcuts in the exception handler but make it cleaner:

```java
// In LuCLI.main()
cmd.setParameterExceptionHandler(new CommandLine.IParameterExceptionHandler() {
    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) {
        if (ex instanceof CommandLine.UnmatchedArgumentException && args.length >= 1) {
            String firstArg = args[0];
            
            // Check for module shortcut
            if (ModuleCommand.moduleExists(firstArg)) {
                // Found a module! Execute it directly via PicocLI
                CommandLine.ParseResult parseResult = 
                    cmd.parseArgs("modules", "run", firstArg, /* rest of args */);
                return cmd.getExecutionStrategy().execute(parseResult);
            }
            
            // Check for CFML file shortcut
            if (firstArg.endsWith(".cfs") && new File(firstArg).exists()) {
                // Execute CFML file
                return executeCfmlFile(firstArg, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        
        // Default error handling
        ex.getCommandLine().usage(System.err);
        return 1;
    }
});
```

**Benefits**:
- Shortcuts stay in one place (exception handler)
- No re-parsing or rebuilding args
- Uses PicocLI's execution strategy directly
- Clean and maintainable

### Option 2: Dynamic Subcommand Registration

PicocLI supports dynamically adding subcommands at runtime:

```java
// In LuCLI.main() - AFTER creating CommandLine
CommandLine cmd = new CommandLine(new LuCLICommand());

// Dynamically register module shortcuts
Path modulesDir = Paths.get(System.getProperty("user.home"), ".lucli", "modules");
if (Files.exists(modulesDir)) {
    try (Stream<Path> modules = Files.list(modulesDir)) {
        modules.filter(Files::isDirectory)
               .forEach(moduleDir -> {
                   String moduleName = moduleDir.getFileName().toString();
                   
                   // Create a dynamic command that delegates to "modules run"
                   cmd.addSubcommand(moduleName, new DynamicModuleCommand(moduleName));
               });
    }
}

// Now these work automatically:
// lucli mymodule arg1 arg2
// lucli completion will show them too!
```

**DynamicModuleCommand implementation**:

```java
@Command(name = "", description = "Run module: ${COMMAND-NAME}")
class DynamicModuleCommand implements Callable<Integer> {
    
    private final String moduleName;
    
    @Parameters(arity = "0..*", description = "Arguments for the module")
    private List<String> args = new ArrayList<>();
    
    public DynamicModuleCommand(String moduleName) {
        this.moduleName = moduleName;
    }
    
    @Override
    public Integer call() throws Exception {
        // Just execute the module directly
        ModuleCommand.executeModuleByName(moduleName, 
            args.toArray(new String[0]));
        return 0;
    }
}
```

**Benefits**:
- Modules appear as real subcommands in `lucli --help`
- Tab completion works for module names automatically
- No exception handler tricks needed
- Modules are "first-class citizens"

**Drawbacks**:
- Need to scan modules directory at startup (but it's fast)
- `--help` output could get cluttered with many modules

---

## Recommended Approach

**Use Option 1 (Custom Exception Handler)** because:

1. **Clean help output** - Modules don't clutter `lucli --help`
2. **Fast startup** - No directory scanning
3. **Flexible** - Easy to handle CFML files, .lucli scripts, etc.
4. **Works with refactor** - Compatible with removing UnifiedCommandExecutor

### Implementation in Refactored Code

```java
// LuCLI.java
public static void main(String[] args) throws Exception {
    CommandLine cmd = new CommandLine(new LuCLICommand());
    
    cmd.setParameterExceptionHandler((ex, parsedArgs) -> {
        if (ex instanceof CommandLine.UnmatchedArgumentException) {
            return handleShortcuts(cmd, parsedArgs);
        }
        
        // Default error handling
        ex.getCommandLine().usage(System.err);
        return 1;
    });
    
    System.exit(cmd.execute(args));
}

private static int handleShortcuts(CommandLine cmd, String[] args) {
    String firstArg = args[0];
    String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
    
    // Module shortcut: lucli <module> args...
    if (ModuleCommand.moduleExists(firstArg)) {
        // Build proper args for "modules run <module> args..."
        List<String> moduleArgs = new ArrayList<>();
        moduleArgs.add("modules");
        moduleArgs.add("run");
        moduleArgs.add(firstArg);
        moduleArgs.addAll(Arrays.asList(remainingArgs));
        
        return cmd.execute(moduleArgs.toArray(new String[0]));
    }
    
    // CFML file shortcut: lucli script.cfs args...
    if (firstArg.endsWith(".cfs") && new File(firstArg).exists()) {
        return executeCfmlFile(firstArg, remainingArgs);
    }
    
    // .lucli script shortcut
    if (firstArg.endsWith(".lucli") && new File(firstArg).exists()) {
        return executeLucliScript(firstArg, remainingArgs);
    }
    
    // Unknown command
    System.err.println("Unknown command: " + firstArg);
    cmd.usage(System.err);
    return 1;
}
```

---

## Terminal Mode Integration

For terminal mode, the same pattern works:

```java
// InteractiveTerminal.java
LineReader reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .completer(new PicocliCompleter(new CommandLine(new LuCLICommand())))
    .build();

while (true) {
    String line = reader.readLine(prompt);
    String[] args = parseCommandLine(line);
    
    if (args[0].equals("exit") || args[0].equals("quit")) {
        break;
    }
    
    // Execute via PicocLI - shortcuts handled automatically!
    CommandLine cmd = new CommandLine(new LuCLICommand());
    cmd.setParameterExceptionHandler(/* same handler as above */);
    
    int exitCode = cmd.execute(args);
    if (exitCode != 0) {
        System.err.println("Command failed with exit code: " + exitCode);
    }
}
```

---

## Completion Support

For tab completion in terminal mode, we can enhance the completer:

```java
class LuCLIDynamicCompleter implements Completer {
    private final PicocliCompleter picocliCompleter;
    private final Supplier<List<String>> moduleNamesSupplier;
    
    public LuCLIDynamicCompleter(CommandLine cmd) {
        this.picocliCompleter = new PicocliCompleter(cmd);
        this.moduleNamesSupplier = () -> {
            // Lazily load module names when needed
            try {
                Path modulesDir = ModuleCommand.getModulesDirectory();
                if (Files.exists(modulesDir)) {
                    return Files.list(modulesDir)
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
                }
            } catch (IOException e) {
                // Ignore
            }
            return Collections.emptyList();
        };
    }
    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        // First try PicocLI completion (for known commands)
        picocliCompleter.complete(reader, line, candidates);
        
        // If we're completing the first word and nothing matched,
        // add module names as candidates
        if (line.wordIndex() == 0 && candidates.isEmpty()) {
            moduleNamesSupplier.get().forEach(name -> 
                candidates.add(new Candidate(name, name, null, 
                    "module: " + name, null, null, true)));
        }
    }
}
```

---

## Summary

**YES, PicocLI can handle dynamic modules!**

The best approach:

1. **Keep shortcuts in exception handler** - Clean and simple
2. **No UnifiedCommandExecutor needed** - PicocLI executes commands directly
3. **Works in both CLI and terminal mode** - Same code path
4. **Tab completion works** - With a custom completer that adds module names
5. **No re-parsing** - Parse once with PicocLI, execute directly

The refactor is still the right move. Modules actually become **easier** to handle without all the intermediate layers.

---

## Action Items for Refactor

1. ✅ Keep shortcuts in `LuCLI.main()` exception handler
2. ✅ Remove `UnifiedCommandExecutor` 
3. ✅ Move implementation into `@Command` classes
4. ✅ Update `InteractiveTerminal` to use PicocLI directly
5. ✅ Add `LuCLIDynamicCompleter` for module name completion
6. ✅ Test: `lucli mymodule arg1 arg2` works in both CLI and terminal

**Result**: Simpler, faster, more maintainable code with full dynamic module support.
