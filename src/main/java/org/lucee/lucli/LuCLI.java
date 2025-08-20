package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lucee.loader.engine.CFMLEngineFactory;

public class LuCLI {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;
    
    public static boolean verbose = false;
    public static boolean debug = false;
    public static boolean timing = false;

    public static void main(String[] args) throws Exception {
        // Parse arguments first to get flags
        String command = args.length > 0 ? args[0] : "terminal";
        ParseResult parseResult = parseArgs(args);
        verbose = parseResult.verbose;
        debug = parseResult.debug;
        timing = parseResult.timing;
        
        // Configure Lucee directories BEFORE any Lucee initialization
        configureLuceeDirectories();

        switch (command) {
            case "--version":
                System.out.println(getVersionInfo());
                break;
                
            case "--lucee-version":
                testLuceeVersion();
                break;
                
            case "terminal":
                SimpleTerminal.main(new String[0]);
                System.exit(EXIT_SUCCESS);
                break;
                
            case "help":
            case "--help":
            case "-h":
                showHelp();
                break;
                
            default:
                // Execute CFML script with arguments
                verbose = parseResult.verbose;
                debug = parseResult.debug;
                String[] scriptArgs = parseResult.scriptArgs != null ? parseResult.scriptArgs : new String[0];
                
                LuceeScriptEngine.getInstance(verbose, debug)
                        .executeScript(command, scriptArgs);
                System.exit(EXIT_SUCCESS);
        }
    }
    
    private static String getVersionInfo() {
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
        System.out.println("LuCLI - A terminal application with Lucee CFML integration");
        System.out.println();
        System.out.println("Usage: java -jar lucli.jar [options] [command] [arguments...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -v, --verbose  Enable verbose output");
        System.out.println("  -d, --debug    Enable debug output");
        System.out.println("  -h, --help     Show this help message");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  terminal       Start interactive terminal (default if no command given)");
        System.out.println("  --version      Show application version");
        System.out.println("  --lucee-version  Show Lucee version");
        System.out.println("  help, --help, -h  Show this help message");
        System.out.println("  script.cfs     Execute a CFML script file");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Lucee server files are stored in ~/.lucli/lucee-server by default");
        System.out.println("  Override with LUCLI_HOME environment variable or -Dlucli.home system property");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar lucli.jar                    # Start interactive terminal");
        System.out.println("  java -jar lucli.jar --verbose --version # Show version with verbose output");
        System.out.println("  java -jar lucli.jar script.cfs arg1 arg2 # Execute CFML script with arguments");
        System.out.println("  LUCLI_HOME=/tmp/lucli java -jar lucli.jar --lucee-version # Use custom home directory");
    }
    
    // TODO move this to the LuceeScriptEngine class
    private static void testLuceeVersion() throws Exception {
        try {
            LuceeScriptEngine engine = LuceeScriptEngine.getInstance(true, false);
            
            if(engine == null) {
                System.out.println("LuceeScriptEngine instance is null. Lucee may not be properly initialized.");
                return;
            }

            engine.eval("version = SERVER.LUCEE.version");
            System.out.println("Lucee Version: " + engine.getEngine().get("version"));
            
        } finally {
            // Lucee starts background threads that prevent JVM exit, so we need to force exit
            System.exit(EXIT_SUCCESS);
        }
    }


    private static void configureLuceeDirectories() throws IOException {
        // Allow customization of lucli home via environment variable or system property
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = Paths.get(userHome, ".lucli").toString();
        }
        
        Path lucliHome = Paths.get(lucliHomeStr);
        Path luceeServerDir = lucliHome.resolve("lucee-server");
        Path patchesDir = lucliHome.resolve("patches");
        
        // Create all necessary directories if they don't exist
        Files.createDirectories(luceeServerDir);
        Files.createDirectories(patchesDir);
        
        if (verbose || debug) {
            System.out.println("Configured Lucee directories:");
            System.out.println("  LuCLI Home: " + lucliHome.toString());
            System.out.println("  Lucee Server: " + luceeServerDir.toString());
            System.out.println("  Patches: " + patchesDir.toString());
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
        boolean updateModules = false;
        String[] moduleNames = null;
        boolean isTerminalMode = false;
        String terminalCommand = null;
    }
    
    private static ParseResult parseArgs(String[] args) {
        ParseResult result = new ParseResult();
        
        if (args.length == 0) {
            result.showHelp = true;
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
            } else if (arg.equals("--update-modules")) {
                result.updateModules = true;
                // Collect module names from remaining arguments
                List<String> moduleNames = new ArrayList<>();
                for (int j = i + 1; j < args.length; j++) {
                    if (args[j].startsWith("-")) {
                        break; // Stop at next option
                    }
                    moduleNames.add(args[j]);
                }
                result.moduleNames = moduleNames.toArray(new String[0]);
                return result; // Return immediately for update mode
            } else if (!arg.startsWith("-")) {
                // This is the script file
                scriptFileIndex = i;
                break;
            } else {
                System.err.println("Unknown option: " + arg);
                result.showHelp = true;
                return result;
            }
        }
        
        if (scriptFileIndex >= 0) {
            String potentialScript = args[scriptFileIndex];
            
            // Set verbose/debug mode before resolution for proper logging
            verbose = result.verbose;
            debug = result.debug;
            
            // Special handling for terminal mode
            if (potentialScript.equals("terminal")) {
                result.isTerminalMode = true;
                
                // Check for -c flag for command mode
                if (scriptFileIndex + 1 < args.length && args[scriptFileIndex + 1].equals("-c")) {
                    // Command mode: terminal -c "command"
                    if (scriptFileIndex + 2 < args.length) {
                        result.terminalCommand = args[scriptFileIndex + 2];
                        // Set a dummy script file to satisfy the null check
                        result.scriptFile = "terminal-command-mode";
                        result.scriptArgs = new String[0];
                    } else {
                        System.err.println("Error: -c flag requires a command");
                        result.showHelp = true;
                    }
                } else {
                    // Interactive mode: terminal
                    result.scriptFile = "terminal-interactive-mode";
                    result.scriptArgs = new String[0];
                }
            } else {
                // Regular script/module resolution
                result.scriptFile = potentialScript;
                
                // Everything after the script file are script arguments
                if (scriptFileIndex + 1 < args.length) {
                    result.scriptArgs = Arrays.copyOfRange(args, scriptFileIndex + 1, args.length);
                } else {
                    result.scriptArgs = new String[0];
                }
            }
        }
        
        return result;
    }
    
}

