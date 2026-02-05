package org.lucee.lucli.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Terminal;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.Timer;
import org.lucee.lucli.cli.commands.CfmlCommand;
import org.lucee.lucli.cli.commands.CompletionCommand;
import org.lucee.lucli.cli.commands.ModulesCommand;
import org.lucee.lucli.cli.commands.RunCommand;
import org.lucee.lucli.cli.commands.ParrotCommand;
import org.lucee.lucli.cli.commands.SecretsCommand;
import org.lucee.lucli.cli.commands.ServerCommand;
import org.lucee.lucli.cli.commands.VersionsListCommand;
import org.lucee.lucli.cli.commands.XmlCommand;
import org.lucee.lucli.cli.commands.DaemonCommand;
import org.lucee.lucli.cli.commands.deps.DepsCommand;
import org.lucee.lucli.cli.commands.deps.InstallCommand;
import org.lucee.lucli.cli.commands.logic.IfCommand;
import org.lucee.lucli.cli.commands.logic.XSetCommand;
import org.lucee.lucli.modules.ModuleCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Main CLI command class using Picocli framework
 * Defines the root command interface and delegates to appropriate subcommands or modes
 */
@Command(
    name = "lucli",
    description = "🚀 LuCLI - A terminal application with Lucee CFML integration",
    mixinStandardHelpOptions = true,
    versionProvider = LuCLIVersionProvider.class,
    subcommands = {
        ServerCommand.class,
        ModulesCommand.class,
        DepsCommand.class,
        InstallCommand.class,  // Shortcut for deps install
        CfmlCommand.class,
        CompletionCommand.class,
        VersionsListCommand.class,
        ParrotCommand.class,
        SecretsCommand.class,
        CommandLine.HelpCommand.class,
        RunCommand.class,
        DaemonCommand.class,
        // Hidden/internal diagnostics
        XmlCommand.class,
        XSetCommand.class,
        IfCommand.class
    },
    footer = {
        "",
        "Configuration:",
        "  Lucee server files are stored in ~/.lucli/lucee-server by default",
        "  Override with LUCLI_HOME environment variable or -Dlucli.home system property",
        "",
        "Examples:",
        "  lucli                           # Start interactive terminal",
        "  lucli --verbose --version       # Show version with verbose output", 
        "  lucli --timing script.cfs       # Execute script with timing analysis",
        "  lucli script.cfs arg1 arg2      # Execute CFML script with arguments",
        "  lucli cfml 'now()'              # Execute CFML expression",
        "  lucli server start --version 6.2.2.91  # Start server with specific Lucee version",
        "  lucli modules list              # List available modules"
    }
)
public class LuCLICommand implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, 
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = {"-d", "--debug"}, 
            description = "Enable debug output") 
    private boolean debug = false;

    @Option(names = {"-t", "--timing"}, 
            description = "Enable timing output for performance analysis")
    private boolean timing = false;

    @Option(names = {"-w", "--whitespace"}, 
            description = "Preserve whitespace in script output")
    private boolean preserveWhitespace = true;

    @Option(names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit")
    private boolean helpRequested = false;

    @Option(names = {"--version"}, 
            description = "Show application version",
            versionHelp = true)
    private boolean versionRequested = false;

    @Option(names = {"--lucee-version"}, 
            description = "Show Lucee version")
    private boolean luceeVersionRequested = false;

    @Option(
        names = {"--timeout"},
        paramLabel = "<seconds>",
        description = "Fail if the command runs longer than this many seconds (0 = no timeout)",
        defaultValue = "0"
    )
    private int timeoutSeconds;

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "FILE_OR_MODULE",
        description = "Optional: CFML/LuCLI file to execute, or module name to run"
    )
    private String firstArg;

    @Parameters(
        index = "1..*",
        arity = "0..*",
        paramLabel = "ARGS",
        description = "Additional arguments to pass to the file or module"
    )
    private String[] additionalArgs = new String[0];

    @Spec
    private CommandSpec spec;

    /**
     * Main entry point when root command is executed
     */
    @Override
    public Integer call() throws Exception {
        // Set global flags for backward compatibility
        LuCLI.verbose = verbose;
        LuCLI.debug = debug;
        LuCLI.timing = timing;
        LuCLI.preserveWhitespace = preserveWhitespace;

        // Initialize timing if requested
        Timer.setEnabled(timing);
        Timer.start("Total Execution");

        try {
            // Handle special version command
            if (luceeVersionRequested) {
                showLuceeVersionNonInteractive();
                return 0;
            }

            // No argument provided - start interactive terminal
            if (firstArg == null || firstArg.trim().isEmpty()) {
                LuCLI.printDebug("LuCLICommand", "No arguments provided, starting terminal mode");
                Timer.start("Terminal Mode");
                Terminal.main(new String[0]);
                Timer.stop("Terminal Mode");
                return 0;
            }

            // Route based on what firstArg is
            return routeCommand(firstArg, additionalArgs);

        } catch (Exception e) {
            StringOutput.Quick.error("Error: " + e.getMessage());
            if (verbose || debug) {
                e.printStackTrace();
            }
            return 1;
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("Total Execution");
        }
    }

    /**
     * Route a command based on the first argument (file or module name)
     */
    private Integer routeCommand(String arg, String[] args) throws Exception {
        File file = new File(arg);

        // Check if it's a .lucli script file
        if (file.exists() && (arg.endsWith(".lucli") || arg.endsWith(".luc"))) {
            LuCLI.printDebug("LuCLICommand", "Routing to .lucli script: " + arg);
            Timer.start("LuCLI Script Execution");
            int exitCode = LuCLI.executeLucliScript(arg);
            Timer.stop("LuCLI Script Execution");
            return exitCode;
        }

        // Check if it's a CFML file (.cfm, .cfc, .cfs)
        if (file.exists() && (arg.endsWith(".cfm") || arg.endsWith(".cfc") || 
                              arg.endsWith(".cfs") || arg.endsWith(".cfml"))) {
            LuCLI.printDebug("LuCLICommand", "Routing to run command: " + arg);
            return executeViaRunCommand(arg, args);
        }

        // Check if it's a module name (and not an existing file)
        if (!file.exists() && ModuleCommand.moduleExists(arg)) {
            LuCLI.printDebug("LuCLICommand", "Routing to module: " + arg);
            return executeViaModulesCommand(arg, args);
        }

        // Unknown - throw error with helpful message
        throw new CommandLine.ParameterException(
            spec.commandLine(),
            "Unknown command, file, or module: '" + arg + "'\n" +
            "  - If it's a file, check the path and extension (.cfm, .cfc, .cfs, .lucli)\n" +
            "  - If it's a module, run 'lucli modules list' to see available modules\n" +
            "  - Run 'lucli --help' to see available commands"
        );
    }

    /**
     * Execute a file via the run command
     */
    private Integer executeViaRunCommand(String filePath, String[] args) throws Exception {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("run");
        cmdArgs.add(filePath);
        if (args != null && args.length > 0) {
            cmdArgs.addAll(Arrays.asList(args));
        }
        return spec.commandLine().execute(cmdArgs.toArray(new String[0]));
    }

    /**
     * Execute a module via the modules run command
     */
    private Integer executeViaModulesCommand(String moduleName, String[] args) throws Exception {
        LuCLI.printVerbose("Executing module shortcut: " + moduleName + 
            " (equivalent to 'lucli modules run " + moduleName + "')");
        
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("modules");
        cmdArgs.add("run");
        cmdArgs.add(moduleName);
        if (args != null && args.length > 0) {
            cmdArgs.addAll(Arrays.asList(args));
        }
        return spec.commandLine().execute(cmdArgs.toArray(new String[0]));
    }


    /**
     * Show Lucee version in non-interactive mode
     */
    private void showLuceeVersionNonInteractive() {
        try {
            // This logic should be moved to a shared utility class eventually
            System.out.println("Initializing Lucee CFML engine...");
            
            // For now, delegate to Terminal's implementation
            Terminal.main(new String[]{"--lucee-version"});
            
        } catch (Exception e) {
            System.err.println("Error getting Lucee version: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Getter methods for subcommands to access flags
     */
    public boolean isVerbose() {
        return verbose;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public boolean isTiming() {
        return timing;
    }
    
    public boolean isPreserveWhitespace() {
        return preserveWhitespace;
    }
}
