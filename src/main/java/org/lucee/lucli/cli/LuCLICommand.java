package org.lucee.lucli.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.InteractiveTerminal;
import org.lucee.lucli.Timer;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.cli.commands.ServerCommand;
import org.lucee.lucli.cli.commands.ModulesCommand;
import org.lucee.lucli.cli.commands.CfmlCommand;

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
            Timer.start("Configure Lucee Directories");
            configureLuceeDirectories();
            Timer.stop("Configure Lucee Directories");

            // Handle special version command
            if (luceeVersionRequested) {
                showLuceeVersionNonInteractive();
                return 0;
            }

            // If we get here, no subcommand was matched, so start interactive terminal mode
            Timer.start("Terminal Mode");
            InteractiveTerminal.main(new String[0]);
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
            InteractiveTerminal.main(new String[]{"--lucee-version"});
            
        } catch (Exception e) {
            System.err.println("Error getting Lucee version: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Configure Lucee directories (copied from original LuCLI.java)
     */
    private void configureLuceeDirectories() throws java.io.IOException {
        // Allow customization of lucli home via environment variable or system property
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = java.nio.file.Paths.get(userHome, ".lucli").toString();
        }
        
        java.nio.file.Path lucliHome = java.nio.file.Paths.get(lucliHomeStr);
        java.nio.file.Path luceeServerDir = lucliHome.resolve("lucee-server");
        java.nio.file.Path patchesDir = lucliHome.resolve("patches");
        
        // Create all necessary directories if they don't exist
        java.nio.file.Files.createDirectories(luceeServerDir);
        java.nio.file.Files.createDirectories(patchesDir);
        
        if (verbose || debug) {
            System.out.println(StringOutput.msg("config.lucee.directories"));
            System.out.println("  " + StringOutput.msg("config.lucli.home", lucliHome.toString()));
            System.out.println("  " + StringOutput.msg("config.lucee.server", luceeServerDir.toString()));
            System.out.println("  " + StringOutput.msg("config.patches", patchesDir.toString()));
        }

        // Set Lucee system properties
        System.setProperty("lucee.base.dir", luceeServerDir.toString());
        System.setProperty("lucee.server.dir", luceeServerDir.toString());
        System.setProperty("lucee.web.dir", luceeServerDir.toString());
        System.setProperty("lucee.patch.dir", patchesDir.toString());
        System.setProperty("lucee.controller.disabled", "true"); // Disable web controller for CLI
        
        // Ensure Lucee doesn't try to create default directories in system paths
        System.setProperty("lucee.controller.disabled", "true");
        System.setProperty("lucee.use.lucee.configs", "false");
    }
}