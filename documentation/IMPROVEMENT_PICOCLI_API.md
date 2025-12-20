# Improvement: Use PicocLI API Instead of Manual Command List

**Date:** 2025-12-20  
**Type:** Code Quality Improvement  
**Status:** ✅ Complete

---

## Problem

`LuCLI.java` had a manual list of known subcommands that needed to be updated every time a new command was added:

```java
private static boolean isKnownSubcommand(String command) {
    // List of known subcommands that should not be treated as shortcuts
    return "server".equals(command) || 
           "modules".equals(command) || 
           "module".equals(command) || 
           "cfml".equals(command) || 
           "help".equals(command) ||
           "terminal".equals(command);
}
```

**Issues:**
1. **Manual maintenance** - Easy to forget to update when adding commands
2. **Out of sync risk** - List could diverge from actual registered commands
3. **Duplication** - PicocLI already knows what commands exist
4. **Missing commands** - `parrot` command was added but not in this list

---

## Solution

Use PicocLI's built-in API to query registered subcommands dynamically:

```java
private static boolean isKnownSubcommand(String command) {
    // Query PicocLI for registered subcommands instead of maintaining a manual list
    CommandLine cmd = new CommandLine(new LuCLICommand());
    return cmd.getSubcommands().containsKey(command);
}
```

---

## PicocLI API Used

### `CommandLine.getSubcommands()`
Returns a `Map<String, CommandLine>` where:
- **Keys**: Command names (including aliases)
- **Values**: CommandLine objects for each subcommand

This gives us access to:
- All registered command names
- Command aliases (e.g., "modules" and "module")
- Dynamically registered commands
- Subcommand hierarchies

---

## Benefits

### 1. **Automatic Synchronization**
Commands are discovered from the `@Command` annotations automatically:
```java
@Command(name = "parrot", description = "...")
public class ParrotCommand implements Callable<Integer> { }
```
No need to update `isKnownSubcommand()` - it's automatic!

### 2. **Alias Support**
Aliases are automatically included:
```java
@Command(name = "modules", aliases = {"module"})
```
Both "modules" and "module" are detected without extra code.

### 3. **Single Source of Truth**
PicocLI command registration is the only place that needs to be updated:
```java
@Command(
    name = "lucli",
    subcommands = {
        ServerCommand.class,
        ModulesCommand.class,
        CfmlCommand.class,
        ParrotCommand.class
    }
)
```

### 4. **No Maintenance Burden**
Add a new command? Just register it in `@Command.subcommands`. Done.

### 5. **Reduces Technical Debt**
Eliminates one more manual list to maintain.

---

## Implementation Details

### Location
`LuCLI.java` line 350-353

### Change Diff
```diff
- private static boolean isKnownSubcommand(String command) {
-     // List of known subcommands that should not be treated as shortcuts
-     return "server".equals(command) || 
-            "modules".equals(command) || 
-            "module".equals(command) || 
-            "cfml".equals(command) || 
-            "help".equals(command) ||
-            "terminal".equals(command);
- }

+ private static boolean isKnownSubcommand(String command) {
+     // Query PicocLI for registered subcommands instead of maintaining a manual list
+     CommandLine cmd = new CommandLine(new LuCLICommand());
+     return cmd.getSubcommands().containsKey(command);
+ }
```

### Performance Consideration
Creating a new `CommandLine` instance is lightweight - PicocLI is designed for this.
The method is only called during command parsing (not hot path).

---

## Related Patterns

### Terminal.java Already Uses This!
`Terminal.java` line 258-260 already uses the same pattern:
```java
private static boolean isPicocliCommand(String command) {
    return picocliCommandLine.getSubcommands().containsKey(command);
}
```

This improvement brings `LuCLI.java` in line with `Terminal.java`.

---

## Testing

### ✅ Recognized Subcommands
```bash
$ java -jar target/lucli.jar server --help
Usage: lucli server [-hV] [COMMAND]
Manage Lucee server instances
```

### ✅ Module Shortcuts Still Work
Module names that aren't registered subcommands still work as shortcuts:
```bash
$ java -jar target/lucli.jar hello-world
# Executes as: lucli modules run hello-world
```

### ✅ All Tests Pass
```bash
$ mvn test
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

---

## Commands Now Auto-Detected

With the PicocLI API, these are automatically recognized:
- `server` (ServerCommand)
- `modules` (ModulesCommand)
- `module` (alias for modules)
- `cfml` (CfmlCommand)
- `parrot` (ParrotCommand) ← **Was missing from manual list!**
- Any future commands added to `LuCLICommand.subcommands`

---

## Other PicocLI APIs Available

For future reference, other useful PicocLI APIs:

### Get All Subcommand Names
```java
Set<String> commandNames = cmd.getSubcommands().keySet();
```

### Check if Command Has Subcommands
```java
boolean hasSubcommands = !cmd.getSubcommands().isEmpty();
```

### Get Subcommand by Name
```java
CommandLine subCmd = cmd.getSubcommands().get("server");
```

### Iterate All Subcommands
```java
for (Map.Entry<String, CommandLine> entry : cmd.getSubcommands().entrySet()) {
    String name = entry.getKey();
    CommandLine subCmd = entry.getValue();
    // Process each subcommand
}
```

### Get Command Model (for reflection)
```java
CommandSpec spec = cmd.getCommandSpec();
String commandName = spec.name();
String description = spec.usageMessage().description()[0];
```

---

## Future Improvements

### Cache CommandLine Instance
If performance becomes a concern (unlikely), we could cache the CommandLine:
```java
private static CommandLine cachedCommandLine;

private static boolean isKnownSubcommand(String command) {
    if (cachedCommandLine == null) {
        cachedCommandLine = new CommandLine(new LuCLICommand());
    }
    return cachedCommandLine.getSubcommands().containsKey(command);
}
```

### Dynamic Command Discovery
Could use PicocLI to build dynamic help or list all available commands:
```java
public static void listAllCommands() {
    CommandLine cmd = new CommandLine(new LuCLICommand());
    System.out.println("Available commands:");
    for (String name : cmd.getSubcommands().keySet()) {
        System.out.println("  - " + name);
    }
}
```

---

## Impact

### Lines Changed
- **Removed:** 6 lines (manual OR chain)
- **Added:** 3 lines (PicocLI API call)
- **Net:** -3 lines

### Maintenance Burden
**Before:** Update in 2 places when adding command
1. Register in `@Command.subcommands`
2. Add to `isKnownSubcommand()` list

**After:** Update in 1 place only
1. Register in `@Command.subcommands`

---

## Lessons Learned

1. **Check the API first** - PicocLI already had this feature
2. **Look for patterns** - Terminal.java was already doing it right
3. **Question manual lists** - Often a sign there's a better way
4. **Small improvements matter** - Reduces cognitive load over time

---

## Related Documentation

- PicocLI JavaDoc: https://picocli.info/apidocs/
- `CommandLine.getSubcommands()`: Returns the subcommand map
- Similar pattern in: `Terminal.isPicocliCommand()` (line 258)
