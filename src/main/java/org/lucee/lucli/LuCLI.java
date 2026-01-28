package org.lucee.lucli;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lucee.lucli.cli.LuCLICommand;
import org.lucee.lucli.secrets.LocalSecretStore;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

import picocli.CommandLine;

public class LuCLI {

    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;
    public static boolean preserveWhitespace = false;
    private static boolean lucliScript = false;
    
    public static Map<String, String> scriptEnvironment = new HashMap<>(System.getenv());
    
    // Result history for .lucli scripts and LuCLI batch execution
    // We keep the last few command outputs so scripts can reference them
    // via placeholders like ${_} and ${last(2)}.
    private static final java.util.List<String> lucliResultHistory = new java.util.ArrayList<>();
    private static final int LUCLI_RESULT_HISTORY_LIMIT = 20;

    // Secret placeholder support for .lucli scripts: ${secret:NAME}
    private static final Pattern LUCLI_SECRET_PATTERN = Pattern.compile("\\$\\{secret:([^}]+)\\}");
    private static SecretStore lucliScriptSecretStore;
    private static boolean lucliScriptSecretsInitialized = false;
    

    public static void main(String[] args) throws Exception {
        // Check for timing flag early and enable Timer singleton
        timing = java.util.Arrays.asList(args).contains("--timing") || java.util.Arrays.asList(args).contains("-t");
        Timer.setEnabled(timing);
        
        // Create Picocli CommandLine with our main command
        CommandLine cmd = new CommandLine(new LuCLICommand());
        
        // Ensure help/usage output goes to the expected streams.
        // (This also keeps `--help | grep ...` style usage working.)
        cmd.setOut(new PrintWriter(System.out, true));
        cmd.setErr(new PrintWriter(System.err, true));
        
        // Set custom execution strategy to automatically time all commands
        cmd.setExecutionStrategy(new TimingExecutionStrategy());
        // Set custom execution strategy to set global flags
        cmd.setExecutionStrategy(new GlobalOptionSettingExecutionStrategy());

        // Configure CommandLine behavior
        cmd.setExecutionExceptionHandler(new CommandLine.IExecutionExceptionHandler() {
            @Override
            public int handleExecutionException(Exception ex,
                                                CommandLine commandLine,
                                                CommandLine.ParseResult parseResult) throws Exception {
                StringOutput.Quick.error("Error: " + ex.getMessage());
                if (verbose || debug) {
                    ex.printStackTrace();
                }
                return 1;
            }
        });
        
        // Handle parameter exceptions (like unknown options) more gracefully
        cmd.setParameterExceptionHandler(new CommandLine.IParameterExceptionHandler() {
            @Override
            public int handleParseException(CommandLine.ParameterException ex, String[] args) {
                // Extract debug, verbose, and timing flags from args for shortcut handling
                boolean shortcutDebug = java.util.Arrays.asList(args).contains("--debug") || java.util.Arrays.asList(args).contains("-d");
                boolean shortcutVerbose = java.util.Arrays.asList(args).contains("--verbose") || java.util.Arrays.asList(args).contains("-v");
                boolean shortcutTiming = java.util.Arrays.asList(args).contains("--timing") || java.util.Arrays.asList(args).contains("-t");
                boolean preserveWhitespace = java.util.Arrays.asList(args).contains("--whitespace") || java.util.Arrays.asList(args).contains("-w");
                // set the defaults
                LuCLI.verbose = shortcutVerbose;
                LuCLI.debug = shortcutDebug;
                LuCLI.timing = shortcutTiming;
                LuCLI.preserveWhitespace = preserveWhitespace;
                // We should clean them up before we send the rest along

                Timer.setEnabled(LuCLI.timing);
                // Check if this might be a shortcut (module or CFML file)
                // BUT ONLY if it's not a recognized subcommand
                if (ex instanceof CommandLine.UnmatchedArgumentException && args.length >= 1) {
                    // Find the first non-flag argument
                    String firstArg = null;
                    int firstArgIndex = -1;
                    for (int i = 0; i < args.length; i++) {
                        if (!args[i].startsWith("-")) {
                            firstArg = args[i];
                            firstArgIndex = i;
                            break;
                        }
                    }
                    
                    // Skip shortcuts if the first argument is a known subcommand
                    if (firstArg != null && !isKnownSubcommand(firstArg)) {
                        java.io.File file = new java.io.File(firstArg);
                        
                        // Extract remaining arguments (after the first non-flag arg), filtering out known flags
                        java.util.List<String> remainingArgsList = new java.util.ArrayList<>();
                        for (int i = firstArgIndex + 1; i < args.length; i++) {
                            String arg = args[i];
                            // Skip known flags that should be handled at the root level
                            if (!arg.equals("--verbose") && !arg.equals("-v") &&
                                !arg.equals("--debug") && !arg.equals("-d") &&
                                !arg.equals("--whitespace") && !arg.equals("-w") &&
                                !arg.equals("--timing") && !arg.equals("-t")) {
                                remainingArgsList.add(arg);
                            }
                        }
                        String[] remainingArgs = remainingArgsList.toArray(new String[0]);
                        
                        // Check if it's a LuCLI script file
                        if (file.exists() && firstArg.endsWith(".lucli")) {
                            try {
                                 Timer.start("LuCLI Script Shortcut Execution (" + firstArg + ")");
                                 int ret = executeLucliScript(firstArg);
                                 Timer.stop("LuCLI Script Shortcut Execution (" + firstArg + ")");
                                 return ret;
                            } catch (Exception scriptEx) {
                                // If script execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("LuCLI script execution failed: " + scriptEx.getMessage());
                                }
                                return 1;
                            }
                        }
                        // Check if it's an existing CFML file
                        else if (file.exists() && (firstArg.endsWith(".cfs") || firstArg.endsWith(".cfm") || firstArg.endsWith(".cfml"))) {
                            try {
                                Timer.start("CFML File Shortcut Execution");
                                int ret = executeCfmlFileShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug, shortcutTiming);
                                Timer.stop("CFML File Shortcut Execution");
                                return ret;
                            } catch (Exception cfmlEx) {
                                // If CFML file execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("CFML file execution failed: " + cfmlEx.getMessage());
                                }
                            }
                        }
                        
                        else if( file.exists() && (firstArg.endsWith(".cfc")) ) {
                            try {
                                return executeCfmlFileShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug, shortcutTiming);
                            } catch (Exception cfmlEx) {
                                // If CFML file execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("CFML file execution failed: " + cfmlEx.getMessage());
                                }
                            }
                        }
                        // IF it is a real file but we dont know what to do with it... 

                        // If it's not an existing file, try to execute as module shortcut
                        else if (!file.exists()) {
                            try {
                                return executeModuleShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug, shortcutTiming);
                            } catch (Exception moduleEx) {
                                // If module execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("Module shortcut failed: " + moduleEx.getMessage());
                                }
                            }
                        }
                    }
                }
                
                // Default error handling
                CommandLine commandLine = ex.getCommandLine();
                CommandLine.UnmatchedArgumentException.printSuggestions(ex, commandLine.getErr());
                commandLine.usage(commandLine.getErr());
                return commandLine == cmd ? 2 : 1;
            }
        });
        
        // Execute the command with optional global timeout and exit with the returned code
        int parsedTimeoutSeconds = 0;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--timeout".equals(a) && i + 1 < args.length) {
                try {
                    parsedTimeoutSeconds = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {
                    // Ignore invalid timeout; treat as 0 (no timeout)
                }
            } else if (a.startsWith("--timeout=")) {
                try {
                    parsedTimeoutSeconds = Integer.parseInt(a.substring("--timeout=".length()));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid timeout; treat as 0 (no timeout)
                }
            }
        }

        int exitCode;
        if (parsedTimeoutSeconds > 0) {
            java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<Integer> future =
                    executor.submit(() -> cmd.execute(args));
                exitCode = future.get(parsedTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                System.err.println(
                    "Command timed out after " + parsedTimeoutSeconds
                    + " seconds (use --timeout 0 to disable).");
                exitCode = 124;
            } finally {
                executor.shutdownNow();
            }
        } else {
            exitCode = cmd.execute(args);
        }
        
        Timer.printResults();
        System.exit(exitCode);
    }
    
        /**
     * Print a message only if verbose mode is enabled
     * @param message The message to print
     */
    public static void printVerbose(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
    
    /**
     * Print a debug message only if debug mode is enabled
     * Debug messages go to stderr with [DEBUG] prefix
     * @param message The message to print
     */
    public static void printDebug(String message) {
        if (debug) {
            System.err.println("[DEBUG] " + message);
        }
    }
    
    /**
     * Print a debug message with context only if debug mode is enabled
     * @param context The context (e.g., class name or method name)
     * @param message The message to print
     */
    public static void printDebug(String context, String message) {
        if (debug) {
            System.err.println("[DEBUG " + context + "] " + message);
        }
    }
    
    /**
     * Print an info message (always shown, but can be used for consistency)
     * @param message The message to print
     */
    public static void printInfo(String message) {
        System.out.println(message);
    }
    
    /**
     * Print an error message
     * @param message The error message
     */
    public static void printError(String message) {
        System.err.println(message);
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
    public static void printDebugStackTrace(Exception e) {
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
        return command.equals("ls") || command.equals("dir") ||
               command.equals("cd") || command.equals("pwd") ||
               command.equals("mkdir") || command.equals("rmdir") ||
               command.equals("rm") || command.equals("cp") ||
               command.equals("mv") || command.equals("cat") ||
               command.equals("touch") || command.equals("find") ||
               command.equals("wc") || command.equals("head") ||
               command.equals("tail") || command.equals("prompt") ||
               command.equals("edit") || command.equals("interactive") ||
               command.equals("cflint");
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

        java.nio.file.Path path = java.nio.file.Paths.get(scriptPath);
        if (!java.nio.file.Files.exists(path)) {
            StringOutput.Quick.error("Script not found: " + scriptPath);
            throw new java.io.FileNotFoundException("Script file not found: " + scriptPath);
        }

        printVerbose("Executing LuCLI script: " + scriptPath);

        // Read all lines from the script file
        java.util.List<String> lines = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            printVerbose("Script is empty: " + scriptPath);
            return 0;
        }

        // Set up a lightweight command environment similar to Terminal.dispatchCommand
        org.lucee.lucli.CommandProcessor commandProcessor = new org.lucee.lucli.CommandProcessor();
        org.lucee.lucli.ExternalCommandProcessor externalCommandProcessor =
            new org.lucee.lucli.ExternalCommandProcessor(commandProcessor, commandProcessor.getSettings());
        CommandLine picocli = new CommandLine(new org.lucee.lucli.cli.LuCLICommand());

        StringOutput stringOutput = StringOutput.getInstance();
        boolean assertionFailed = false;

        // Pattern for simple variable assignments with command substitution: NAME=$(command ...)
        java.util.regex.Pattern assignmentPattern = java.util.regex.Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\$\\((.*)\\)\\s*$");

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
                printDebug("LuCLIScript", "Skipping comment line: " + line);
                continue;
            }

            // Check if this is a SET directive (case-insensitive)
            if (trimmed.toLowerCase().startsWith("set ")) {
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
                    
                    printDebug("SET directive: " + key + " = " + value);
                    
                    scriptEnvironment.put(key, value);
                    stringOutput.addPlaceholder(key, value);
                    continue;
                }
            }

            // Check for simple variable assignment with command substitution: NAME=$(command ...)
            java.util.regex.Matcher assignmentMatcher = assignmentPattern.matcher(trimmed);
            if (assignmentMatcher.matches()) {
                String varName = assignmentMatcher.group(1);
                String innerCommand = assignmentMatcher.group(2).trim();

                // Resolve ${secret:NAME} placeholders before normal processing
                String innerWithSecrets = resolveSecretsInScriptLine(innerCommand);

                // Process placeholders inside the command before execution
                String processedInner = stringOutput.process(innerWithSecrets);

                String captured = executeLucliScriptCommand(
                    processedInner,
                    commandProcessor,
                    externalCommandProcessor,
                    picocli,
                    true
                );

                printDebug("LuCLIScript", "Captured output for variable '" + varName + "': " + captured);

                scriptEnvironment.put(varName, captured);
                stringOutput.addPlaceholder(varName, captured);

                // Also update the generic result history so ${_} and last(n)
                // can see values coming from explicit assignments.
                recordLucliResult(captured);
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
        }

        // Scripts are best-effort; fail overall if any assertion failed
        return assertionFailed ? 1 : 0;
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
        if (!scriptLine.isEmpty()) {
            // Split minimally to inspect the first token
            String[] firstTokens = commandProcessor.parseCommand(scriptLine);
            if (firstTokens.length > 0 && "lucli".equalsIgnoreCase(firstTokens[0])) {
                // Remove the leading "lucli" token and re-join the rest as the
                // effective script line. This allows lines like
                // "lucli server start" to behave the same as "server start"
                // inside .lucli scripts.
                if (firstTokens.length == 1) {
                    // Line was just "lucli"; nothing meaningful to execute.
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < firstTokens.length; i++) {
                    if (i > 1) sb.append(' ');
                    sb.append(firstTokens[i]);
                }
                scriptLine = sb.toString();
            }
        }

        // Parse command into parts using the same parser as the terminal
        String[] parts = commandProcessor.parseCommand(scriptLine);
        if (parts.length == 0) {
            return "";
        }

        String command = parts[0].toLowerCase();

        try {
            // 1) Picocli subcommands (server, modules, cfml, run, etc.)
            if (picocli.getSubcommands().containsKey(command)) {
                if (captureOutput) {
                    // Only redirect System.out/err when we actually need to capture.
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
                    return captured;
                } else {
                    // Normal script line: let Picocli write directly to stdout/stderr.
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
                    // Normal script invocation: run the module and let it print directly.
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
            // Log script-level errors but continue with subsequent lines
            StringOutput.Quick.error("Error executing script line '" + processedLine + "': " + e.getMessage());
            printDebugStackTrace(e);
            return "";
        }
    }
    
    /**
     * Check if the given string is a known subcommand
     * Uses PicocLI's API to query registered subcommands dynamically
     * @param command The command to check
     * @return true if it's a known subcommand, false otherwise
     */
    private static boolean isKnownSubcommand(String command) {
        // Query PicocLI for registered subcommands instead of maintaining a manual list
        CommandLine cmd = new CommandLine(new LuCLICommand());
        return cmd.getSubcommands().containsKey(command);
    }
    
    /**
     * Execute a module shortcut by redirecting to "modules run <moduleName> [args...]"
     * @param moduleName The name of the module to run
     * @param args Additional arguments to pass to the module
     * @param verbose Enable verbose output
     * @param debug Enable debug output
     * @param timing Enable timing output
     * @return Exit code from module execution
     */
    private static int executeModuleShortcut(String moduleName, String[] args, boolean verbose, boolean debug, boolean timing) throws Exception {
        // Set global flags
        LuCLI.verbose = verbose;
        LuCLI.debug = debug;
        LuCLI.timing = timing;
        
        printVerbose("Executing module shortcut: " + moduleName + 
            " (equivalent to 'lucli modules run " + moduleName + "')");
        
        // Build the new arguments array: [flags..., "modules", "run", moduleName, ...additionalArgs]
        // Flags must come BEFORE the subcommand, not after
        java.util.List<String> newArgs = new java.util.ArrayList<>();
        
        // Add flags first (before subcommands)
        if (verbose) {
            newArgs.add("--verbose");
        }
        if (debug) {
            newArgs.add("--debug");
        }
        if (timing) {
            newArgs.add("--timing");
        }
        
        newArgs.add("modules");
        newArgs.add("run");
        newArgs.add(moduleName);
        
        // Add any additional arguments
        if (args != null && args.length > 0) {
            for (String arg : args) {
                newArgs.add(arg);
            }
        }
        
        // Create a new CommandLine instance with the same configuration
        CommandLine cmd = new CommandLine(new org.lucee.lucli.cli.LuCLICommand());

        // Allow unknown options (like module-specific flags) to be treated as positional
        // arguments so they can be forwarded to the module implementation instead of
        // causing an "Unknown option" error at the LuCLI level.
        // cmd.setUnmatchedArgumentsAllowed(true);
        
        
        // Configure the same exception handlers
        cmd.setExecutionExceptionHandler(new CommandLine.IExecutionExceptionHandler() {
            @Override
            public int handleExecutionException(Exception ex,
                                                CommandLine commandLine,
                                                CommandLine.ParseResult parseResult) throws Exception {
                StringOutput.Quick.error("Error: " + ex.getMessage());
                printDebugStackTrace(ex);
                return 1;
            }
        });
        
        // Execute the modules run command
        return cmd.execute(newArgs.toArray(new String[0]));
    }
    
    /**
     * Execute a CFML file shortcut by delegating to the Picocli RunCommand.
     *
     * This is used by the root command's shortcut handling so that invocations like
     * `lucli somefile.cfm` or `lucli SomeComponent.cfc arg1` are treated as if the
     * user had explicitly run `lucli run ...`.
     */
    private static int executeCfmlFileShortcut(String filePath, String[] args, boolean verbose, boolean debug, boolean timing) throws Exception {
        // Root/main is responsible for initializing global flags; here we only
        // reconstruct the argument vector for Picocli.

        // Build arguments for the Picocli command: [global flags..., "run", filePath, args...]
        java.util.List<String> newArgs = new java.util.ArrayList<>();
        if (verbose) {
            newArgs.add("--verbose");
        }
        if (debug) {
            newArgs.add("--debug");
        }
        if (timing) {
            newArgs.add("--timing");
        }

        newArgs.add("run");
        newArgs.add(filePath);

        if (args != null && args.length > 0) {
            for (String arg : args) {
                newArgs.add(arg);
            }
        }

        // Delegate to a new Picocli CommandLine instance so we reuse the RunCommand
        CommandLine cmd = new CommandLine(new org.lucee.lucli.cli.LuCLICommand());

        cmd.setExecutionExceptionHandler(new CommandLine.IExecutionExceptionHandler() {
            @Override
            public int handleExecutionException(Exception ex,
                                                CommandLine commandLine,
                                                CommandLine.ParseResult parseResult) throws Exception {
                StringOutput.Quick.error("Error: " + ex.getMessage());
                if (verbose || debug) {
                    ex.printStackTrace();
                }
                return 1;
            }
        });

        return cmd.execute(newArgs.toArray(new String[0]));
    }
}
