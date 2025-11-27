package org.lucee.lucli;

import org.lucee.lucli.cli.LuCLICommand;

import picocli.CommandLine;

public class LuCLI {

    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;
    private static boolean lucliScript = false;
    
    /**
     * Check if we're running in script mode (executing a .lucli file)
     * This is used to suppress prompts and other interactive elements
     */
    public static boolean isLucliScript() {
        return lucliScript;
    }

    public static void main(String[] args) throws Exception {
        // Create Picocli CommandLine with our main command
        CommandLine cmd = new CommandLine(new LuCLICommand());
        
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
                                return executeLucliScript(firstArg, shortcutVerbose, shortcutDebug, shortcutTiming);
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
                                return executeCfmlFileShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug, shortcutTiming);
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
        System.exit(exitCode);
    }
    
	public static String getVersionInfo() {
        String version = getVersion();
        return "LuCLI " + version;
    }
    
    /**
     * Get both LuCLI and Lucee version information
     */
    public static String getFullVersionInfo() {
        StringBuilder versionInfo = new StringBuilder();
        versionInfo.append(getVersionInfo());
        
        try {
            String luceeVersion = getLuceeVersionInfo();
            if (luceeVersion != null) {
                versionInfo.append("\n").append(luceeVersion);
            }
        } catch (Exception e) {
            versionInfo.append("\nLucee Version: Error retrieving version - ").append(e.getMessage());
        }
        
        return versionInfo.toString();
    }
    
    /**
     * Get Lucee version information
     * @return the version of lucee the lucli is running by default
     */
    public static String getLuceeVersionInfo() throws Exception {
    	String lucee_version = LuceeScriptEngine.getInstance(false,false).getVersion();
        return "Lucee Version: " + lucee_version;
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

        if (verbose) {
            StringOutput.Quick.info("Executing LuCLI script: " + scriptPath);
        }

        // Read all lines from the script file
        java.util.List<String> lines = java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            if (verbose) {
                StringOutput.Quick.info("Script is empty: " + scriptPath);
            }
            return 0;
        }

        // Script-scoped variables defined via "Set NAME=\"value\"" directives.
        // java.util.Map<String,String> scriptVars = new java.util.HashMap<>();
        // Make them visible as script-local environment overrides for this thread.
        
        // java.util.regex.Pattern setPattern = java.util.regex.Pattern.compile("(?i)^\\s*set\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");
        // java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");

        // Helper to expand ${VAR} from scriptVars only.
        // Function<String,String> applyScriptVars = (text) -> {
        //     if (text == null || text.isEmpty()) {
        //         return text;
        //     }
        //     java.util.regex.Matcher m = placeholderPattern.matcher(text);
        //     StringBuffer sb = new StringBuffer();
        //     while (m.find()) {
        //         String key = m.group(1);
        //         String replacement = scriptVars.get(key);
        //         if (replacement != null) {
        //             m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        //         } else {
        //             // leave placeholder as-is
        //             m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(0)));
        //         }
        //     }
        //     m.appendTail(sb);
        //     return sb.toString();
        // };

        // Process each line:
        //  - handle "Set VAR=..." directives
        //  - expand ${VAR} from scriptVars
        //  - then run through StringOutput for global placeholders/env/etc.
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

             String processedLine = stringOutput.process(line);
            processedLines.add(processedLine);
            // java.util.regex.Matcher setMatcher = setPattern.matcher(trimmed);
            // if (setMatcher.matches()) {
            //     String varName = setMatcher.group(1);
            //     String rawValue = setMatcher.group(2).trim();

            //     // Remove optional surrounding single or double quotes
            //     if (rawValue.length() >= 2 &&
            //         ((rawValue.startsWith("\"") && rawValue.endsWith("\"")) ||
            //          (rawValue.startsWith("'") && rawValue.endsWith("'")))) {
            //         rawValue = rawValue.substring(1, rawValue.length() - 1);
            //     }

            //     // Allow previously defined script variables in the value.
            //     String expandedValue = applyScriptVars.apply(rawValue);
            //     scriptVars.put(varName, expandedValue);
            //     // 'Set' lines are directives, not commands to execute
            //     continue;
            // }

            // // First expand script variables, then global placeholders
            // String withScriptVars = applyScriptVars.apply(line);
           
        }
        
        // Execute using InteractiveTerminal's script mode
        // This redirects stdin to feed the processed script lines
        String content = String.join("\n", processedLines) + "\n";

        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            InteractiveTerminal.main(new String[0]);
            return 0; // Success if no exception thrown
        } catch (Exception e) {
            StringOutput.Quick.error("Error executing script: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return 1;
        } finally {
            System.setIn(originalIn);

        }
    }
    
    /**
     * Check if the given string is a known subcommand
     * @param command The command to check
     * @return true if it's a known subcommand, false otherwise
     */
    private static boolean isKnownSubcommand(String command) {
        // List of known subcommands that should not be treated as shortcuts
        return "server".equals(command) || 
               "modules".equals(command) || 
               "module".equals(command) || 
               "cfml".equals(command) || 
               "help".equals(command) ||
               "terminal".equals(command);
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
        
        if (verbose || debug) {
            StringOutput.Quick.info("Executing module shortcut: " + moduleName + 
                " (equivalent to 'lucli modules run " + moduleName + "')");
        }
        
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
        
        // Configure the same exception handlers
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
        
        if (verbose || debug) {
            StringOutput.Quick.info("Executing CFML file: " + filePath);
        }
        
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
                    if (debug) {
                        System.err.println("Warning: Failed to inject built-in variables: " + e.getMessage());
                    }
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
                
                if (debug) {
                    System.err.println("[DEBUG] Script with ARGS setup:");
                    System.err.println(scriptWithArgs.toString());
                    System.err.println("[DEBUG] End of script");
                }
                
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
            if (debug) {
                e.printStackTrace();
            }
            return 1;
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("CFML File Execution");
            Timer.printResults();
        }
    }
}
