# Command Migration Priority List

Ordered from **least dangerous/complex** to **most complex**

---

## ✅ Already Migrated (Properly Structured)

These commands already follow the correct PicocLI pattern:

1. **ParrotCommand** - Proof of concept ✅
2. **CfmlCommand** - CFML expression execution ✅
3. **VersionsListCommand** - Hidden helper for completion ✅

---

## 🟢 Tier 1: Zero Risk - Read-Only, No External Dependencies

### 1. **help** (Built-in Terminal Command)
**Complexity:** ⭐ (Very Simple)  
**Risk:** None - Just prints help text  
**Current:** Handled in `Terminal.showHelp()` line 192-194  
**Why First:** Simplest possible command, pure output  
**Lines of Code:** ~10-20  
**Dependencies:** None

### 2. **version / --version** (Built-in Terminal Command)
**Complexity:** ⭐ (Very Simple)  
**Risk:** None - Just prints version string  
**Current:** Handled in `Terminal.dispatchCommand()` line 196-198  
**Why Early:** No logic, just returns `LuCLI.getFullVersionInfo()`  
**Lines of Code:** ~5-10  
**Dependencies:** None

### 3. **lucee-version / --lucee-version** (Built-in Terminal Command)
**Complexity:** ⭐ (Very Simple)  
**Risk:** None - Just prints Lucee version  
**Current:** Handled in `Terminal.showLuceeVersion()` line 200-202  
**Why Early:** Similar to version command  
**Lines of Code:** ~10-15  
**Dependencies:** LuceeScriptEngine (read-only)

---

## 🟡 Tier 2: Low Risk - Read-Only with Simple Logic

### 4. **completion** Commands
**Complexity:** ⭐⭐ (Simple to Medium)  
**Risk:** Low - Generates shell completion scripts  
**Current:** `CompletionCommand.java` (already structured, just needs verification)  
**Files:** 
- `CompletionCommand.java` - Container
- `CompletionCommand.BashCommand` - Bash script generator
- `CompletionCommand.ZshCommand` - Zsh script generator
**Why Here:** Pure output generation, no state changes  
**Lines of Code:** ~200 total  
**Dependencies:** None (self-contained)

### 5. **modules list** (Read-Only)
**Complexity:** ⭐⭐ (Simple)  
**Risk:** Low - Just lists available modules  
**Current:** `ModulesCommand.ListCommand` via UnifiedCommandExecutor  
**Why Here:** File system read-only, formatting output  
**Lines of Code:** ~50-100  
**Dependencies:** File system access (read-only)

### 6. **modules info <module>** (Read-Only)
**Complexity:** ⭐⭐ (Simple)  
**Risk:** Low - Reads module metadata  
**Current:** `ModulesCommand.InfoCommand` via UnifiedCommandExecutor  
**Why Here:** Reads JSON, displays info  
**Lines of Code:** ~50-100  
**Dependencies:** JSON parsing (Jackson)

---

## 🟠 Tier 3: Medium Risk - File System Operations (Non-Critical)

### 7. **modules init <name>** (Creates Files)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Medium - Creates module template  
**Current:** `ModulesCommand.InitCommand` via UnifiedCommandExecutor  
**Why Here:** Creates files but in predictable location  
**Lines of Code:** ~100-150  
**Dependencies:** File system write, templates

### 8. **modules install <name>** (Downloads & Installs)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Medium - Downloads from registry  
**Current:** `ModulesCommand.InstallCommand` via UnifiedCommandExecutor  
**Why Here:** Network I/O, file operations  
**Lines of Code:** ~150-200  
**Dependencies:** HTTP client, file system

### 9. **modules uninstall <name>** (Removes Files)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Medium - Deletes module directory  
**Current:** `ModulesCommand.UninstallCommand` via UnifiedCommandExecutor  
**Why Here:** Deletes files (but not critical ones)  
**Lines of Code:** ~50-100  
**Dependencies:** File system delete

### 10. **modules update [name]** (Downloads & Replaces)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Medium - Updates existing modules  
**Current:** `ModulesCommand.UpdateCommand` via UnifiedCommandExecutor  
**Why Here:** Network + file operations  
**Lines of Code:** ~150-200  
**Dependencies:** HTTP client, file system

---

## 🔴 Tier 4: Higher Risk - Server Operations (Saved for Later)

### 11. **server list** (Read-Only)
**Complexity:** ⭐⭐ (Simple)  
**Risk:** Low - Just reads server state  
**Current:** `ServerCommand.ListCommand` via UnifiedCommandExecutor  
**Why Later:** Good for testing, but server commands are interconnected  
**Lines of Code:** ~100  
**Dependencies:** LuceeServerManager (read-only)

### 12. **server status [name]** (Read-Only)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Low - Checks if server is running  
**Current:** `ServerCommand.StatusCommand` via UnifiedCommandExecutor  
**Why Later:** Reads PID files, checks processes  
**Lines of Code:** ~100-150  
**Dependencies:** LuceeServerManager, process checking

### 13. **server stop [name]** (Process Control)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** High - Terminates running processes  
**Current:** `ServerCommand.StopCommand` via UnifiedCommandExecutor  
**Why Later:** Stops servers, can affect running apps  
**Lines of Code:** ~150-200  
**Dependencies:** LuceeServerManager, process control

### 14. **server start [options]** (Process Control + Config)
**Complexity:** ⭐⭐⭐⭐⭐ (Very Complex)  
**Risk:** High - Starts servers, port binding, config generation  
**Current:** `ServerCommand.StartCommand` via UnifiedCommandExecutor  
**Why Last:** Most complex, generates configs, manages ports, agents  
**Lines of Code:** ~300-500  
**Dependencies:** LuceeServerManager, TomcatConfigGenerator, port checking, JMX

### 15. **server restart [name]** (Process Control)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** High - Stop + Start combo  
**Current:** `ServerCommand.RestartCommand` via UnifiedCommandExecutor  
**Why Later:** Combines stop + start complexity  
**Lines of Code:** ~100 (delegates to stop/start)  
**Dependencies:** Server stop + start

### 16. **server prune** (Cleanup)
**Complexity:** ⭐⭐⭐ (Medium)  
**Risk:** Medium - Removes stopped server directories  
**Current:** `ServerCommand.PruneCommand` via UnifiedCommandExecutor  
**Why Later:** Deletes server state  
**Lines of Code:** ~100-150  
**Dependencies:** LuceeServerManager, file system

### 17. **server config get/set** (Configuration)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** Medium-High - Modifies lucee.json  
**Current:** `ServerCommand.ConfigCommand` via UnifiedCommandExecutor  
**Why Later:** JSON parsing, validation, config merging  
**Lines of Code:** ~200-300  
**Dependencies:** Jackson, ServerConfigHelper, file system

### 18. **server monitor** (Interactive Display)
**Complexity:** ⭐⭐⭐⭐⭐ (Very Complex)  
**Risk:** Medium - Interactive JMX dashboard  
**Current:** `MonitorCommand` via UnifiedCommandExecutor  
**Why Last:** Real-time updates, JMX, ASCII graphics  
**Lines of Code:** ~400-500  
**Dependencies:** JMX, CliDashboard, real-time updates

### 19. **server log [options]** (Log Display)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** Low - Reads and displays logs  
**Current:** `LogCommand` via UnifiedCommandExecutor  
**Why Later:** Tail follow mode, file watching  
**Lines of Code:** ~200-300  
**Dependencies:** File watching, tail -f logic

### 20. **server debug** (Debugging)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** Medium - Dumps server state  
**Current:** Via UnifiedCommandExecutor  
**Why Later:** Reads various config files  
**Lines of Code:** ~100-200  
**Dependencies:** File system, config parsers

---

## 🟣 Tier 5: Module Execution (Special Cases)

### 21. **modules run <module> [args]** (Code Execution)
**Complexity:** ⭐⭐⭐⭐ (Complex)  
**Risk:** Medium-High - Executes user CFML code  
**Current:** `ModulesCommand.RunCommand` via LuceeScriptEngine  
**Why Later:** Invokes Lucee engine, runs arbitrary code  
**Lines of Code:** ~200-300  
**Dependencies:** LuceeScriptEngine, module resolution

---

## Recommended Migration Order

Based on **risk**, **complexity**, and **learning value**:

1. ✅ **help** - Get familiar with pattern (if not already done)
2. ✅ **version** - Reinforce pattern
3. ✅ **lucee-version** - Similar to version
4. **completion** - Verify existing structure
5. ✅ **modules list** - First real read operation (COMPLETE)
6. **modules info** - Similar pattern to list
7. **modules init** - First write operation
8. **modules install** - Network + files
9. **modules update** - Similar to install
10. **modules uninstall** - Delete operations

**STOP HERE AND EVALUATE**

After completing modules commands:
- Pattern is proven
- Read, write, network, delete all tested
- Confidence is high
- Can tackle server commands if needed

---

## Why Avoid Server Commands Initially?

1. **Interconnected** - Start/stop/restart depend on each other
2. **State Management** - Complex server lifecycle tracking
3. **Process Control** - Can affect running applications
4. **Configuration Complexity** - Many options, environments, agents
5. **Hard to Test** - Requires actual server instances
6. **Higher Stakes** - Mistakes can break running servers

Start with modules → Lower risk, simpler testing, faster iteration

---

## Success Criteria for Each Command

Before marking complete:
- [ ] Command works in CLI mode (`lucli <command>`)
- [ ] Command works in terminal mode (interactive)
- [ ] All existing tests pass
- [ ] New tests added if appropriate
- [ ] Error handling tested
- [ ] Documentation updated if needed
- [ ] No UnifiedCommandExecutor dependency

---

## Pattern Template

```java
@Command(name = "command-name", description = "What it does")
public class CommandNameCommandImpl implements Callable<Integer> {
    
    @Parameters(description = "Parameters")
    private String[] params;
    
    @Option(names = {"-f", "--flag"}, description = "Optional flag")
    private boolean flag;
    
    @Override
    public Integer call() {
        // Direct implementation
        // No UnifiedCommandExecutor
        // Handle both CLI and terminal modes the same way
        
        try {
            // Your logic here
            System.out.println("Output");
            return 0; // Success
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1; // Failure
        }
    }
}
```

---

## Notes

- **Terminal-specific commands** (cd, ls, pwd, etc.) stay in `CommandProcessor` - they're not PicocLI commands
- **CFML execution** already migrated (CfmlCommand)
- **Modules run** can wait - it's complex and less used
- Focus on **modules list/info/init** first - best learning curve
