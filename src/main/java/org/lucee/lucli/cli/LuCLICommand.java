package org.lucee.lucli.cli;

import java.util.concurrent.Callable;

import org.lucee.lucli.InteractiveTerminalV2;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.Timer;
import org.lucee.lucli.cli.commands.CfmlCommand;
import org.lucee.lucli.cli.commands.CompletionCommand;
import org.lucee.lucli.cli.commands.EchoCommand;
import org.lucee.lucli.cli.commands.ModulesCommand;
import org.lucee.lucli.cli.commands.ServerCommand;
import org.lucee.lucli.cli.commands.VersionsListCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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
        CfmlCommand.class,
        CompletionCommand.class,
        VersionsListCommand.class,
        EchoCommand.class,
        CommandLine.HelpCommand.class
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

    // Note: Parameters removed to prevent conflict with subcommands
    // Script execution will be handled differently

    /**
     * Main entry point when root command is executed
     */
    @Override
    public Integer call() throws Exception {
        // Set global flags for backward compatibility
        LuCLI.verbose = verbose;
        LuCLI.debug = debug;
        LuCLI.timing = timing;

        // Initialize timing if requested
        Timer.setEnabled(timing);
        Timer.start("Total Execution");

        try {
            // Configure Lucee directories BEFORE any Lucee initialization
            

            // Handle special version command
            if (luceeVersionRequested) {
                showLuceeVersionNonInteractive();
                return 0;
            }

            // If we get here, no subcommand was matched, so start interactive terminal mode
            // Use the Picocli-integrated terminal so CLI and terminal share the same
            // command definitions, options, and autocomplete.
            Timer.start("Terminal Mode");
            InteractiveTerminalV2.main(new String[0]);
            Timer.stop("Terminal Mode");

            return 0;

        } catch (Exception e) {
            StringOutput.Quick.error("Error: " + e.getMessage());
            if (verbose || debug) {
                e.printStackTrace();
            }
            return 1;
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("Total Execution");
            Timer.printResults();
        }
    }


    /**
     * Show Lucee version in non-interactive mode
     */
    private void showLuceeVersionNonInteractive() {
        try {
            // This logic should be moved to a shared utility class eventually
            System.out.println("Initializing Lucee CFML engine...");
            
            // For now, delegate to InteractiveTerminal's implementation
            InteractiveTerminalV2.main(new String[]{"--lucee-version"});
            
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

}
