package org.lucee.lucli;

import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.ParseResult;

/**
 * Custom PicocLI execution strategy that automatically wraps command execution with timing.
 * This ensures every command gets timed if timing is enabled, without manual timer calls.
 */
public class TimingExecutionStrategy implements CommandLine.IExecutionStrategy {
    
    private final CommandLine.IExecutionStrategy defaultStrategy = new CommandLine.RunLast();
    
    @Override
    public int execute(ParseResult parseResult) throws ExecutionException {
        // Only time if timing is enabled (Timer singleton is configured in LuCLI.main())
        if (!Timer.isEnabled()) {
            return defaultStrategy.execute(parseResult);
        }
        
        // Get the command name for timing
        String commandName = getCommandName(parseResult);
        
        // Start timer for this command
        Timer.start(commandName);
        
        try {
            // Execute the command using default strategy
            return defaultStrategy.execute(parseResult);
        } finally {
            // Always stop the timer
            Timer.stop(commandName);
        }
    }
    
    /**
     * Extract a readable command name from the parse result
     */
    private String getCommandName(ParseResult parseResult) {
        StringBuilder commandPath = new StringBuilder();
        
        ParseResult current = parseResult;
        while (current != null) {
            CommandLine.Model.CommandSpec spec = current.commandSpec();
            String name = spec.name();
            
            if (commandPath.length() > 0) {
                commandPath.append(" ");
            }
            commandPath.append(name);
            
            current = current.subcommand();
        }
        
        return commandPath.length() > 0 ? commandPath.toString() : "unknown-command";
    }
}
