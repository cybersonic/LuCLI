package org.lucee.lucli;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lucee.lucli.cli.LuCLIVersionProvider;
import org.lucee.lucli.profile.CliProfile;
import org.lucee.lucli.profile.DefaultProfile;
import org.lucee.lucli.cli.commands.CfmlCommand;
import org.lucee.lucli.cli.commands.CompletionCommand;
import org.lucee.lucli.cli.commands.DaemonCommand;
import org.lucee.lucli.cli.commands.McpCommand;
import org.lucee.lucli.cli.commands.AiCommand;
import org.lucee.lucli.cli.commands.modules.ModulesCommand;
import org.lucee.lucli.cli.commands.ParrotCommand;
import org.lucee.lucli.cli.commands.ReplCommand;
import org.lucee.lucli.cli.commands.RunCommand;
import org.lucee.lucli.cli.commands.SecretsCommand;
import org.lucee.lucli.cli.commands.ServerCommand;
import org.lucee.lucli.cli.commands.SystemCommand;
import org.lucee.lucli.cli.commands.VersionsListCommand;
import org.lucee.lucli.cli.commands.XmlCommand;
import org.lucee.lucli.cli.commands.deps.DepsCommand;
import org.lucee.lucli.cli.commands.deps.InstallCommand;
import org.lucee.lucli.cli.commands.logic.IfCommand;
import org.lucee.lucli.cli.commands.logic.XSetCommand;
import org.lucee.lucli.modules.ModuleCommand;
import org.lucee.lucli.modules.BundledModuleInstaller;
import org.lucee.lucli.script.LucliScriptPreprocessor;
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
        ReplCommand.class,
        CompletionCommand.class,
        VersionsListCommand.class,
        ParrotCommand.class,
        SecretsCommand.class,
        SystemCommand.class,
        CommandLine.HelpCommand.class,
        RunCommand.class,
        DaemonCommand.class,
        McpCommand.class,
        AiCommand.class,
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
        "  lucli modules list              # List available modules",
        "  lucli system                    # Show system command help",
        "  lucli system inspect --lucee    # Print Lucee CFConfig as formatted JSON",
        "  lucli system paths              # Show resolved LuCLI home paths",
        "  lucli system backup create      # Create a LuCLI home backup",
        "  lucli system backup prune --older-than 30d --keep 10  # Preview backup pruning"
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
    private boolean preserveWhitespaceOption = false;

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

    @Option(
        names = {"--env", "-e"},
        paramLabel = "<environment>",
        description = "Set the execution environment (e.g., dev, staging, prod) for .lucli scripts"
    )
    private String envOption;

    @Option(
        names = {"--envfile"},
        paramLabel = "<path>",
        description = "Load environment variables from a file (e.g., .env) before script execution"
    )
    private String envFileOption;

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "FILE_OR_MODULE",
        description = "Optional: CFML/LuCLI file to execute, or module name to run"
    )
    private String firstArg;

    @picocli.CommandLine.Unmatched
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
    public static String currentEnvironment = null;
    public static String envFilePath = null;
    private static boolean lucliScript = false;
    private static volatile Path runtimeCwd = null;
    private static CliProfile activeProfile = new DefaultProfile();

    public static Map<String, String> scriptEnvironment = new HashMap<>(System.getenv());

    /**
     * The active CLI profile, determined by the binary name at startup.
     * Controls branding, home directory name, and prompt prefix.
     */
    public static CliProfile getActiveProfile() {
        return activeProfile;
    }

    /**
     * Override the active profile. Intended for tests that need to verify
     * profile-dependent behaviour; production code sets the profile once in
     * {@link #main(String[])}.
     */
    public static void setActiveProfile(CliProfile profile) {
        activeProfile = profile;
    }
    
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
        LuCLI.verbose = verboseOption;
        LuCLI.debug = debugOption;
        LuCLI.timing = timingOption;
        LuCLI.preserveWhitespace = preserveWhitespaceOption;

        // Initialize timing if requested
        Timer.setEnabled(timing);
        Timer.start("Total Execution");

        try {
            // Handle special version command
            if (luceeVersionRequested) {
                String luceeVersion = LuceeScriptEngine.getInstance().getVersion();
                StringOutput.Quick.info("Lucee Version: " + luceeVersion);
                // showLuceeVersionNonInteractive();
                return 0;
            }

            // Pre-load environment file if --envfile was specified
            if (envFileOption != null && !envFileOption.trim().isEmpty()) {
                loadEnvFileIntoScript(Paths.get(envFileOption), null);
            }

            // No argument provided - start interactive terminal
            if (firstArg == null || firstArg.trim().isEmpty()) {
                // Check for unrecognized options absorbed by @Unmatched
                if (additionalArgs != null && additionalArgs.length > 0) {
                    for (String arg : additionalArgs) {
                        if (arg.startsWith("-")) {
                            StringOutput.Quick.error("Unknown option: '" + arg + "'");
                            spec.commandLine().usage(System.err);
                            return 2;
                        }
                    }
                }
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
        if (file.exists() && arg.endsWith(".cfc")) {
            StringOutput.Quick.error("Executing .cfc files directly is not supported.");
            StringOutput.getInstance().println("Use a module entry point instead (e.g. 'lucli modules run <module>').");
            return 1;
        }
        if (file.exists() && (arg.endsWith(".cfm") || 
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
            "  - If it's a file, check the path and extension (.cfm, .cfs, .lucli)\n" +
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
        // Intercept --help / -h before picocli can swallow it;
        // delegate to the module's own showHelp() instead.
        if (args != null) {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    LuceeScriptEngine engine = LuceeScriptEngine.getInstance();
                    engine.executeModule(moduleName, new String[]{"showHelp"});
                    return 0;
                }
            }
        }

        verbose("Executing module shortcut: " + moduleName + 
            " (equivalent to 'lucli modules run " + moduleName + " " + String.join(" ", args) + "')");
        
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("modules");
        cmdArgs.add("run");
        cmdArgs.add(moduleName);
        if (args != null && args.length > 0) {
            cmdArgs.addAll(Arrays.asList(args));
        }
        // Re-inject root-level flags that picocli consumed at the root before the
        // module shortcut dispatched, so the module can see them (e.g. the wheels
        // module's `doctor --verbose` / `stats --verbose` / `test --verbose`).
        // Appended AFTER the module args so the subcommand stays the first
        // positional; the module's arg parser reads --verbose/--debug from there.
        if (isVerbose()) {
            cmdArgs.add("--verbose");
        }
        if (isDebug()) {
            cmdArgs.add("--debug");
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
    
    public String getEnvOption() {
        return envOption;
    }
    
    public String getEnvFileOption() {
        return envFileOption;
    }
    
    /**
     * Get the current execution environment.
     * Resolution order: --env flag > LUCLI_ENV environment variable > null
     * 
     * @return The environment name (e.g., "dev", "prod") or null if not set
     */
    public static String getCurrentEnvironment() {
        // First check static field (set by execution strategy from --env flag)
        if (currentEnvironment != null && !currentEnvironment.trim().isEmpty()) {
            return currentEnvironment.trim();
        }
        
        // Then check LUCLI_ENV environment variable
        String envVar = System.getenv("LUCLI_ENV");
        if (envVar != null && !envVar.trim().isEmpty()) {
            return envVar.trim();
        }
        
        return null;
    }

    /**
     * Set explicit runtime CWD used by LuCLI-managed execution contexts.
     */
    public static void setRuntimeCwd(Path cwd) {
        if (cwd == null) {
            runtimeCwd = null;
            return;
        }
        runtimeCwd = cwd.toAbsolutePath().normalize();
    }

    /**
     * Return explicit runtime CWD override if set; otherwise null.
     */
    public static Path getRuntimeCwd() {
        return runtimeCwd;
    }

    /**
     * Clear explicit runtime CWD override.
     */
    public static void clearRuntimeCwd() {
        runtimeCwd = null;
    }

    /**
     * Resolve effective runtime CWD, falling back to JVM user.dir.
     */
    public static Path getEffectiveRuntimeCwd() {
        Path cwd = runtimeCwd;
        if (cwd != null) {
            return cwd;
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
    
    // ====================
    // Main Entry Point
    // ====================

    public static void main(String[] args) throws Exception {
        // Suppress JLine "Unable to create a system terminal" warning
        java.util.logging.Logger.getLogger("org.jline").setLevel(java.util.logging.Level.SEVERE);

        // Binary name detection runs only on the initial CLI entry point — NOT in
        // executeInProcess() — because modules call executeInProcess() recursively
        // (e.g., BaseModule.executeCommand("server", ["start"])) and the system
        // property persists for the JVM's lifetime. Prepending here ensures it
        // happens exactly once.
        args = prependBinaryNameIfAliased(args);

        // Resolve the CLI profile (branding, home dir) from the binary name.
        // Must happen after prependBinaryNameIfAliased() which normalises the
        // system property, and before executeInProcess() which may read paths.
        // forBinaryName() normalises paths and extensions internally.
        activeProfile = CliProfile.forBinaryName(
            System.getProperty("lucli.binary.name", "lucli")
        );

        int exitCode = executeInProcess(args);
        System.exit(exitCode);
    }

    /**
     * Execute LuCLI commands in-process without calling System.exit().
     * This is meant for use by modules or embedded callers that need to
     * invoke LuCLI commands within an existing JVM session (e.g. from
     * BaseModule.executeCommand() or the interactive terminal).
     *
     * @param args command line arguments
     * @return exit code (0 for success)
     */
    public static int executeInProcess(String[] args) throws Exception {
        // Suppress JLine "Unable to create a system terminal" warning
        java.util.logging.Logger.getLogger("org.jline").setLevel(java.util.logging.Level.SEVERE);

        // Install any modules bundled into /modules-install in the active profile home.
        // Runs once per process and is safe to call from both CLI entry and embedded flows.
        BundledModuleInstaller.ensureBundledModulesInstalled();

        // For one-shot in-process invocations, default runtime CWD to JVM user.dir
        // unless a session flow has already provided an explicit value.
        if (runtimeCwd == null) {
            setRuntimeCwd(Paths.get(System.getProperty("user.dir")));
        }

        // Pre-process: if first arg is a module name and --help/-h (or the bare
        // `help` verb) is present, rewrite to "modules run <module> ... --help" so
        // picocli routes to ModulesRunCommandImpl (which delegates to the module's
        // showHelp()) instead of showing root-level help.
        args = preprocessModuleHelp(args);
        // Pre-process: rewrite `<module> [subs...] --version=<value>` to
        // "modules run <module> ... --version=<value>" so the root --version flag
        // (versionHelp, boolean) doesn't short-circuit on a value-bearing
        // `--version=<tag>` a module wants (e.g. `wheels deploy --version=v1`).
        args = preprocessModuleVersion(args);

        // Create Picocli CommandLine with our main command
        CommandLine cmd = new CommandLine(new LuCLI());
        cmd.setExpandAtFiles(false);
        
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
        
        return exitCode;
    }
    
    /**
     * If the binary was invoked under an alias (e.g., via symlink "wheels" -> "lucli"),
     * prepend the binary name as the first argument so it routes to the corresponding
     * module. For example, "wheels generate model User" becomes
     * ["wheels", "generate", "model", "User"] which routes to the "wheels" module.
     *
     * The binary name is passed from the shell stub via -Dlucli.binary.name.
     * Only activates when the name is not "lucli" itself.
     */
    private static String[] prependBinaryNameIfAliased(String[] args) {
        String binaryName = CliProfile.normalizeBinaryName(
            System.getProperty("lucli.binary.name", "lucli")
        );

        // Only activate for non-lucli binary names
        if (binaryName == null || binaryName.isEmpty()
                || "lucli".equalsIgnoreCase(binaryName)) {
            return args;
        }

        // Note: verbose/debug flags are not yet parsed at this point (pre-picocli),
        // so use LUCLI_DEBUG env var for early-boot diagnostics.
        if (System.getenv("LUCLI_DEBUG") != null) {
            System.err.println("[lucli] Binary name detection: invoked as '" + binaryName + "', prepending module name");
        }

        String[] newArgs = new String[args.length + 1];
        newArgs[0] = binaryName;
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return newArgs;
    }

    /**
     * If the CLI args look like {@code <module-name> [subcommand...] --help},
     * rewrite them to {@code modules run <module-name> [subcommand...] --help}
     * so picocli routes to ModulesRunCommandImpl instead of showing root help.
     */
    private static String[] preprocessModuleHelp(String[] args) {
        if (args.length < 2) return args;

        String first = args[0];
        if (first.startsWith("-")) return args;

        // Only rewrite when the first arg is an installed module (e.g. the
        // binary-name-aliased `wheels`). Bare `lucli help` (first arg not a
        // module) is left to LuCLI's builtin HelpCommand, unchanged.
        if (!ModuleCommand.moduleExists(first)) return args;

        // (a) `<module> help [subcommand...]` — the bare `help` verb right after
        // the module name. Drop it and append --help so it routes to the
        // module's showHelp instead of LuCLI's builtin HelpCommand (which would
        // print the root/global banner — the `wheels help` brand-leak).
        if ("help".equals(args[1])) {
            List<String> rewritten = new ArrayList<>();
            rewritten.add("modules");
            rewritten.add("run");
            rewritten.add(first);
            for (int i = 2; i < args.length; i++) {
                rewritten.add(args[i]);
            }
            rewritten.add("--help");
            return rewritten.toArray(new String[0]);
        }

        // (b) --help / -h anywhere after the module name.
        boolean hasHelp = false;
        for (int i = 1; i < args.length; i++) {
            if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                hasHelp = true;
                break;
            }
        }
        if (!hasHelp) return args;

        // Prepend "modules run" so picocli delegates to ModulesRunCommandImpl
        List<String> rewritten = new ArrayList<>();
        rewritten.add("modules");
        rewritten.add("run");
        for (String arg : args) {
            rewritten.add(arg);
        }
        return rewritten.toArray(new String[0]);
    }

    /**
     * Rewrite `<module> [subcommands...] --version=<value>` to
     * "modules run <module> [subcommands...] --version=<value>" so a module that
     * accepts a value-bearing --version (e.g. `wheels deploy --version=v1.2.3`,
     * Kamal-compatible) is not blocked by the root's boolean versionHelp --version
     * flag, which would short-circuit with "Invalid value for option '--version'".
     * A bare `--version` (no value) and bare `lucli --version` (first arg not a
     * module) are untouched, preserving the root version-banner behavior.
     */
    private static String[] preprocessModuleVersion(String[] args) {
        if (args.length < 2) return args;

        String first = args[0];
        if (first.startsWith("-")) return args;
        if (!ModuleCommand.moduleExists(first)) return args;

        boolean hasVersionValue = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--version=")) {
                hasVersionValue = true;
                break;
            }
        }
        if (!hasVersionValue) return args;

        List<String> rewritten = new ArrayList<>();
        rewritten.add("modules");
        rewritten.add("run");
        for (String arg : args) {
            rewritten.add(arg);
        }
        return rewritten.toArray(new String[0]);
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
    public static void printVerbose(String message) {
        verbose(message);
    }
    
    /** @deprecated Use {@link #debug(String)} instead */
    @Deprecated
    public static void printDebug(String message) {
        debug(message);
    }
    
    /** @deprecated Use {@link #debug(String, String)} instead */
    @Deprecated
    public static void printDebug(String context, String message) {
        debug(context, message);
    }
    
    /** @deprecated Use {@link #debugStack(Exception)} instead */
    @Deprecated
    public static void printDebugStackTrace(Exception e) {
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
     * Check if we're running in script mode (executing a .lucli file)
     * This is used to suppress prompts and other interactive elements
     */
    public static boolean isLucliScript() {
        return lucliScript;
    }

    // ====================
    // Env File Loading
    // ====================

    /**
     * Parse a .env-style file and return its contents as a Map.
     * Supports KEY=VALUE format, comments (#), blank lines, and
     * both single- and double-quoted values.
     *
     * @param envFilePath Absolute or relative path to the env file
     * @return Map of key-value pairs parsed from the file
     * @throws java.io.IOException if the file cannot be read
     */
    public static Map<String, String> loadEnvFileToMap(Path envFilePath) throws java.io.IOException {
        Map<String, String> result = new HashMap<>();
        if (envFilePath == null || !Files.exists(envFilePath)) {
            return result;
        }

        java.util.List<String> lines = Files.readAllLines(envFilePath, java.nio.charset.StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            String line = rawLine.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse KEY=VALUE
            int equalIndex = line.indexOf('=');
            if (equalIndex <= 0) {
                continue; // Invalid line, skip
            }

            String key = line.substring(0, equalIndex).trim();
            String value = line.substring(equalIndex + 1).trim();

            // Remove surrounding quotes if present
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }
            }

            result.put(key, value);
        }
        return result;
    }

    /**
     * Load a .env-style file and inject all variables into
     * {@link #scriptEnvironment} and {@link StringOutput} placeholders.
     *
     * @param filePath  Path to the env file (absolute or relative)
     * @param resolveDir If non-null and filePath is relative, resolve against this directory;
     *                   if null, resolve against the current working directory
     */
    public static void loadEnvFileIntoScript(Path filePath, Path resolveDir) {
        Path resolved = filePath;
        if (!filePath.isAbsolute()) {
            if (resolveDir != null) {
                resolved = resolveDir.resolve(filePath);
            } else {
                resolved = Paths.get(System.getProperty("user.dir")).resolve(filePath);
            }
        }

        if (!Files.exists(resolved)) {
            StringOutput.Quick.error("source: file not found: " + resolved);
            return;
        }

        try {
            Map<String, String> vars = loadEnvFileToMap(resolved);
            StringOutput stringOutput = StringOutput.getInstance();
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                scriptEnvironment.put(entry.getKey(), entry.getValue());
                stringOutput.addPlaceholder(entry.getKey(), entry.getValue());
            }
            verbose("Loaded " + vars.size() + " variable(s) from " + resolved);
        } catch (java.io.IOException e) {
            StringOutput.Quick.error("source: could not read file: " + resolved + " (" + e.getMessage() + ")");
        }
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
     *   getVersionInfo(true)  -> "LuCLI 0.1.207-SNAPSHOT\nLucee Version: 6.2.2.91\nJava Version: openjdk 21.0.5 2024-10-15 LTS"
     */
    public static String getVersionInfo(boolean includeLucee) {
        StringBuilder info = new StringBuilder();

        // Version information header first so tools/tests that only see the
        // first few lines can still parse the version without needing to
        // strip the ASCII art banner.
        String ver = activeProfile.productVersionOverride();
        if (ver == null || ver.trim().isEmpty()) {
            ver = getVersion();
        } else {
            ver = ver.trim();
        }
        info.append(activeProfile.displayName()).append(" Version: ").append(ver).append("\n");

        // ASCII art banner — provided by the active profile
        info.append("\n");
        info.append(activeProfile.bannerText());
        info.append("\n");
        
        if (includeLucee) {
            try {
                String luceeVersion = LuceeScriptEngine.getInstance().getVersion();
                info.append("Lucee Version: ").append(luceeVersion).append("\n");
            } catch (Exception e) {
                info.append("Lucee Version: Error - ").append(e.getMessage()).append("\n");
            }
        }

        info.append("Java Version: ").append(getJavaVersionInfo()).append("\n");
        
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
        // Preferred source: filtered build resource available in both
        // mvn exec:java development runs and packaged JAR executions.
        String resourceVersion = readVersionFromResource();
        if (resourceVersion != null && !resourceVersion.isBlank()) {
            return resourceVersion;
        }
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

    private static String readVersionFromResource() {
        try (java.io.InputStream in = LuCLI.class.getResourceAsStream("/lucli/version.properties")) {
            if (in == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("lucli.version");
            if (version == null) {
                return null;
            }
            version = version.trim();
            return version.isEmpty() ? null : version;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getJavaVersionInfo() {
        try {
            String javaExecutable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString()
                : Paths.get(System.getProperty("java.home"), "bin", "java").toString();

            Process process = new ProcessBuilder(javaExecutable, "-version")
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            if (output != null && !output.trim().isEmpty()) {
                String[] lines = output.split("\\R");
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }
                    return normalizeJavaVersionLine(line);
                }
            }
        } catch (Exception e) {
            // Fall back to runtime properties
        }

        String runtimeName = System.getProperty("java.runtime.name");
        if (runtimeName == null || runtimeName.trim().isEmpty()) {
            runtimeName = "java";
        }
        String runtimeVersion = System.getProperty("java.runtime.version");
        if (runtimeVersion == null || runtimeVersion.trim().isEmpty()) {
            runtimeVersion = System.getProperty("java.version");
        }
        if (runtimeVersion == null || runtimeVersion.trim().isEmpty()) {
            return runtimeName;
        }
        return runtimeName + " " + runtimeVersion;
    }

    private static String normalizeJavaVersionLine(String line) {
        String normalized = line == null ? "" : line.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        normalized = normalized.replaceAll("\"", "");
        normalized = normalized.replaceFirst("(?i)\\bversion\\b\\s+", "");
        return normalized.trim();
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
        String env = getCurrentEnvironment();
        if (env != null) {
            verbose("Executing with environment: " + env);
        }

        // Pre-load --envfile if specified (covers the RunCommand path)
        if (envFilePath != null && !envFilePath.trim().isEmpty()) {
            Path scriptDir = path.toAbsolutePath().getParent();
            loadEnvFileIntoScript(Paths.get(envFilePath), scriptDir);
        }

        // Read all lines from the script file
        java.util.List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            verbose("Script is empty: " + scriptPath);
            return 0;
        }

        // Preprocess: line continuation and environment blocks
        // (Comments and placeholders are handled per-line during execution)
        try {
            lines = LucliScriptPreprocessor.joinContinuationLines(lines);
            lines = LucliScriptPreprocessor.processEnvBlocks(lines, env);
        } catch (LucliScriptPreprocessor.PreprocessorException e) {
            StringOutput.Quick.error("Script preprocessing error: " + e.getMessage());
            return 1;
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
        setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
        org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor =
            new org.lucee.lucli.ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());
        CommandLine picocli = new CommandLine(new org.lucee.lucli.LuCLI());
        picocli.setExpandAtFiles(false);

        StringOutput stringOutput = StringOutput.getInstance();
        boolean assertionFailed = false;
        boolean commandFailed = false;

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
            
            // Handle "source <file>" directive — load env file into script environment
            if (trimmed.toLowerCase().startsWith("source ")) {
                String sourceArg = trimmed.substring(7).trim();
                // Strip surrounding quotes
                if (sourceArg.length() >= 2) {
                    char fc = sourceArg.charAt(0);
                    char lc = sourceArg.charAt(sourceArg.length() - 1);
                    if ((fc == '"' && lc == '"') || (fc == '\'' && lc == '\'')) {
                        sourceArg = sourceArg.substring(1, sourceArg.length() - 1);
                    }
                }
                // Resolve placeholders in the path (e.g. source ${CONFIG_DIR}/.env)
                sourceArg = stringOutput.process(sourceArg);
                // Resolve relative to the script's directory
                Path scriptDir = path.toAbsolutePath().getParent();
                loadEnvFileIntoScript(Paths.get(sourceArg), scriptDir);
                continue;
            }

            // Check if this is a SET directive (case-insensitive)
            if (trimmed.toLowerCase().startsWith("set ")) {

                // Quick validation: must have '=' after 'set '
                if (trimmed.indexOf('=') < 1) {
                    StringOutput.Quick.error("SET directive syntax error: " + line);
                    commandFailed = true;
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
                        int assignmentExitCode = handleCommandSubstitutionAssignment(
                            key,
                            innerCommand,
                            commandProcessor,
                            externalCommandProcessor,
                            picocli,
                            stringOutput
                        );
                        if (assignmentExitCode != 0) {
                            commandFailed = true;
                        }
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
                        commandFailed = true;
                    }
                    continue;
                }
            }

            // Check for simple variable assignment with command substitution: NAME=$(command ...)
            java.util.regex.Matcher assignmentMatcher = assignmentPattern.matcher(trimmed);
            if (assignmentMatcher.matches()) {
                String varName = assignmentMatcher.group(1);
                String innerCommand = assignmentMatcher.group(2).trim();

                int assignmentExitCode = handleCommandSubstitutionAssignment(
                    varName,
                    innerCommand,
                    commandProcessor,
                    externalCommandProcessor,
                    picocli,
                    stringOutput
                );
                if (assignmentExitCode != 0) {
                    commandFailed = true;
                }
                continue;
            }

            // First resolve ${secret:NAME} placeholders using the LuCLI secrets store
            String lineWithSecrets = resolveSecretsInScriptLine(line);

            // Apply StringOutput placeholder processing
            String processedLine = stringOutput.process(lineWithSecrets);
            ScriptOutputRedirection redirection;
            try {
                redirection = parseScriptOutputRedirection(processedLine);
            } catch (IllegalArgumentException e) {
                StringOutput.Quick.error("Script redirection syntax error: " + e.getMessage());
                commandFailed = true;
                continue;
            }

            String commandToRun = redirection == null ? processedLine : redirection.commandLine;

            // Parse command into parts using the same parser as the terminal
            String[] parts = commandProcessor.parseCommand(commandToRun);
            if (parts.length == 0) {
                continue;
            }

            String command = parts[0].toLowerCase();

            // Built-in testing/assertion command: assert <actual> <expected>
            if ("assert".equals(command)) {
                String assertOutput;
                if (parts.length < 3) {
                    assertOutput = "❌ assert: usage: assert <actual> <expected>";
                    StringOutput.Quick.error("assert: usage: assert <actual> <expected>");
                } else {
                    String actual = parts[1];
                    String expected = parts[2];

                    if (actual.equals(expected)) {
                        assertOutput = "✅ assert passed: expected '" + expected + "'";
                    } else {
                        assertOutput = "❌ assert failed: expected '" + expected + "' but got '" + actual + "'";
                        assertionFailed = true;
                    }
                }

                if (redirection != null) {
                    try {
                        writeScriptRedirectOutput(redirection, assertOutput, commandProcessor);
                    } catch (Exception e) {
                        StringOutput.Quick.error("Error writing redirected output to '" + redirection.targetPath + "': " + e.getMessage());
                        debugStack(e);
                    }
                } else {
                    System.out.println(assertOutput);
                }
                continue;
            }

            // Delegate all other commands to the shared dispatcher
            ScriptCommandResult commandResult = executeLucliScriptCommand(
                commandToRun,
                commandProcessor,
                externalCommandProcessor,
                picocli,
                true
            );
            if (commandResult.exitCode != 0) {
                commandFailed = true;
            }
            String result = commandResult.output;

            if (redirection != null) {
                try {
                    writeScriptRedirectOutput(redirection, result, commandProcessor);
                } catch (Exception e) {
                    StringOutput.Quick.error("Error writing redirected output to '" + redirection.targetPath + "': " + e.getMessage());
                    debugStack(e);
                }
                continue;
            }

            if(result != null && !result.trim().isEmpty()) {
                recordLucliResult(result);
                // Todo: add this to our StringOutput system
                System.out.println(result);
                
            }

        }

        // Scripts are best-effort; fail overall if any assertion or command failed
        return (assertionFailed || commandFailed) ? 1 : 0;
    }

    static ScriptOutputRedirection parseScriptOutputRedirection(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }

            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }
            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (ch != '>' || inSingleQuotes || inDoubleQuotes) {
                continue;
            }

            boolean append = (i + 1 < line.length() && line.charAt(i + 1) == '>');
            int operatorLength = append ? 2 : 1;
            String commandPart = line.substring(0, i).trim();
            String targetPart = line.substring(i + operatorLength).trim();

            if (commandPart.isEmpty()) {
                throw new IllegalArgumentException("missing command before output redirection");
            }
            if (targetPart.isEmpty()) {
                throw new IllegalArgumentException("missing file path after output redirection");
            }

            String normalizedTarget = unquoteScriptRedirectionTarget(targetPart);
            if (normalizedTarget == null || normalizedTarget.isBlank()) {
                throw new IllegalArgumentException("missing file path after output redirection");
            }

            return new ScriptOutputRedirection(commandPart, normalizedTarget, append);
        }
        return null;
    }

    private static void writeScriptRedirectOutput(
        ScriptOutputRedirection redirection,
        String commandOutput,
        org.lucee.lucli.CommandProcessor commandProcessor
    ) throws Exception {
        Path outputPath = commandProcessor
            .getFileSystemState()
            .resolvePath(redirection.targetPath)
            .toAbsolutePath()
            .normalize();

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String content = commandOutput == null ? "" : commandOutput.trim();
        if (!content.isEmpty()) {
            content = content + System.lineSeparator();
        }

        if (redirection.append) {
            Files.writeString(
                outputPath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            return;
        }

        Files.writeString(
            outputPath,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private static String unquoteScriptRedirectionTarget(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    static final class ScriptOutputRedirection {
        final String commandLine;
        final String targetPath;
        final boolean append;

        ScriptOutputRedirection(String commandLine, String targetPath, boolean append) {
            this.commandLine = commandLine;
            this.targetPath = targetPath;
            this.append = append;
        }
    }

    static final class ScriptCommandResult {
        final String output;
        final int exitCode;

        ScriptCommandResult(String output, int exitCode) {
            this.output = output == null ? "" : output;
            this.exitCode = exitCode;
        }
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

    private static boolean startsWithLucliCommandPrefix(String line) {
        if (line == null) {
            return false;
        }
        if (line.length() < 5) {
            return false;
        }
        if (!line.regionMatches(true, 0, "lucli", 0, 5)) {
            return false;
        }
        return line.length() == 5 || Character.isWhitespace(line.charAt(5));
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
    private static int handleCommandSubstitutionAssignment(
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



        ScriptCommandResult commandResult = executeLucliScriptCommand(
            processedInner,
            commandProcessor,
            externalCommandProcessor,
            picocli,
            true
        );
        String captured = commandResult.output;
        if (commandResult.exitCode != 0) {
            return commandResult.exitCode;
        }

        debug("LuCLIScript", "Captured output for variable '" + varName + "': " + captured);

        scriptEnvironment.put(varName, captured);
        stringOutput.addPlaceholder(varName, captured);

        // Also update the generic result history so ${_} and last(n)
        // can see values coming from explicit assignments.
        recordLucliResult(captured);
        return 0;
    }
 
    /**
     * Shared dispatcher for executing a single script line in the LuCLI script environment.
     * This mirrors the behavior of the interactive terminal, with optional output capture
     * for use in variable assignment (e.g., FOO=$(command ...)).
     */
    private static ScriptCommandResult executeLucliScriptCommand(
        String processedLine,
        org.lucee.lucli.CommandProcessor commandProcessor,
        org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor,
        CommandLine picocli,
        boolean captureOutput) {

        // Normalise script line: if the user copied a full "lucli ..." command
        // into the script, strip the leading "lucli" so we don't recursively
        // invoke LuCLI from within itself.
        String scriptLine = processedLine == null ? "" : processedLine.trim();
        setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());

        // Should be fairly unreachable since this is pre-checked, but guard against empty lines
        if(scriptLine.trim().isEmpty()) {
            return new ScriptCommandResult("", 0);
        }


        if (startsWithLucliCommandPrefix(scriptLine)) {
            scriptLine = scriptLine.substring(5).trim();
            if (scriptLine.isEmpty()) {
                return new ScriptCommandResult("", 0); // bare "lucli"
            }
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
                    return new ScriptCommandResult("", 1);
                }

                // Call Lucee directly instead of going through picocli
                // This avoids issues with getExecutionResult() not propagating from subcommands
                Object result = LuceeScriptEngine.getInstance().eval(cfmlCode);
                return new ScriptCommandResult((result != null ? result.toString() : ""), 0);



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
                return new ScriptCommandResult("", 0);
            }

            String command = parts[0].toLowerCase();

            // 1) Picocli subcommands (server, modules, run, etc.)
            if (picocli.getSubcommands().containsKey(command)) {
                if (captureOutput) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    java.io.PrintStream originalOut = System.out;
                    java.io.PrintStream originalErr = System.err;
                    int exitCode;
                    try {
                        System.setOut(new java.io.PrintStream(baos));
                        System.setErr(new java.io.PrintStream(baos));
                        exitCode = picocli.execute(parts); // Picocli writes directly to System.out/err
                        setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                    String captured = baos.toString().trim();
                    if (captured != null && !captured.isEmpty()) {
                        recordLucliResult(captured);
                    }
                    return new ScriptCommandResult(captured, exitCode);
                } else {
                    int exitCode = picocli.execute(parts);
                    setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
                    return new ScriptCommandResult("", exitCode);
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
                        setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
                    } finally {
                        System.setOut(originalOut);
                        System.setErr(originalErr);
                    }
                    String captured = baos.toString().trim();
                    if (captured != null && !captured.isEmpty()) {
                        recordLucliResult(captured);
                    }
                    return new ScriptCommandResult(captured, 0);
                } else {
                    org.lucee.lucli.modules.ModuleCommand.executeModuleByName(command, moduleArgs);
                    setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
                    return new ScriptCommandResult("", 0);
                }
            }

            // 3) File-system style commands (ls, cd, rm, etc.)
            if (isFileSystemStyleCommand(command)) {
                String fsResult = commandProcessor.executeCommand(scriptLine);
                setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
                if (fsResult != null) {
                    recordLucliResult(fsResult);
                }
                if (captureOutput) {
                    return new ScriptCommandResult(fsResult != null ? fsResult : "", 0);
                } else {
                    if (fsResult != null && !fsResult.isEmpty()) {
                        System.out.println(fsResult);
                    }
                    return new ScriptCommandResult("", 0);
                }
            }

            // 4) Fallback to external command processor (git, echo, etc.)
            String extResult = externalCommandProcessor.executeCommand(scriptLine);
            setRuntimeCwd(commandProcessor.getFileSystemState().getCurrentWorkingDirectory());
            if (extResult != null) {
                recordLucliResult(extResult);
            }
            if (captureOutput) {
                return new ScriptCommandResult(extResult != null ? extResult : "", 0);
            } else {
                if (extResult != null && !extResult.isEmpty()) {
                    System.out.println(extResult);
                }
                return new ScriptCommandResult("", 0);
            }

        } catch (Exception e) {
            StringOutput.Quick.error("Error executing script line '" + processedLine + "': " + e.getMessage());
            debugStack(e);
            return new ScriptCommandResult("", 1);
        }
    }









}
