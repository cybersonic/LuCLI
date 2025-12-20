package org.lucee.lucli.cli.commands;

import java.util.concurrent.Callable;

import org.lucee.lucli.modules.ModuleCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules update command
 */
@Command(
    name = "update",
    description = "Update a module from git"
)
public class ModulesUpdateCommandImpl implements Callable<Integer> {

    @Parameters(
        paramLabel = "MODULE_NAME",
        description = "Name of module to update"
    )
    private String moduleName;

    @Option(
        names = {"-u", "--url"},
        description = "Git URL to update from (e.g. https://github.com/user/repo.git[#ref]]"
    )
    private String gitUrl;

    @Option(
        names = {"-f", "--force"},
        description = "Overwrite existing module if it already exists"
    )
    private boolean force;

    @Override
    public Integer call() throws Exception {
        // Call updateModule directly - no arg parsing needed
        ModuleCommand.updateModule(moduleName, gitUrl, force);
        return 0;
    }
}
