package org.lucee.lucli.cli.commands;

import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Timer;
import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.commands.UnifiedCommandExecutor; // Still used by other subcommands

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import org.lucee.lucli.cli.completion.DynamicArgumentCompletion;

/**
 * Module management command using Picocli
 */
    @Command(
        name = "modules",
        aliases = {"module"},
        description = "Manage LuCLI modules",
        subcommands = {
            ModulesListCommandImpl.class,
            ModulesInitCommandImpl.class,
            ModulesRunCommandImpl.class,
            ModulesInstallCommandImpl.class,
            ModulesUninstallCommandImpl.class,
            ModulesUpdateCommandImpl.class
        }

)
public class ModulesCommand implements Callable<Integer> {

    @ParentCommand 
    private LuCLICommand parent;

    @Override
    public Integer call() throws Exception {
        // If modules command is called without subcommand, show help
        new picocli.CommandLine(this).usage(System.out);
        return 0;
    }

    // Modules list subcommand now in ModulesListCommandImpl
    // Modules init subcommand now in ModulesInitCommandImpl

    // Modules install subcommand now in ModulesInstallCommandImpl
    // Modules uninstall subcommand now in ModulesUninstallCommandImpl
    // Modules update subcommand now in ModulesUpdateCommandImpl

    // Modules run subcommand now in ModulesRunCommandImpl
}
