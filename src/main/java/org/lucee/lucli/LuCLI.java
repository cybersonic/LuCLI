package org.lucee.lucli;

import picocli.CommandLine;
import org.lucee.lucli.cli.LuCLICommand;

public class LuCLI {

    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;

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
                // Extract debug and verbose flags from args for shortcut handling
                boolean shortcutDebug = java.util.Arrays.asList(args).contains("--debug") || java.util.Arrays.asList(args).contains("-d");
                boolean shortcutVerbose = java.util.Arrays.asList(args).contains("--verbose") || java.util.Arrays.asList(args).contains("-v");
                
                // Check if this might be a shortcut (module or CFML file)
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
                    
                    // Skip if no non-flag argument found
                    if (firstArg == null) {
                        // Fall through to default error handling
                    } else {
                        java.io.File file = new java.io.File(firstArg);
                        
                        // Extract remaining arguments (after the first non-flag arg)
                        String[] remainingArgs = java.util.Arrays.copyOfRange(args, firstArgIndex + 1, args.length);
                        
                        // Check if it's an existing CFML file
                        if (file.exists() && (firstArg.endsWith(".cfs") || firstArg.endsWith(".cfm") || firstArg.endsWith(".cfml"))) {
                            try {
                                return executeCfmlFileShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug);
                            } catch (Exception cfmlEx) {
                                // If CFML file execution fails, fall through to normal error handling
                                if (shortcutVerbose || shortcutDebug) {
                                    StringOutput.Quick.error("CFML file execution failed: " + cfmlEx.getMessage());
                                }
                            }
                        }
                        // If it's not an existing file, try to execute as module shortcut
                        else if (!file.exists()) {
                            try {
                                return executeModuleShortcut(firstArg, remainingArgs, shortcutVerbose, shortcutDebug);
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
     */
    public static String getLuceeVersionInfo() throws Exception {
        LuceeScriptEngine engine = LuceeScriptEngine.getInstance(false, false);
        engine.eval("version = SERVER.LUCEE.version");
        Object version = engine.getEngine().get("version");
        return "Lucee Version: " + version;
    }
    
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
     * Execute a module shortcut by redirecting to "modules run <moduleName> [args...]"
     * @param moduleName The name of the module to run
     * @param args Additional arguments to pass to the module
     * @param verbose Enable verbose output
     * @param debug Enable debug output  
     * @return Exit code from module execution
     */
    private static int executeModuleShortcut(String moduleName, String[] args, boolean verbose, boolean debug) throws Exception {
        if (verbose || debug) {
            StringOutput.Quick.info("Executing module shortcut: " + moduleName + 
                " (equivalent to 'lucli modules run " + moduleName + "')");
        }
        
        // Build the new arguments array: ["modules", "run", moduleName, ...additionalArgs]
        java.util.List<String> newArgs = new java.util.ArrayList<>();
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
     * @return Exit code from file execution
     */
    private static int executeCfmlFileShortcut(String filePath, String[] args, boolean verbose, boolean debug) throws Exception {
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
            LuceeScriptEngine luceeEngine = LuceeScriptEngine.getInstance(verbose, debug);
            
            // For .cfs files, we need to set up the ARGS array manually since setupScriptContext is disabled
            if (fileName.endsWith(".cfs")) {
                // Read the file content
                String fileContent = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                
                // Create ARGS array setup
                StringBuilder scriptWithArgs = new StringBuilder();
                scriptWithArgs.append("// Auto-generated ARGS array setup\n");
                scriptWithArgs.append("ARGS = ['" + filePath + "'");
                if (args != null && args.length > 0) {
                    for (String arg : args) {
                        scriptWithArgs.append(", '" + arg.replace("'", "''") + "'");
                    }
                }
                scriptWithArgs.append("];\n\n");
                scriptWithArgs.append(fileContent);
                
                if (debug) {
                    System.err.println("[DEBUG] Script with ARGS setup:");
                    System.err.println(scriptWithArgs.toString());
                    System.err.println("[DEBUG] End of script");
                }
                
                // Execute the wrapped script content directly
                luceeEngine.eval(scriptWithArgs.toString());
            } else {
                // For .cfm and .cfc files, use the existing method
                luceeEngine.executeScript(path.toAbsolutePath().toString(), args);
            }
            
            // Success
            return 0;
            
        } catch (Exception e) {
            StringOutput.Quick.error("Error executing CFML script '" + filePath + "': " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
