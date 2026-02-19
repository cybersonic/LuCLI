package org.lucee.lucli.cli.commands.modules;

import java.util.concurrent.Callable;

import org.lucee.lucli.modules.ModuleCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules install command
 */
@Command(
    name = "install",
    description = "Install a module"
)
public class ModulesInstallCommandImpl implements Callable<Integer> {

    @Parameters(
        paramLabel = "MODULE_NAME",
        description = "Name of module to install",
        arity = "0..1"
    )
    private String moduleName;

    @Option(
        names = {"-u", "--url"},
        description = "Git URL to install from (e.g. https://github.com/user/repo.git[#ref]]"
    )
    private String gitUrl;

    @Option(
        names = {"-f", "--force"},
        description = "Overwrite existing module if it already exists"
    )
    private boolean force;

    @Override
    public Integer call() throws Exception {
        // Call installModule directly - no arg parsing needed
        ModuleCommand.installModule(moduleName, gitUrl, force);
        return 0;
    }
}
