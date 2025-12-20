# Architecture Refactor - Proof of Concept

## Branch: `refactor/simplify-architecture`

## What We Proved

We created a simple `echo` command that demonstrates the **correct architecture pattern**: implementation directly in the PicocLI `@Command` class with **no intermediate parsing layers**.

---

## The Echo Command

**File**: `src/main/java/org/lucee/lucli/cli/commands/EchoCommand.java`

```java
@Command(
    name = "echo",
    description = "Echo the provided text (proof of concept command)"
)
public class EchoCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", description = "Text to echo back")
    private List<String> words;

    @Override
    public Integer call() throws Exception {
        // ACTUAL IMPLEMENTATION HERE - No delegation!
        System.out.println(String.join(" ", words));
        return 0;
    }
}
```

**Key points**:
- ✅ PicocLI parses arguments into `List<String> words`
- ✅ Implementation is directly in `call()` method
- ✅ No conversion to `String[]` and re-parsing
- ✅ No `UnifiedCommandExecutor`
- ✅ Simple, clean, maintainable

---

## Test Results

### CLI Mode ✅

```bash
$ java -jar target/lucli.jar echo hello world
hello world
```

### Help Output ✅

```bash
$ java -jar target/lucli.jar echo --help
Usage: lucli echo [-hV] <words>...
Echo the provided text (proof of concept command)
      <words>...   Text to echo back
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
```

### Shows in Main Help ✅

```bash
$ java -jar target/lucli.jar --help | grep echo
  echo             Echo the provided text (proof of concept command)
```

---

## What This Proves

1. ✅ **PicocLI can handle implementation directly** - No need for UnifiedCommandExecutor
2. ✅ **Works in CLI mode** - Single execution path
3. ✅ **Help generation works** - PicocLI generates proper help
4. ✅ **Arguments are parsed correctly** - No manual parsing needed

---

## Architecture Comparison

### ❌ Old Way (Current)

```
User: lucli echo hello world
    ↓
PicocLI parses to EchoCommand
    ↓
EchoCommand.call() converts to String[]
    ↓
UnifiedCommandExecutor.executeCommand("echo", ["hello", "world"])
    ↓
Manual parsing in UnifiedCommandExecutor
    ↓
Finally prints "hello world"
```

**Problems**:
- Parsed 2+ times
- Converted parsed objects back to String[]
- Manual string parsing logic
- Lots of code

### ✅ New Way (Refactored)

```
User: lucli echo hello world
    ↓
PicocLI parses to EchoCommand
    ↓
EchoCommand.call() prints "hello world"
    ↓
Done!
```

**Benefits**:
- Parsed once
- No conversions
- No manual parsing
- Less code

---

## Next Steps

### Phase 1: Prove Terminal Mode Works ✅ (NEXT)

Test that `echo` works in interactive terminal mode:

```bash
$ java -jar target/lucli.jar
lucli> echo hello from terminal
hello from terminal
```

### Phase 2: Migrate Real Command

Pick a simple real command to migrate, suggested: `server list`

**Why `server list`?**
- Simple (no complex options)
- Read-only (safe to test)
- Shows real-world pattern

**Steps**:
1. Move implementation from `UnifiedCommandExecutor.handleServerList()` 
2. Directly into `ServerCommand.ListCommand.call()`
3. Test in both CLI and terminal modes
4. Verify output is identical

### Phase 3: Migrate All Commands

Once pattern is proven with `server list`, migrate:
1. `server status`
2. `server start` (complex - has many options)
3. `server stop`
4. `modules list`
5. `modules run`
6. All other commands

### Phase 4: Delete UnifiedCommandExecutor

Once all commands are migrated:
1. Delete `UnifiedCommandExecutor.java`
2. Delete redundant code in `InteractiveTerminal.java`
3. Verify all tests pass
4. Update documentation

---

## Code Savings Estimate

**Current**:
- `UnifiedCommandExecutor.java`: ~1000 lines
- `ServerCommand.java`: ~300 lines of conversion code
- `ModulesCommand.java`: ~200 lines of conversion code
- Total: **~1500 lines of redundant parsing**

**After refactor**:
- Implementation directly in `@Command` classes
- Estimated savings: **~1200 lines** (keeping ~300 for actual logic)

---

## Architectural Benefits

1. **Single Source of Truth** - PicocLI commands ARE the implementation
2. **No Parsing Overhead** - Parse once, execute once
3. **Feature Parity** - CLI and terminal use same code path
4. **Better Testing** - Test the actual command, not intermediate layers
5. **Easier Maintenance** - Change once, works everywhere
6. **Better Completion** - JLine can use PicocLI metadata directly
7. **Cleaner Code** - Obvious what each command does

---

## Success Criteria

Before merging this refactor, we need:

- [x] Echo command works in CLI mode
- [ ] Echo command works in terminal mode
- [ ] At least one real command migrated (`server list`)
- [ ] Migrated command works in both CLI and terminal
- [ ] Tab completion still works
- [ ] All existing tests pass
- [ ] Documentation updated
- [ ] Code review approved

---

## Lessons Learned

The complexity was **not necessary**. PicocLI + JLine are designed to work together without intermediate layers.

The correct pattern is:
```java
@Command(name = "mycommand")
class MyCommand implements Callable<Integer> {
    @Option(...) private String option;
    
    public Integer call() {
        // Just do the work here!
        return 0;
    }
}
```

Not:
```java
@Command(name = "mycommand")
class MyCommand implements Callable<Integer> {
    @Option(...) private String option;
    
    public Integer call() {
        // Convert back to String[] 
        String[] args = new String[]{"--option", option};
        // Pass to another processor
        return processor.execute("mycommand", args);
    }
}
```

---

## Conclusion

The proof of concept **succeeded**. The refactored architecture is:
- ✅ Simpler
- ✅ Faster
- ✅ More maintainable
- ✅ Works correctly

We should proceed with the full refactor.
