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
            ModulesCommand.InitCommand.class,
            ModulesCommand.RunCommand.class,
            ModulesCommand.InstallCommand.class,
            ModulesCommand.UninstallCommand.class,
            ModulesCommand.UpdateCommand.class
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

        @Option(names = "--git", description = "Initialize a git repository in the new module directory")
        private boolean git;

        @Option(names = "--no-git", description = "Do not initialize git and do not prompt")
        private boolean noGit;

        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("init");
            args.add(moduleName);
            if (git) {
                args.add("--git");
            }
            if (noGit) {
                args.add("--no-git");
            }

            // Execute the modules init command
            String result = executor.executeCommand("modules", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }

            return 0;
        }
    }

    @Command(
        name = "install",
        description = "Install a module"
    )
    static class InstallCommand implements Callable<Integer> {
        @Parameters(paramLabel = "MODULE_NAME", description = "Name of module to install")
        private String moduleName;
        
        @Option(names = {"-u", "--url"}, description = "Git URL to install from (e.g. https://github.com/user/repo.git[#ref]]")
        private String gitUrl;
        
        @Override
        public Integer call() throws Exception {
            // Create UnifiedCommandExecutor for CLI mode
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("install");
            if (moduleName != null) {
                args.add(moduleName);
            }
            if (gitUrl != null && !gitUrl.isEmpty()) {
                args.add("--url");
                args.add(gitUrl);
            }

            String result = executor.executeCommand("modules", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
            return 0;
        } 
    }   
 
 
    @Command(
        name = "uninstall",
        description = "Uninstall (remove) a module"
    )
    static class UninstallCommand implements Callable<Integer> {

        @Parameters(paramLabel = "MODULE_NAME", description = "Name of module to uninstall")
        private String moduleName;

        @Override
        public Integer call() throws Exception {
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("uninstall");
            if (moduleName != null && !moduleName.isEmpty()) {
                args.add(moduleName);
            }

            String result = executor.executeCommand("modules", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
            return 0;
        }
    }

    @Command(
        name = "update",
        description = "Update a module from git"
    )
    static class UpdateCommand implements Callable<Integer> {

        @Parameters(paramLabel = "MODULE_NAME", description = "Name of module to update")
        private String moduleName;

        @Option(names = {"-u", "--url"}, description = "Git URL to update from (e.g. https://github.com/user/repo.git[#ref]]")
        private String gitUrl;

        @Option(names = {"-f", "--force"}, description = "Overwrite existing module if it already exists")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, Paths.get(System.getProperty("user.dir")));

            java.util.List<String> args = new java.util.ArrayList<>();
            args.add("update");
            if (moduleName != null && !moduleName.isEmpty()) {
                args.add(moduleName);
            }
            if (gitUrl != null && !gitUrl.isEmpty()) {
                args.add("--url");
                args.add(gitUrl);
            }
            if (force) {
                args.add("--force");
            }

            String result = executor.executeCommand("modules", args.toArray(new String[0]));
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
            return 0;
        }
    }

     /**
      * Modules run subcommand
     * TODO: We should be able to add subcommands here, so for exampple
     * lucli lint export arg=1 arg2=etc
     */
    @Command(
        name = "run", 
        description = "Run a module"
    )
    static class RunCommand implements Callable<Integer> {

        @ParentCommand 
        private ModulesCommand parent;

        @Parameters(index = "0",
                    paramLabel = "MODULE_NAME", 
                    description = "Name of the module to run")
        private String moduleName;

        // Collect all remaining args, including unknown options, and pass them through to the module
        @picocli.CommandLine.Unmatched
        private java.util.List<String> moduleArgs = new java.util.ArrayList<>();

        @Override
        public Integer call() throws Exception {
            // Access flags from grandparent (root) command via parent
            // parent.parent is the LuCLICommand instance
            LuCLICommand rootCommand = parent.parent;
            
            // Set global flags from root command
            LuCLI.verbose = rootCommand.isVerbose();
            LuCLI.debug = rootCommand.isDebug();
            LuCLI.timing = rootCommand.isTiming();
            
            // Initialize timing if requested
            Timer.setEnabled(rootCommand.isTiming());
            Timer.start("Module Execution");
            


            


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

            try {
                // Execute the modules run command
                String result = executor.executeCommand("modules", args.toArray(new String[0]));
                if (result != null && !result.isEmpty()) {
                    System.out.println(result);
                }

                return 0;
            } finally {
                // Always stop timer and show results before exit (if timing enabled)
                Timer.stop("Module Execution");
                Timer.printResults();
            }
        }
    }
}
