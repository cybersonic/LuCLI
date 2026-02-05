package org.lucee.lucli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lucee.lucli.cli.LuCLIVersionProvider;
import org.lucee.lucli.cli.commands.CfmlCommand;
import org.lucee.lucli.cli.commands.CompletionCommand;
import org.lucee.lucli.cli.commands.DaemonCommand;
import org.lucee.lucli.cli.commands.ModulesCommand;
import org.lucee.lucli.cli.commands.ParrotCommand;
import org.lucee.lucli.cli.commands.RunCommand;
import org.lucee.lucli.cli.commands.SecretsCommand;
import org.lucee.lucli.cli.commands.ServerCommand;
import org.lucee.lucli.cli.commands.VersionsListCommand;
import org.lucee.lucli.cli.commands.XmlCommand;
import org.lucee.lucli.cli.commands.deps.DepsCommand;
import org.lucee.lucli.cli.commands.deps.InstallCommand;
import org.lucee.lucli.cli.commands.logic.IfCommand;
import org.lucee.lucli.cli.commands.logic.XSetCommand;
import org.lucee.lucli.modules.ModuleCommand;
import org.lucee.lucli.secrets.LocalSecretStore;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Main CLI command class using Picocli framework.
 * Defines the root command interface and delegates to appropriate subcommands or modes.
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
        "  lucli myscript.lucli            # Execute a LuCLI command script",
        "  lucli cfml 'now()'              # Execute CFML expression",
        "  lucli server start --version 6.2.2.91  # Start server with specific Lucee version",
        "  lucli modules list              # List available modules"
    }
)
public class LuCLI implements Callable<Integer> {

    // ====================
    // Picocli Options
    // ====================
    
    @Option(names = {"-v", "--verbose"}, 
            description = "Enable verbose output")
    private boolean verboseOption = false;

    @Option(names = {"-d", "--debug"}, 
            description = "Enable debug output") 
    private boolean debugOption = false;

    @Option(names = {"-t", "--timing"}, 
            description = "Enable timing output for performance analysis")
    private boolean timingOption = false;

    @Option(names = {"-w", "--whitespace"}, 
            description = "Preserve whitespace in script output")
    private boolean preserveWhitespaceOption = true;

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

    // ====================
    // Static Global State
    // ====================
    
    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;
    public static boolean preserveWhitespace = false;
    private static boolean lucliScript = false;
    
    public static Map<String, String> scriptEnvironment = new HashMap<>(System.getenv());
    
    /**
     * Commands that are treated as internal file-system style commands, routed
     * through {@link org.lucee.lucli.CommandProcessor} both in the interactive
     * terminal and in .lucli script execution.
     *
     * Exposed as a public constant so other components can share the same
     * definition instead of duplicating the list.
     */
    public static final Set<String> internalFileSystemCommands = Set.of(
        "ls", "dir",
        "cd", "pwd",
        "mkdir", "rmdir",
        "rm", "cp",
        "mv", "cat",
        "touch", "find",
        "wc", "head", "tail",
        "prompt", "edit", "interactive",
        "cflint"
    );
    
    // Result history for .lucli scripts and LuCLI batch execution
    // We keep the last few command outputs so scripts can reference them
    // via placeholders like ${_} and ${last(2)}.
    private static final java.util.List<String> lucliResultHistory = new java.util.ArrayList<>();
    private static final int LUCLI_RESULT_HISTORY_LIMIT = 20;

    // Secret placeholder support for .lucli scripts: ${secret:NAME}
    private static final Pattern LUCLI_SECRET_PATTERN = Pattern.compile("\\$\\{secret:([^}]+)\\}");
    private static SecretStore lucliScriptSecretStore;
    private static boolean lucliScriptSecretsInitialized = false;
    
    // ====================
    // Picocli Command Implementation
    // ====================
    
    /**
     * Main entry point when root command is executed via picocli
     */
    @Override
    public Integer call() throws Exception {
        // Set global flags for backward compatibility
        verbose = verboseOption;
        debug = debugOption;
        timing = timingOption;
        preserveWhitespace = preserveWhitespaceOption;

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
                debug("LuCLI", "No arguments provided, starting terminal mode");
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
            debug("LuCLI", "Routing to .lucli script: " + arg);
            Timer.start("LuCLI Script Execution");
            int exitCode = executeLucliScript(arg);
            Timer.stop("LuCLI Script Execution");
            return exitCode;
        }

        // Check if it's a CFML file (.cfm, .cfc, .cfs)
        if (file.exists() && (arg.endsWith(".cfm") || arg.endsWith(".cfc") || 
                              arg.endsWith(".cfs") || arg.endsWith(".cfml"))) {
            debug("LuCLI", "Routing to run command: " + arg);
            return executeViaRunCommand(arg, args);
        }

        // Check if it's a module name (and not an existing file)
        if (!file.exists() && ModuleCommand.moduleExists(arg)) {
            debug("LuCLI", "Routing to module: " + arg);
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
        verbose("Executing module shortcut: " + moduleName + 
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
            System.out.println("Initializing Lucee CFML engine...");
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
        return verboseOption;
    }
    
    public boolean isDebug() {
        return debugOption;
    }
    
    public boolean isTiming() {
        return timingOption;
    }
    
    public boolean isPreserveWhitespace() {
        return preserveWhitespaceOption;
    }
    
    // ====================
    // Main Entry Point
    // ====================

    public static void main(String[] args) throws Exception {
        // Create Picocli CommandLine with our main command
        CommandLine cmd = new CommandLine(new LuCLI());
        
        // Configure output streams
        cmd.setOut(new PrintWriter(System.out, true));
        cmd.setErr(new PrintWriter(System.err, true));
        
        // Set custom execution strategy that handles timing and global flags
        cmd.setExecutionStrategy(new org.lucee.lucli.cli.LuCLIExecutionStrategy());

        // Configure exception handler
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            StringOutput.Quick.error("Error: " + ex.getMessage());
            if (verbose || debug) {
                ex.printStackTrace();
            }
            return 1;
        });
        
        // Execute the command
        int exitCode = cmd.execute(args);
        
        // Print timing results if enabled
        Timer.printResults();
        
        System.exit(exitCode);
    }
    
    // ====================
    // Output Methods
    // ====================
    
    /**
     * Print a verbose message (only if --verbose flag is enabled)
     * @param message The message to print
     */
    public static void verbose(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
    
    /**
     * Print a verbose message with formatting (only if --verbose flag is enabled)
     * @param format The format string
     * @param args The arguments for the format string
     */
    public static void verbose(String format, Object... args) {
        if (verbose) {
            System.out.println(String.format(format, args));
        }
    }
    
    /**
     * Print a debug message (only if --debug flag is enabled)
     * @param message The message to print
     */
    public static void debug(String message) {
        if (debug) {
            System.err.println("[DEBUG] " + message);
        }
    }
    
    /**
     * Print a debug message with context (only if --debug flag is enabled)
     * @param context The context (e.g., class name or method name)
     * @param message The message to print
     */
    public static void debug(String context, String message) {
        if (debug) {
            System.err.println("[DEBUG " + context + "] " + message);
        }
    }
    
    /**
     * Print a debug message with formatting (only if --debug flag is enabled)
     * @param format The format string
     * @param args The arguments for the format string
     */
    public static void debug(String format, Object... args) {
        if (debug) {
            System.err.println(String.format("[DEBUG] " + format, args));
        }
    }
    
    /**
     * Print an exception stack trace (only if --debug flag is enabled)
     * @param e The exception to print
     */
    public static void debugStack(Exception e) {
        if (debug) {
            e.printStackTrace();
        }
    }
    
    /**
     * Print an info message (always shown)
     * @param message The message to print
     */
    public static void info(String message) {
        System.out.println(message);
    }
    
    /**
     * Print an error message (always shown)
     * @param message The error message
     */
    public static void error(String message) {
        System.err.println(message);
    }
    
    // ====================
    // Deprecated Methods (for backward compatibility)
    // ====================
    
    /** @deprecated Use {@link #verbose(String)} instead */
    @Deprecated
    public static void verbose(String message) {
        verbose(message);
    }
    
    /** @deprecated Use {@link #debug(String)} instead */
    @Deprecated
    public static void debug(String message) {
        debug(message);
    }
    
    /** @deprecated Use {@link #debug(String, String)} instead */
    @Deprecated
    public static void debug(String context, String message) {
        debug(context, message);
    }
    
    /** @deprecated Use {@link #debugStack(Exception)} instead */
    @Deprecated
    public static void debugStack(Exception e) {
        debugStack(e);
    }
    
    /** @deprecated Use {@link #info(String)} instead */
    @Deprecated
    public static void printInfo(String message) {
        info(message);
    }
    
    /** @deprecated Use {@link #error(String)} instead */
    @Deprecated
    public static void printError(String message) {
        error(message);
    }

    /**
     * Resolve ${secret:NAME} placeholders in a .lucli script line using the
     * LuCLI secrets manager. This is evaluated after normal StringOutput
     * placeholders and uses the same local encrypted store as lucee.json.
     */
    private static String resolveSecretsInScriptLine(String line) throws Exception {
        if (line == null || line.isEmpty()) {
            return line;
        }
        Matcher matcher = LUCLI_SECRET_PATTERN.matcher(line);
        if (!matcher.find()) {
            return line; // fast path: no ${secret:...}
        }

        // Lazily initialize the secret store once per script execution.
        if (!lucliScriptSecretsInitialized) {
            initializeLucliScriptSecretStore();
            lucliScriptSecretsInitialized = true;
        }

        // If initialization failed, initializeLucliScriptSecretStore() will have thrown
        // and aborted the script. At this point we expect a usable store instance.

        matcher = LUCLI_SECRET_PATTERN.matcher(line);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            try {
                java.util.Optional<char[]> value = lucliScriptSecretStore.get(name);
                if (value.isEmpty()) {
                    throw new SecretStoreException("Secret '" + name + "' not found for placeholder in script");
                }
                String replacement = new String(value.get());
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } catch (SecretStoreException e) {
                // Fatal for .lucli scripts: stop execution when a required secret
                // cannot be resolved.
                StringOutput.Quick.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Initialize the secret store for .lucli scripts using the same conventions as
     * `lucli secrets` and `lucee.json` secret resolution.
     */
    private static void initializeLucliScriptSecretStore() throws Exception {
        // Derive LuCLI home similar to SecretsCommand.getDefaultStorePath
        String home = System.getProperty("lucli.home");
        if (home == null) home = System.getenv("LUCLI_HOME");
        if (home == null) home = System.getProperty("user.home") + "/.lucli";
        Path storePath = Paths.get(home).resolve("secrets").resolve("local.json");

        if (!Files.exists(storePath)) {
            throw new IllegalStateException("Script references ${secret:...} but local secret store does not exist. Run 'lucli secrets init' and define the required secrets.");
        }

        // Prefer non-interactive passphrase from environment when available
        char[] passphrase = null;
        String envPass = System.getenv("LUCLI_SECRETS_PASSPHRASE");
        if (envPass != null && !envPass.isEmpty()) {
            passphrase = envPass.toCharArray();
        } else if (System.console() != null) {
            passphrase = System.console().readPassword("Enter secrets passphrase to unlock script secrets: ");
        }

        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalStateException("Script requires secrets but no passphrase is available. Set LUCLI_SECRETS_PASSPHRASE or run with an interactive console.");
        }

        lucliScriptSecretStore = new LocalSecretStore(storePath, passphrase);
    }
    
    /**
     * Print a stack trace only if debug mode is enabled
     * @param e The exception to print
     */
    public static void debugStack(Exception e) {
        if (debug) {
            e.printStackTrace();
        }
    }

    
    /**
     * Check if we're running in script mode (executing a .lucli file)
     * This is used to suppress prompts and other interactive elements
     */
    public static boolean isLucliScript() {
        return lucliScript;
    }
    
    /**
     * Record a command result string into the .lucli result history.
     * Empty or null results are ignored.
     */
    public static void recordLucliResult(String result) {
        if (result == null) {
            return;
        }
        String trimmed = result.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        synchronized (lucliResultHistory) {
            // Prepend newest result
            lucliResultHistory.add(0, trimmed);
            // Enforce max history size
            while (lucliResultHistory.size() > LUCLI_RESULT_HISTORY_LIMIT) {
                lucliResultHistory.remove(lucliResultHistory.size() - 1);
            }

            // Also keep the most recent result available in scriptEnvironment
            // so it can be referenced as ${_} from .lucli scripts.
            scriptEnvironment.put("_", trimmed);
        }
    }

    /**
     * Get the Nth result from history (1 = most recent).
     * Returns null if there is no such entry.
     */
    public static String getLucliResult(int position) {
        if (position < 1) {
            position = 1;
        }
        synchronized (lucliResultHistory) {
            if (position > lucliResultHistory.size()) {
                return null;
            }
            return lucliResultHistory.get(position - 1);
        }
    }
    
    /**
     * Determine if a given command name should be treated as a file-system style
     * command within a .lucli script. These are the same commands that the
     * interactive terminal routes through {@link CommandProcessor}.
     */
    private static boolean isFileSystemStyleCommand(String command) {
        return internalFileSystemCommands.contains(command);
    }
    
    /**
     * Get formatted version information
     * @param includeLucee Whether to include Lucee version
     * @return Formatted version string with labels
     * 
     * Examples:
     *   getVersionInfo(false) -> "LuCLI 0.1.207-SNAPSHOT"
     *   getVersionInfo(true)  -> "LuCLI 0.1.207-SNAPSHOT\nLucee Version: 6.2.2.91"
     */
    public static String getVersionInfo(boolean includeLucee) {
        StringBuilder info = new StringBuilder();

        // Version information header first so tools/tests that only see the
        // first few lines can still parse the version without needing to
        // strip the ASCII art banner.
        String ver = getVersion();
        info.append("LuCLI Version: ").append(ver).append("\n");
        // info.append("Version: ").append(ver).append("\n");
        
        // ASCII art banner
        info.append("\n");
        info.append(" _           ____ _     ___ \n");
        info.append("| |   _   _ / ___| |   |_ _|\n");
        info.append("| |  | | | | |   | |    | | \n");
        info.append("| |__| |_| | |___| |___ | | \n");
        info.append("|_____\\__,_|\\____|_____|___|\n");
        info.append("\n");
        
        if (includeLucee) {
            try {
                String luceeVersion = LuceeScriptEngine.getInstance().getVersion();
                info.append("Lucee Version: ").append(luceeVersion).append("\n");
            } catch (Exception e) {
                info.append("Lucee Version: Error - ").append(e.getMessage()).append("\n");
            }
        }
        
        // Copyright and repository information
        info.append("\n");
        info.append("Copyright (c) Mark Drew https://github.com/cybersonic\n");
        info.append("Repository: https://github.com/cybersonic/lucli\n");
        
        return info.toString();
    }
    
    /**
     * Get the version of LuCLI
     * @return version in pom
     */
    public static String getVersion() {
        // Try to read version from JAR manifest
        Package pkg = LuCLI.class.getPackage();
        if (pkg != null) {
            String implVersion = pkg.getImplementationVersion();
            if (implVersion != null && !implVersion.trim().isEmpty()) {
                return implVersion;
            }
        }
        
        // Fallback to reading from manifest manually
        try {
            java.net.URL manifestUrl = LuCLI.class.getClassLoader().getResource("META-INF/MANIFEST.MF");
            if (manifestUrl != null) {
                java.util.jar.Manifest manifest = new java.util.jar.Manifest(manifestUrl.openStream());
                java.util.jar.Attributes attrs = manifest.getMainAttributes();
                String version = attrs.getValue("Implementation-Version");
                if (version != null && !version.trim().isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            // Ignore and fall back
        }
        
        // Final fallback
        return "unknown";
    }
    
    /**
     * Execute a .lucli script file line by line.
     *
     * Each non-blank, non-comment line is passed through {@link StringOutput} and then
     * executed as if it had been typed directly into the interactive terminal.
     * Lines beginning with {@code #} (after trimming) are treated as comments and
     * ignored. This method does NOT start the interactive terminal; it uses the same
     * command infrastructure (Picocli, CommandProcessor, ExternalCommandProcessor)
     * in a non-interactive, batch-friendly way.
     */
    public static int executeLucliScript(String scriptPath) throws Exception {
        // Mark that we're running in non-interactive script mode
        LuCLI.lucliScript = true;

        Path path = Paths.get(scriptPath);
        if (!Files.exists(path)) {
            StringOutput.Quick.error("Script not found: " + scriptPath);
            throw new java.io.FileNotFoundException("Script file not found: " + scriptPath);
        }

        verbose("Executing LuCLI script: " + scriptPath);

        // Read all lines from the script file
        java.util.List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            verbose("Script is empty: " + scriptPath);
            return 0;
        }

        // Pre-scan script for ${secret:...} placeholders so we can prompt for
        // the secrets passphrase (or fail fast on missing secrets) before any
        // commands are executed. 
        try {
            preResolveSecretsInScript(lines);
        } catch (Exception e) {
            // resolveSecretsInScriptLine() already printed a helpful error
            // message for missing/invalid secrets. Treat this as a fatal
            // error for the script.
            debugStack((Exception)(e instanceof Exception ? e : new Exception(e)));
            return 1;
        }

        // Set up a lightweight command environment similar to Terminal.dispatchCommand
        org.lucee.lucli.CommandProcessor commandProcessor = new org.lucee.lucli.CommandProcessor();
        org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor =
            new org.lucee.lucli.ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());
        CommandLine picocli = new CommandLine(new org.lucee.lucli.LuCLI());

        StringOutput stringOutput = StringOutput.getInstance();
        boolean assertionFailed = false;

        // Pattern for simple variable assignments with command substitution: NAME=$(command ...)
        Pattern assignmentPattern = Pattern.compile("^(?i)(?:set\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:\\$\\((.*)\\)|(.+))\\s*$");

        // Make the latest history entry available as ${_} before script lines run
        if (!lucliResultHistory.isEmpty()) {
            String last = lucliResultHistory.get(0);
            scriptEnvironment.put("_", last);
        }

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                // Skip blank lines in scripts
                continue;
            }
            
            // Treat lines starting with '#' as comments (including shebangs like '#!/usr/bin/env lucli')
            if (trimmed.startsWith("#")) {
                debug("LuCLIScript", "Skipping comment line: " + line);
                continue;
            }
            
            // Early-exit command for .lucli scripts: "exit" or "exit <code>"
            if(isExitCommand(line)){
                int exitCode = getExitCodeFromExitCommand(line);
                debug("LuCLIScript", "Exit requested from script with code " + exitCode);
                return exitCode;
            }
            

            // Check if this is a SET directive (case-insensitive)
            if (trimmed.toLowerCase().startsWith("set ")) {

                // Quick validation: must have '=' after 'set '
                if (trimmed.indexOf('=') < 1) {
                    StringOutput.Quick.error("SET directive syntax error: " + line);
                    continue;
                }

                // Parse: set KEY=VALUE or set KEY="VALUE" or set KEY='VALUE'
                String afterSet = trimmed.substring(4).trim();
                
                // Split on first '=' to get key and value
                int equalsIndex = afterSet.indexOf('=');
                if (equalsIndex > 0) {
                    String key = afterSet.substring(0, equalsIndex).trim();
                    String value = afterSet.substring(equalsIndex + 1).trim();
                    
                    // Remove surrounding quotes from value only (not quotes within)
                    if (value.length() >= 2) {
                        char firstChar = value.charAt(0);
                        char lastChar = value.charAt(value.length() - 1);
                        
                        // Check if surrounded by matching quotes
                        if ((firstChar == '"' && lastChar == '"') || 
                            (firstChar == '\'' && lastChar == '\'')) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }
                    
                    // 1) Command substitution in SET: set NAME=$(command ...)
                    if (isExecEvaluation(value)) {
                        String innerCommand = getExecEvaluationInnerCommand(value);
                        handleCommandSubstitutionAssignment(
                            key,
                            innerCommand,
                            commandProcessor,
                            externalCommandProcessor,
                            picocli,
                            stringOutput
                        );
                        continue;
                    }

                    // 2) Normal SET value: resolve secrets and placeholders so
                    //    constructs like set var2=${var1} and
                    //    set mySecret=${secret:NAME} behave as expected.
                    try {
                        String valueWithSecrets = resolveSecretsInScriptLine(value);
                        String resolvedValue    = stringOutput.process(valueWithSecrets);

                        debug("SET directive: " + key + " = " + resolvedValue);

                        scriptEnvironment.put(key, resolvedValue);
                        stringOutput.addPlaceholder(key, resolvedValue);
                    } catch (Exception e) {
                        StringOutput.Quick.error("Error processing SET value for '" + key + "': " + e.getMessage());
                        debugStack(e);
                    }
                    continue;
                }
            }

            // Check for simple variable assignment with command substitution: NAME=$(command ...)
            java.util.regex.Matcher assignmentMatcher = assignmentPattern.matcher(trimmed);
            if (assignmentMatcher.matches()) {
                String varName = assignmentMatcher.group(1);
                String innerCommand = assignmentMatcher.group(2).trim();

                handleCommandSubstitutionAssignment(
                    varName,
                    innerCommand,
                    commandProcessor,
                    externalCommandProcessor,
                    picocli,
                    stringOutput
                );
                continue;
            }

            // First resolve ${secret:NAME} placeholders using the LuCLI secrets store
            String lineWithSecrets = resolveSecretsInScriptLine(line);

            // Apply StringOutput placeholder processing
            String processedLine = stringOutput.process(lineWithSecrets);

            // Parse command into parts using the same parser as the terminal
            String[] parts = commandProcessor.parseCommand(processedLine);
            if (parts.length == 0) {
                continue;
            }

            String command = parts[0].toLowerCase();

            // Built-in testing/assertion command: assert <actual> <expected>
            if ("assert".equals(command)) {
                if (parts.length < 3) {
                    StringOutput.Quick.error("assert: usage: assert <actual> <expected>");
                } else {
                    String actual = parts[1];
                    String expected = parts[2];

                    if (actual.equals(expected)) {
                        System.out.println("✅ assert passed: expected '" + expected + "'");
                    } else {
                        System.out.println("❌ assert failed: expected '" + expected + "' but got '" + actual + "'");
                        assertionFailed = true;
                    }
                }
                continue;
            }

            // Delegate all other commands to the shared dispatcher
            executeLucliScriptCommand(
                processedLine,
                commandProcessor,
                externalCommandProcessor,
                picocli,
                false
            );
            System.out.println("");
        }

        // Scripts are best-effort; fail overall if any assertion failed
        return assertionFailed ? 1 : 0;
    }
//  ExecFile, ExecLine, ExecVar, ExecEval
    
    /**
     * Determines whether the provided command string is wrapped for execution evaluation. 
     *
     * @param command the command string to inspect
     * @return {@code true} if the command starts with "$(" and ends with ")", otherwise {@code false}
     */
    private static boolean isExecEvaluation(String command) {
        return command.startsWith("$(") && command.endsWith(")");
    }

    private static boolean isExitCommand(String line) {
        String trimmed = line.trim();
        return trimmed.equalsIgnoreCase("exit") || trimmed.toLowerCase().startsWith("exit ");
    }

    private static int getExitCodeFromExitCommand(String line) {
        String trimmed = line.trim();
        int exitCode = 0;
        String[] parts = trimmed.split("\\s+");
        if (parts.length > 1) {
            try {
                exitCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                exitCode = 1;
            }
        }
        return exitCode;
    }

    /**
     * Determines whether a LuCLI exec line should be treated as empty or ignorable.
     * <p>
     * A line is considered empty if it is {@code null}, contains only whitespace,
     * or begins with the comment character {@code '#'} after trimming.
     * </p>
     *
     * @param line the input line to evaluate
     * @return {@code true} if the line is {@code null}, blank, or a comment; otherwise {@code false}
     */
    private static boolean isEmptyLucliExecLine(String line) {
        if(line == null) {
            return true;
        }
        String trimmed = line.trim();
        if(trimmed.isEmpty()) {
            return true;
        }
        if(trimmed.startsWith("#")) {
            return true;
        }
        return false;
    }


    private static String getExecEvaluationInnerCommand(String command) {
        return command.substring(2, command.length() - 1).trim();
    }
    /**
     * Pre-scan a .lucli script for ${secret:...} placeholders so that we can
     * unlock the secret store and validate that all referenced secrets exist
     * before executing any commands.
     */
    private static void preResolveSecretsInScript(java.util.List<String> lines) throws Exception {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue; // skip blank
            }
            // Skip full-line comments / shebangs
            if (trimmed.startsWith("#")) {
                continue;
            }
            // Fast path: only bother when we see the secret placeholder prefix
            if (trimmed.contains("${secret:")) {
                // We don't need the resolved value here; calling this once per
                // relevant line is enough to trigger secret store
                // initialization and to validate that referenced secrets
                // exist. Any failures here should abort the script before
                // executing commands.
                resolveSecretsInScriptLine(line);
            }
        }
    }
 
    /**
     * Helper to execute a command inside $(...) for variable assignment and record its result
     * into the script environment and placeholder system.
     */
    private static void handleCommandSubstitutionAssignment(
            String varName,
            String innerCommand,
            org.lucee.lucli.CommandProcessor commandProcessor,
            org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor,
            CommandLine picocli,
            StringOutput stringOutput) throws Exception {

        // Resolve ${secret:NAME} placeholders before normal processing
        String innerWithSecrets = resolveSecretsInScriptLine(innerCommand);

        // Process ${PLACEHOLDER} values (environment, previous results, etc.)
        String processedInner = stringOutput.process(innerWithSecrets);



        String captured = executeLucliScriptCommand(
            processedInner,
            commandProcessor,
            externalCommandProcessor,
            picocli,
            true
        );

        debug("LuCLIScript", "Captured output for variable '" + varName + "': " + captured);

        scriptEnvironment.put(varName, captured);
        stringOutput.addPlaceholder(varName, captured);

        // Also update the generic result history so ${_} and last(n)
        // can see values coming from explicit assignments.
        recordLucliResult(captured);
    }
 
    /**
     * Shared dispatcher for executing a single script line in the LuCLI script environment.
     * This mirrors the behavior of the interactive terminal, with optional output capture
     * for use in variable assignment (e.g., FOO=$(command ...)).
     */
    private static String executeLucliScriptCommand(
        String processedLine,
        org.lucee.lucli.CommandProcessor commandProcessor,
        org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor,
        CommandLine picocli,
        boolean captureOutput) {

        // Normalise script line: if the user copied a full "lucli ..." command
        // into the script, strip the leading "lucli" so we don't recursively
        // invoke LuCLI from within itself.
        String scriptLine = processedLine == null ? "" : processedLine.trim();

        // Should be fairly unreachable since this is pre-checked, but guard against empty lines
        if(scriptLine.trim().isEmpty()) {
            return "";
        }


        String[] firstTokens = commandProcessor.parseCommand(scriptLine);
        if (firstTokens.length > 0 && "lucli".equalsIgnoreCase(firstTokens[0])) {
            if (firstTokens.length == 1) {
                return ""; // bare "lucli"
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < firstTokens.length; i++) {
                if (i > 1) sb.append(' ');
                sb.append(firstTokens[i]);
            }
            scriptLine = sb.toString();
        }
        

        try {
            // QUICK PATH: handle cfml without using parseCommand for its expression,
            // so that inner quotes are fully preserved.
            String lower = scriptLine.toLowerCase();
            int spaceIdx = lower.indexOf(' ');
            String cmdName = (spaceIdx == -1) ? lower : lower.substring(0, spaceIdx);

            if ("cfml".equals(cmdName)) {
                int firstSpace = scriptLine.indexOf(' ');
                String cfmlCode = scriptLine.substring(firstSpace + 1).trim();
                if (cfmlCode.isEmpty()) {
                    StringOutput.Quick.error("cfml: missing expression");
                    return "";
                }

                StringWriter outWriter = null;
                StringWriter errWriter = null;
                PrintWriter out = null;
                PrintWriter err = null;

                if(captureOutput){
                    outWriter = new StringWriter();
                    errWriter = new StringWriter();
                    out = new PrintWriter(outWriter);
                    err = new PrintWriter(errWriter);
                    picocli.setOut(out);
                    picocli.setErr(err);
                }

                picocli.execute("cfml", cfmlCode);
                Object res = picocli.getExecutionResult();
                // System.out.println("Returned Variable:" + res.toString());

                if(captureOutput){
                    String stdOut = outWriter.toString();
                    String stdErr = errWriter.toString();

                    
                }

                
                return (res != null ? res.toString() : "");



                // if (captureOutput) {

                //     // // Silent execution and gather output
                //     // LuceeScriptEngine luceeEngine = LuceeScriptEngine.getInstance();
                //     // Object ret = luceeEngine.evalScriptStatement(cfmlCode, null);
                //     // if (ret != null) {
                //     //     recordLucliResult(ret.toString());
                //     // }
                //     // return ret.toString();

                //     // java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                //     // java.io.PrintStream originalOut = System.out;
                //     // java.io.PrintStream originalErr = System.err;
                //     // try {
                //     //     System.setOut(new java.io.PrintStream(baos));
                //     //     System.setErr(new java.io.PrintStream(baos));
                //     //     // Execute cfml with the entire expression as a single argument so
                //     //     // inner quotes are preserved.
                //     //     // This doesnt need picocli, we can just do it direct
                //     //     // Execute it directly to avoid double-parsing issues
                //     //     // luceeEngine.evalScriptStatement(wrappedScript, null);
                //     //     // But this is not for everuthing then? It's running cfml only.
                //     //     picocli.execute("cfml", cfmlCode);
                //     // } 
                //     // catch(Exception e) {
                //     //     StringOutput.Quick.error("Error executing cfml command: " + e.getMessage());
                //     //     debugStack(e);
                //     // }
                //     // finally {
                //     //     System.setOut(originalOut);
                //     //     System.setErr(originalErr);
                //     // }
                //     // String captured = baos.toString().trim();
                //     // if (captured != null && !captured.isEmpty()) {
                //     //     recordLucliResult(captured);
                //     // }
                //     // return "";
                // } else {
                    // picocli.execute("cfml", cfmlCode);
                    // Object res = picocli.getExecutionResult();
                    // System.out.println("Elvis!");
                    // return "";
                // }

            }
            

            // For all non-cfml commands, it is safe to parse the line into parts.
            String[] parts = commandProcessor.parseCommand(scriptLine);
            if (parts.length == 0) {
                return "";
            }

            String command = parts[0].toLowerCase();

            // 1) Picocli subcommands (server, modules, run, etc.)
            if (picocli.getSubcommands().containsKey(command)) {
                if (captureOutput) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    java.io.PrintStream originalOut = System.out;
                    java.io.PrintStream originalErr = System.err;
                    try {
                        System.setOut(new java.io.PrintStream(baos));
                        System.setErr(new java.io.PrintStream(baos));
                        picocli.execute(parts); // Picocli writes directly to System.out/err
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                    String captured = baos.toString().trim();
                    if (captured != null && !captured.isEmpty()) {
                        recordLucliResult(captured);
                    }
                    return "";
                } else {
                    picocli.execute(parts);
                    return "";
                }
            }

            // 2) Module shortcuts (hello-world, lint, etc.)
            if (org.lucee.lucli.modules.ModuleCommand.moduleExists(command)) {
                String[] moduleArgs = new String[parts.length - 1];
                if (moduleArgs.length > 0) {
                    System.arraycopy(parts, 1, moduleArgs, 0, moduleArgs.length);
                }

                if (captureOutput) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    java.io.PrintStream originalOut = System.out;
                    java.io.PrintStream originalErr = System.err;
                    try {
                        System.setOut(new java.io.PrintStream(baos));
                        System.setErr(new java.io.PrintStream(baos));
                        org.lucee.lucli.modules.ModuleCommand.executeModuleByName(command, moduleArgs);
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                    String captured = baos.toString().trim();
                    if (captured != null && !captured.isEmpty()) {
                        recordLucliResult(captured);
                    }
                    return captured;
                } else {
                    org.lucee.lucli.modules.ModuleCommand.executeModuleByName(command, moduleArgs);
                    return "";
                }
            }

            // 3) File-system style commands (ls, cd, rm, etc.)
            if (isFileSystemStyleCommand(command)) {
                String fsResult = commandProcessor.executeCommand(scriptLine);
                if (fsResult != null) {
                    recordLucliResult(fsResult);
                }
                if (captureOutput) {
                    return fsResult != null ? fsResult : "";
                } else {
                    if (fsResult != null && !fsResult.isEmpty()) {
                        System.out.println(fsResult);
                    }
                    return "";
                }
            }

            // 4) Fallback to external command processor (git, echo, etc.)
            String extResult = externalCommandProcessor.executeCommand(scriptLine);
            if (extResult != null) {
                recordLucliResult(extResult);
            }
            if (captureOutput) {
                return extResult != null ? extResult : "";
            } else {
                if (extResult != null && !extResult.isEmpty()) {
                    System.out.println(extResult);
                }
                return "";
            }

        } catch (Exception e) {
            StringOutput.Quick.error("Error executing script line '" + processedLine + "': " + e.getMessage());
            debugStack(e);
            return "";
        }
    }









}
