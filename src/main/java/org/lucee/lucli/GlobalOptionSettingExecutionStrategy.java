package org.lucee.lucli;

import picocli.CommandLine;

public class GlobalOptionSettingExecutionStrategy implements CommandLine.IExecutionStrategy {

    private final CommandLine.IExecutionStrategy defaultStrategy = new CommandLine.RunLast();

    @Override
    public int execute(CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
        // Set global flags based on parsed options
        LuCLI.verbose = parseResult.hasMatchedOption("verbose");
        LuCLI.debug = parseResult.hasMatchedOption("debug");
        LuCLI.timing = parseResult.hasMatchedOption("timing");
        LuCLI.preserveWhitespace = parseResult.hasMatchedOption("whitespace");

        // Proceed with default execution strategy
        return defaultStrategy.execute(parseResult);
    }

}
