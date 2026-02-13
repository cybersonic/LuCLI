package org.lucee.lucli.cli.commands.modules;

import java.util.concurrent.Callable;

import org.lucee.lucli.StringOutput;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Direct implementation of modules run command - executes a module with arguments
 */
@Command(
    name = "help",
    description = "Show help for a module",
    mixinStandardHelpOptions = true
)
public class ModuleHelpCommandImpl  implements Callable<Integer>{

    @Option(
        names = {"-m", "--module"},
        description = "Name of the module to show help for",
        required = true
    )
    String moduleName;
    @Option(
        names = {"-c", "--command"},
        description = "Name of the command to show help for",
        required = false
    )
    String commandName = null;

    @Override
    public Integer call() throws Exception {
        StringOutput.msg("Showing help for" + moduleName + " and command " + commandName);
        return 0;
    }
}
