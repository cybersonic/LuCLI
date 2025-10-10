package org.lucee.lucli.cli.commands;

import java.nio.file.Paths;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.commands.UnifiedCommandExecutor;

/**
 * Module management command using Picocli
 */
@Command(
    name = "modules",
    description = "Manage LuCLI modules",
    subcommands = {
        ModulesCommand.ListCommand.class,
        ModulesCommand.InitCommand.class,
        ModulesCommand.RunCommand.class
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

    /**
     * Modules list subcommand
     */
    @Command(
        name = "list", 
        description = "List available modules"
    )
    static class ListCommand implements Callable<Integer> {

        @ParentCommand 
        private ModulesCommand parent;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Execute the modules list command
            String result = executor.executeCommand("modules", new String[]{"list"});
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Modules init subcommand
     */
    @Command(
        name = "init", 
        description = "Initialize a new module"
    )
    static class InitCommand implements Callable<Integer> {

        @ParentCommand 
        private ModulesCommand parent;

        @Parameters(paramLabel = "MODULE_NAME", 
                    description = "Name of the module to initialize")
        private String moduleName;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Execute the modules init command
            String result = executor.executeCommand("modules", new String[]{"init", moduleName});
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    /**
     * Modules run subcommand
     */
    @Command(
        name = "run", 
        description = "Run a module"
    )
    static class RunCommand implements Callable<Integer> {

        @ParentCommand 
        private ModulesCommand parent;

        @Parameters(paramLabel = "MODULE_NAME", 
                    description = "Name of the module to run")
        private String moduleName;

        @Parameters(paramLabel = "ARGS", 
                    description = "Arguments to pass to the module",
                    arity = "0..*")
        private String[] moduleArgs = new String[0];

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            // Build arguments array
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("run");
            args.add(moduleName);
            
            // Add module arguments
            if (moduleArgs != null) {
                for (String arg : moduleArgs) {
                    args.add(arg);
                }
            }

            // Execute the modules run command
            String result = executor.executeCommand("modules", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }
}