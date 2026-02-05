package org.lucee.lucli.cli;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Timer;

import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.ParseResult;

/**
 * Composite execution strategy that handles:
 * 1. Setting global flags from parsed command options
 * 2. Timing command execution when --timing flag is enabled
 * 
 * This strategy walks the command hierarchy to find the root LuCLI command
 * and extracts global flags (verbose, debug, timing, preserveWhitespace) to
 * maintain backward compatibility with code that checks static LuCLI fields.
 */
public class LuCLIExecutionStrategy implements IExecutionStrategy {
    
    private final IExecutionStrategy delegate;
    
    public LuCLIExecutionStrategy() {
        // Use picocli's default execution strategy (RunLast)
        this.delegate = new CommandLine.RunLast();
    }
    
    @Override
    public int execute(ParseResult parseResult) throws ExecutionException {
        // Extract and set global flags from the root command
        setGlobalFlagsFromParseResult(parseResult);
        
        // Get command name for timing
        String commandName = parseResult.commandSpec().name();
        
        // Execute with timing if enabled
        if (LuCLI.timing) {
            Timer.start(commandName);
        }
        
        try {
            return delegate.execute(parseResult);
        } finally {
            if (LuCLI.timing) {
                Timer.stop(commandName);
            }
        }
    }
    
    /**
     * Extract global flags from the parse result and set them in LuCLI static fields.
     * This maintains backward compatibility with code that checks LuCLI.verbose, etc.
     */
    private void setGlobalFlagsFromParseResult(ParseResult parseResult) {
        // The root command is always at the top of the hierarchy
        // Walk backwards to find the root LuCLI
        ParseResult root = parseResult;
        
        // Find the topmost parse result (root)
        while (root.commandSpec().parent() != null) {
            root = root.commandSpec().parent().commandLine().getParseResult();
            if (root == null) break;
        }
        
        // Extract flags from root command
        if (root != null) {
            Object userObject = root.commandSpec().userObject();
            if (userObject instanceof LuCLI) {
                LuCLI rootCmd = (LuCLI) userObject;
                LuCLI.verbose = rootCmd.isVerbose();
                LuCLI.debug = rootCmd.isDebug();
                LuCLI.timing = rootCmd.isTiming();
                LuCLI.preserveWhitespace = rootCmd.isPreserveWhitespace();
                Timer.setEnabled(rootCmd.isTiming());
            }
        }
    }
}
