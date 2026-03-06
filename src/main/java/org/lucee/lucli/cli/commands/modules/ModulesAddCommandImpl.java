package org.lucee.lucli.cli.commands.modules;

import java.util.concurrent.Callable;

import org.lucee.lucli.modules.ModuleCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Direct implementation of modules add command.
 * Alias semantics for install, with explicit --ref support.
 */
@Command(
    name = "add",
    description = "Add (install) a module"
)
public class ModulesAddCommandImpl implements Callable<Integer> {

    @Parameters(
        paramLabel = "MODULE_NAME",
        description = "Name of module to add",
        arity = "0..1"
    )
    private String moduleName;

    @Option(
        names = {"-u", "--url"},
        description = "Git URL to add from (e.g. https://github.com/user/repo.git)"
    )
    private String gitUrl;

    @Option(
        names = {"-r", "--ref", "--rev"},
        description = "Git ref to install (branch, tag, or commit)"
    )
    private String ref;
    @Option(
        names = {"-n", "--name"},
        description = "Install alias/name to use locally (overrides module.json name)"
    )
    private String installName;

    @Option(
        names = {"-f", "--force"},
        description = "Overwrite existing module if it already exists"
    )
    private boolean force;

    @Override
    public Integer call() throws Exception {
        ModuleCommand.installModule(moduleName, installName, gitUrl, ref, force);
        return 0;
    }
}
