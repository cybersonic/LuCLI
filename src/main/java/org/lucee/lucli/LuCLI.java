package org.lucee.lucli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LuCLI {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;
    
    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;

    public static void main(String[] args) throws Exception {
        // Parse arguments first to get flags
        ParseResult parseResult = parseArgs(args);
        verbose = parseResult.verbose;
        debug = parseResult.debug;
        timing = parseResult.timing;
        
        // Initialize timing if requested
        Timer.setEnabled(timing);
        Timer.start("Total Execution");
        
        // Handle help early
        if (parseResult.showHelp) {
            showHelp();
            // Exit with error code if help was shown due to invalid option
            if (parseResult.invalidOption) {
                System.exit(EXIT_ERROR);
            }
            return;
        }
        
        try {
            // Configure Lucee directories BEFORE any Lucee initialization
            Timer.start("Configure Lucee Directories");
            configureLuceeDirectories();
            Timer.stop("Configure Lucee Directories");

            // Determine if we're running in terminal mode or one-shot command mode
            if (parseResult.scriptFile != null) {
                // One-shot command mode - pass command and args to InteractiveTerminal
                Timer.start("One-Shot Command Mode");
                
                List<String> terminalArgs = new ArrayList<>();
                terminalArgs.add(parseResult.scriptFile);
                if (parseResult.scriptArgs != null) {
                    terminalArgs.addAll(Arrays.asList(parseResult.scriptArgs));
                }
                
                InteractiveTerminal.main(terminalArgs.toArray(new String[0]));
                Timer.stop("One-Shot Command Mode");
            } else {
                // Interactive terminal mode
                Timer.start("Terminal Mode");
                InteractiveTerminal.main(new String[0]);
                Timer.stop("Terminal Mode");
            }
        } catch(Exception e) {
            StringOutput.Quick.error("Error: " + e.getMessage());
            if (verbose || debug) {
                e.printStackTrace();
            }
            System.exit(EXIT_ERROR);
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("Total Execution");
            Timer.printResults();
        }
        
        // Exit cleanly after all operations are complete
        System.exit(EXIT_SUCCESS);
    }
    
    public static String getVersionInfo() {
        String version = getVersion();
        return "LuCLI " + version;
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
    
    private static void showHelp() {
        System.out.println(StringOutput.loadText("/text/main-help.txt"));
    }
    
    private static void configureLuceeDirectories() throws java.io.IOException {
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

    private static class ParseResult {
        String scriptFile;
        String[] scriptArgs;
        boolean verbose = false;
        boolean debug = false;
        boolean timing = false;
        boolean showHelp = false;
        boolean invalidOption = false;
    }
    
    private static ParseResult parseArgs(String[] args) {
        ParseResult result = new ParseResult();
        
        if (args.length == 0) {
            // Default to terminal mode when no arguments provided
            result.scriptFile = null; // Will trigger interactive terminal mode
            return result;
        }
        
        int scriptFileIndex = -1;
        
        // Parse flags
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.equals("-v") || arg.equals("--verbose")) {
                result.verbose = true;
            } else if (arg.equals("-d") || arg.equals("--debug")) {
                result.debug = true;
            } else if (arg.equals("-t") || arg.equals("--timing")) {
                result.timing = true;
            } else if (arg.equals("-h") || arg.equals("--help")) {
                result.showHelp = true;
                return result;
            } else if (arg.equals("--version") || arg.equals("--lucee-version") || arg.equals("help")) {
                // These commands don't take additional arguments
                scriptFileIndex = i;
                break;
            } else if (!arg.startsWith("-")) {
                // This is the script file or command
                scriptFileIndex = i;
                break;
            } else {
                System.err.println("Unknown option: " + arg);
                result.showHelp = true;
                result.invalidOption = true;
                return result;
            }
        }
        
        if (scriptFileIndex >= 0) {
            result.scriptFile = args[scriptFileIndex];
            
            // Everything after the script file are script arguments
            if (scriptFileIndex + 1 < args.length) {
                result.scriptArgs = Arrays.copyOfRange(args, scriptFileIndex + 1, args.length);
            } else {
                result.scriptArgs = new String[0];
            }
        } else {
            // No script file found, default to interactive terminal mode
            result.scriptFile = null;
        }
        
        return result;
    }
}