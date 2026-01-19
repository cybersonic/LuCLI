package org.lucee.lucli.cli.commands.deps;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main deps command for dependency management
 * Aliases: deps, dependencies
 */
@Command(
    name = "deps",
    aliases = {"dependencies"},
    description = "Manage project dependencies",
    subcommands = {
        InstallCommand.class,
        AddCommand.class,
        PruneCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class DepsCommand implements Runnable {
    
    @Override
    public void run() {
        // Show help if no subcommand provided
        CommandLine.usage(this, System.out);
    }
}
