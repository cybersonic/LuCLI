---
title: Server Command Development
layout: docs
---

# Adding New Server Subcommands

This guide explains how to add a new subcommand to the `server` command in LuCLI. The implementation uses a two-layer architecture:

1. **CLI Layer** (`ServerCommand.java`) - Picocli command definition
2. **Execution Layer** (`UnifiedCommandExecutor.java`) - Command implementation

## Architecture Overview

The server command uses a facade pattern where:
- `ServerCommand` defines the CLI structure using Picocli annotations
- Each subcommand is a nested static class implementing `Callable<Integer>`
- `UnifiedCommandExecutor` contains the actual implementation logic
- This design ensures feature parity between CLI and terminal modes

## Step-by-Step: Adding a New Server Subcommand

Let's walk through adding a hypothetical `backup` subcommand as an example.

### Step 1: Create the Subcommand Class in ServerCommand.java

In `src/main/java/org/lucee/lucli/cli/commands/ServerCommand.java`, add a new static class after existing subcommands:

```java
/**
 * Server backup subcommand
 */
@Command(
    name = "backup", 
    description = "Backup a server instance"
)
static class BackupCommand implements Callable<Integer> {

    @ParentCommand 
    private ServerCommand parent;

    @Option(names = {\"-n\", \"--name\"}, 
            description = \"Name of the server instance to backup\")
    private String name;

    @Option(names = {\"-o\", \"--output\"}, 
            description = \"Output directory for backup\")
    private String outputDir;

    @Override
    public Integer call() throws Exception {
        // Create UnifiedCommandExecutor for CLI mode
        UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty(\"user.dir\")));

        // Build arguments array
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(\"backup\");
        
        if (name != null) {
            args.add(\"--name\");
            args.add(name);
        }
        if (outputDir != null) {
            args.add(\"--output\");
            args.add(outputDir);
        }

        // Execute the server backup command
        String result = executor.executeCommand(\"server\", args.toArray(new String[0]));
        if (result != null && !result.isEmpty()) {
            System.out.println(result);
        }

        return 0;
    }
}
```

**Key points:**
- Use `@Command` annotation with `name` (CLI name) and `description`
- Extend `Callable<Integer>` and implement `call()`
- Include `@ParentCommand` reference for Picocli hierarchy
- Use `@Option` for command flags with both short and long names
- Use `@Parameters` for positional arguments (if needed)
- Always create `UnifiedCommandExecutor` and delegate to it
- Match the argument pattern used by other commands

### Step 2: Register the Subcommand in ServerCommand

Add your new subcommand class to the `@Command` annotation's `subcommands` list:

```java
@Command(
    name = "server",
    description = "Manage Lucee server instances",
    subcommands = {
        ServerCommand.StartCommand.class,
        ServerCommand.StopCommand.class,
        ServerCommand.RestartCommand.class,
        ServerCommand.BackupCommand.class,  // Add here
        ServerCommand.StatusCommand.class,
        ServerCommand.ListCommand.class,
        ServerCommand.LogCommand.class,
        ServerCommand.MonitorCommand.class
    }
)
```

### Step 3: Add Handler Method in UnifiedCommandExecutor

In `src/main/java/org/lucee/lucli/commands/UnifiedCommandExecutor.java`, add a case in the `executeServerCommand` switch statement:

```java
private String executeServerCommand(String[] args) throws Exception {
    if (args.length == 0) {
        return formatOutput("❌ server: missing subcommand\n💡 Usage: server [start|stop|restart|backup|status|list|prune|monitor|log|debug] [options]", true);
    }
    
    String subCommand = args[0];
    LuceeServerManager serverManager = new LuceeServerManager();
    
    Timer.start("Server " + subCommand + " Command");
    
    try {
        switch (subCommand) {
            case "start":
                return handleServerStart(serverManager, args);
            case "stop":  
                return handleServerStop(serverManager, args);
            case "restart":
                return handleServerRestart(serverManager, args);
            case "backup":                    // Add here
                return handleServerBackup(serverManager, args);
            case "status":
                return handleServerStatus(serverManager, args);
            // ... rest of cases
            default:
                return formatOutput("❌ Unknown server command: " + subCommand + 
                    "\n💡 Available commands: start, stop, restart, backup, status, list, prune, config, monitor, log, debug", true);
        }
    } finally {
        Timer.stop("Server " + subCommand + " Command");
    }
}
```

### Step 4: Implement the Handler Method

Add the implementation method in `UnifiedCommandExecutor.java`:

```java
private String handleServerBackup(LuceeServerManager serverManager, String[] args) throws Exception {
    String serverName = null;
    String outputDir = null;
    
    // Parse arguments (skip "backup")
    for (int i = 1; i < args.length; i++) {
        if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
            serverName = args[i + 1];
            i++; // Skip next argument
        } else if ((args[i].equals("--output") || args[i].equals("-o")) && i + 1 < args.length) {
            outputDir = args[i + 1];
            i++; // Skip next argument
        }
    }
    
    StringBuilder result = new StringBuilder();
    
    if (serverName != null) {
        // Backup specific server by name
        if (!isTerminalMode) {
            result.append("Backing up server: ").append(serverName).append("\n");
        }
        // TODO: Implement backup logic using serverManager
        result.append("✅ Server '").append(serverName).append("' backed up successfully.");
    } else {
        // Backup server for current directory
        if (!isTerminalMode) {
            result.append("Backing up server for: ").append(currentWorkingDirectory).append("\n");
        }
        // TODO: Implement backup logic using serverManager
        result.append("✅ Server backed up successfully.");
    }
    
    return formatOutput(result.toString(), false);
}
```

**Implementation guidelines:**
- Extract and parse command-line arguments (skip first element which is the subcommand)
- Handle both named and current directory contexts
- Use `isTerminalMode` to adjust output verbosity
- Call `LuceeServerManager` methods for server operations
- Build result messages using `StringBuilder`
- Return formatted output using `formatOutput(message, isError)`

### Step 5: Update Help Text

Update the help messages to include your new command:

1. In `executeServerCommand()` at the top (usage message)
2. In the `default` case of the switch statement (available commands list)

## Testing Your New Subcommand

### CLI Mode
```bash
java -jar target/lucli.jar server backup --help
java -jar target/lucli.jar server backup --name my-server --output /tmp/backups
```

### Terminal Mode
```bash
java -jar target/lucli.jar
> server backup --help
> server backup --name my-server --output /tmp/backups
```

## Best Practices

1. **Naming Convention**: Use simple, imperative verb names (start, stop, backup, prune)
2. **Help Text**: Provide clear, concise descriptions for both command and options
3. **Consistency**: Follow the existing pattern for argument parsing and output formatting
4. **Error Handling**: Return meaningful error messages with suggestions
5. **Modes**: Always handle both CLI and terminal modes appropriately
6. **Logging**: Use `Timer` for performance monitoring
7. **Documentation**: Update WARP.md with new command examples if user-facing

## Common Patterns

### With Server Name
```java
if (serverName != null) {
    // Handle named server
} else {
    // Handle current directory server
}
```

### Optional Arguments
```java
@Option(names = {"-x\", \"--example\"}, required = false)
private String example;
```

### Boolean Flags
```java
@Option(names = {\"--force\", \"-f\"})
private boolean force = false;
```

### Positional Parameters
```java
@Parameters(paramLabel = \"[PROJECT_DIR]\", 
            description = \"Project directory\",
            arity = \"0..1\")
private String projectDir;
```

## Troubleshooting

- **Command not appearing in help**: Ensure it's added to the `subcommands` list in `@Command`
- **Arguments not parsed**: Check argument names match between `ServerCommand` and `UnifiedCommandExecutor`
- **Terminal mode shows nothing**: Remember to wrap output in `formatOutput()` for terminal mode
- **Type mismatches**: Ensure argument types are converted correctly (String to Integer, etc.)
