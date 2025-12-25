package org.lucee.lucli;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.lucee.lucli.cli.LuCLICommand;

import picocli.CommandLine;

public class LuCLI {

    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;
    private static boolean lucliScript = false;

    public static Map<String, String> scriptEnvironment = new HashMap<>(System.getenv());
    
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
                
                // set the defaults
                LuCLI.verbose = shortcutVerbose;
                LuCLI.debug = shortcutDebug;
                LuCLI.timing = shortcutTiming;
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
                                !arg.equals("--timing") && !arg.equals("-t")) {
                                remainingArgsList.add(arg);
                            }
                        }
                        String[] remainingArgs = remainingArgsList.toArray(new String[0]);
                        
                        // Check if it's a LuCLI script file
                        if (file.exists() && firstArg.endsWith(".lucli")) {
                            try {
                                 Timer.start("LuCLI Script Shortcut Execution (" + firstArg + ")");
                                 int ret = executeLucliScript(firstArg, shortcutVerbose, shortcutDebug, shortcutTiming);
                                 Timer.stop("LuCLI Script Shortcut Execution (" + firstArg + ")");
                                 return ret;
                            } catch (Exception scriptEx) {
                                // If script execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("LuCLI script execution failed: " + scriptEx.getMessage());
                                }
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
                                LuceeScriptEngine engine  = LuceeScriptEngine.getInstance(shortcutVerbose, shortcutDebug);
                                return engine.executeScript(firstArg, remainingArgs);
                                // return executeCfmlFileShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug, shortcutTiming);
                            } catch (Exception cfmlEx) {
                                // If CFML file execution fails, fall through to normal error handling
                                // if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("CFML file execution failed: " + cfmlEx.getMessage());
                                    return 1;
                                // }
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
        
        // Execute the command and exit with the returned code
        int exitCode = cmd.execute(args);
        
        Timer.printResults();
        System.exit(exitCode);
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
        
        // ASCII art banner
        info.append("\n");
        info.append(" _           ____ _     ___ \n");
        info.append("| |   _   _ / ___| |   |_ _|\n");
        info.append("| |  | | | | |   | |    | | \n");
        info.append("| |__| |_| | |___| |___ | | \n");
        info.append("|_____\\__,_|\\____|_____|___|\n");
        info.append("\n");
        
        // Version information
        info.append("Version: ").append(getVersion()).append("\n");
        
        if (includeLucee) {
            try {
                String luceeVersion = LuceeScriptEngine.getInstance(false, false).getVersion();
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
     * Execute a .lucli script file line by line
     * Each line is a command that gets executed individually
     */
    private static int executeLucliScript(String scriptPath, boolean verbose, boolean debug, boolean timing) throws Exception {
        // Set global flags
        LuCLI.verbose = verbose;
        LuCLI.debug = debug;
        LuCLI.timing = timing;
        LuCLI.lucliScript = true;

        java.nio.file.Path path = java.nio.file.Paths.get(scriptPath);
        if (!java.nio.file.Files.exists(path)) {
            StringOutput.Quick.error("Script not found: " + scriptPath);
            return 1;
        }

        printVerbose("Executing LuCLI script: " + scriptPath);

        // Read all lines from the script file
        java.util.List<String> lines = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            printVerbose("Script is empty: " + scriptPath);
            return 0;
        }

        java.util.List<String> processedLines = new java.util.ArrayList<>();
        StringOutput stringOutput = StringOutput.getInstance();
        for (String line : lines) {
            if (line == null) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                // Skip blank lines in scripts
                continue;
            }

            

            // Check if this is a SET directive (case-insensitive)
            if (line.trim().toLowerCase().startsWith("set ")) {
                // Parse: set KEY=VALUE or set KEY="VALUE" or set KEY='VALUE'
                // Remove "set " prefix (case-insensitive)
                String afterSet = line.trim().substring(4).trim();
                
                // // Split on first '=' to get key and value
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

            String processedLine = stringOutput.process(line);
            processedLines.add(processedLine);

           
        }
        
        // Execute using Terminal's script mode
        // This redirects stdin to feed the processed script lines
        String content = String.join("\n", processedLines) + "\n";

        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Terminal.main(new String[0]);
            return 0; // Success if no exception thrown
        } catch (Exception e) {
            StringOutput.Quick.error("Error executing script: " + e.getMessage());
            printDebugStackTrace(e);
            return 1;
        } finally {
            System.setIn(originalIn);

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
     * Execute a CFML file shortcut by using LuceeScriptEngine.executeScript
     * @param filePath The path to the CFML file to execute
     * @param args Additional arguments to pass to the script
     * @param verbose Enable verbose output
     * @param debug Enable debug output
     * @param timing Enable timing output
     * @return Exit code from file execution
     */
    private static int executeCfmlFileShortcut(String filePath, String[] args, boolean verbose, boolean debug, boolean timing) throws Exception {
        // Set global flags
        LuCLI.verbose = verbose;
        LuCLI.debug = debug;
        LuCLI.timing = timing;
        
        // Initialize timing if requested
        Timer.setEnabled(timing);
        Timer.start("CFML File Execution");
        
        printVerbose("Executing CFML file: " + filePath);
        
        // Check if file exists and is a CFML file
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(path)) {
            StringOutput.Quick.error("File not found: " + filePath);
            return 1;
        }
        
        if (java.nio.file.Files.isDirectory(path)) {
            StringOutput.Quick.error("'" + filePath + "' is a directory");
            return 1;
        }
        
        // Check if file has a supported CFML extension
        String fileName = path.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".cfm") && !fileName.endsWith(".cfc") && !fileName.endsWith(".cfs")) {
            StringOutput.Quick.error("'" + filePath + "' is not a CFML file (.cfm, .cfc, or .cfs)");
            return 1;
        }
        
        try {
            // Get or create the LuceeScriptEngine instance
            Timer.start("Lucee Engine Initialization");
            LuceeScriptEngine luceeEngine = LuceeScriptEngine.getInstance(verbose, debug);
            Timer.stop("Lucee Engine Initialization");
            
            // For .cfs files, we need to set up the ARGS array manually since setupScriptContext is disabled
            if (fileName.endsWith(".cfs")) {
                Timer.start("Script Preparation");
                // Read the file content
                String fileContent = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                
                // Create script with built-in variables and ARGS array setup
                StringBuilder scriptWithArgs = new StringBuilder();
                
                // Add built-in variables setup
                try {
                    org.lucee.lucli.BuiltinVariableManager variableManager = org.lucee.lucli.BuiltinVariableManager.getInstance(verbose, debug);
                    String builtinSetup = variableManager.createVariableSetupScript(filePath, args);
                    scriptWithArgs.append(builtinSetup);
                    scriptWithArgs.append("\n");
                } catch (Exception e) {
                    printDebug("Warning: Failed to inject built-in variables: " + e.getMessage());
                }
                
                // Add ARGS array setup for backward compatibility
                scriptWithArgs.append("// Auto-generated ARGS array setup\n");
                scriptWithArgs.append("ARGS = ['" + filePath + "'");
                if (args != null && args.length > 0) {
                    for (String arg : args) {
                        scriptWithArgs.append(", '" + arg.replace("'", "''") + "'");
                    }
                }
                scriptWithArgs.append("];\n\n");
                scriptWithArgs.append(fileContent);
                Timer.stop("Script Preparation");
                
                printDebug("Script with ARGS setup:\n" + scriptWithArgs.toString() + "\n[DEBUG] End of script");
                
                // Execute the wrapped script content with built-in variables
                Timer.start("Script Execution");
                luceeEngine.evalWithBuiltinVariables(scriptWithArgs.toString(), filePath, args);
                Timer.stop("Script Execution");
            } else {
                // For .cfm and .cfc files, use the existing method
                Timer.start("Script Execution");
                luceeEngine.executeScript(path.toAbsolutePath().toString(), args);
                Timer.stop("Script Execution");
            }
            
            // Success
            return 0;
            
        } catch (Exception e) {
            StringOutput.Quick.error("Error executing CFML script '" + filePath + "': " + e.getMessage());
            printDebugStackTrace(e);
            return 1;
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("CFML File Execution");
            Timer.printResults();
        }
    }
}
