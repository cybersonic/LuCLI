package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lucee.loader.engine.CFMLEngineFactory;
import org.lucee.lucli.server.LuceeServerManager;

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
        
        // Determine the actual command after flag parsing
        String command = parseResult.scriptFile != null ? parseResult.scriptFile : "terminal";
        
        try {
            // Configure Lucee directories BEFORE any Lucee initialization
            Timer.start("Configure Lucee Directories");
            configureLuceeDirectories();
            Timer.stop("Configure Lucee Directories");

            switch (command) {
                case "--version":
                    System.out.println(getVersionInfo());
                    break;
                    
                case "--lucee-version":
                    Timer.start("Lucee Version Check");
                    testLuceeVersion();
                    Timer.stop("Lucee Version Check");
                    break;
                    
                case "terminal":
                    Timer.start("Terminal Mode");
                    SimpleTerminal.main(new String[0]);
                    Timer.stop("Terminal Mode");
                    System.exit(EXIT_SUCCESS);
                    break;
                    
                case "help":
                case "--help":
                case "-h":
                    showHelp();
                    break;
                    
                case "server":
                    Timer.start("Server Command");
                    // Create filtered args array without flags for server command processing
                    List<String> serverArgs = new ArrayList<>();
                    serverArgs.add("lucli"); // Program name placeholder
                    serverArgs.add("server"); // Server command
                    // Add the server subcommand and its arguments
                    if (parseResult.scriptArgs != null) {
                        for (String arg : parseResult.scriptArgs) {
                            serverArgs.add(arg);
                        }
                    }
                    handleServerCommand(serverArgs.toArray(new String[0]));
                    Timer.stop("Server Command");
                    break;
                    
                default:
                    // Execute CFML script with arguments
                    Timer.start("Script Execution");
                    verbose = parseResult.verbose;
                    debug = parseResult.debug;
                    String[] scriptArgs = parseResult.scriptArgs != null ? parseResult.scriptArgs : new String[0];
                    
                    LuceeScriptEngine.getInstance(verbose, debug)
                            .executeScript(command, scriptArgs);
                    Timer.stop("Script Execution");
                    
                    // Break out of switch to allow finally block to execute
                    break;
            }
        } finally {
            // Always stop total timer and show results before exit (if timing enabled)
            Timer.stop("Total Execution");
            Timer.printResults();
        }
        
        // Exit cleanly after all operations are complete
        System.exit(EXIT_SUCCESS);
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
        System.out.println("  -t, --timing   Enable timing output for performance analysis");
        System.out.println("  -h, --help     Show this help message");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  terminal       Start interactive terminal (default if no command given)");
        System.out.println("  server         Manage Lucee server instances (start, stop, status, list)");
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
        System.out.println("  java -jar lucli.jar --timing script.cfs # Execute script with timing analysis");
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


    /**
     * Handle server commands (start, stop, status, list)
     */
    private static void handleServerCommand(String[] args) {
        if (args.length < 3) {
            showServerHelp();
            return;
        }
        
        String subCommand = args[2];
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        
        try {
            LuceeServerManager serverManager = new LuceeServerManager();
            
            switch (subCommand) {
                case "start":
                    handleServerStart(serverManager, currentDir, args);
                    break;
                    
                case "stop":
                    handleServerStop(serverManager, currentDir);
                    break;
                    
                case "status":
                    handleServerStatus(serverManager, currentDir);
                    break;
                    
                case "list":
                    handleServerList(serverManager);
                    break;
                    
                default:
                    System.err.println("Unknown server command: " + subCommand);
                    showServerHelp();
                    System.exit(EXIT_ERROR);
            }
        } catch (Exception e) {
            System.err.println("Server command failed: " + e.getMessage());
            if (verbose || debug) {
                e.printStackTrace();
            }
            System.exit(EXIT_ERROR);
        }
    }
    
    private static void showServerHelp() {
        System.out.println("LuCLI Server Management");
        System.out.println();
        System.out.println("Usage: lucli server [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  start [--version VERSION]  Start a Lucee server for the current directory");
        System.out.println("  stop                       Stop the server for the current directory");
        System.out.println("  status                     Show server status for the current directory");
        System.out.println("  list                       List all server instances");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Server configuration is read from lucee.json in the current directory.");
        System.out.println("  If lucee.json doesn't exist, a default one will be created.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  lucli server start                   # Start server with default settings");
        System.out.println("  lucli server start --version 6.1.0.123  # Start server with specific version");
        System.out.println("  lucli server stop                    # Stop the server");
        System.out.println("  lucli server status                  # Check server status");
        System.out.println("  lucli server list                    # List all servers");
    }
    
    private static void handleServerStart(LuceeServerManager serverManager, Path currentDir, String[] args) throws Exception {
        Timer.start("Server Start Operation");
        String versionOverride = null;
        
        // Parse additional arguments
        for (int i = 2; i < args.length; i++) {
            if ((args[i].equals("--version") || args[i].equals("-v")) && i + 1 < args.length) {
                versionOverride = args[i + 1];
                i++; // Skip next argument
            }
        }
        
        System.out.println("Starting Lucee server in: " + currentDir);
        LuceeServerManager.ServerInstance instance = serverManager.startServer(currentDir, versionOverride);
        Timer.stop("Server Start Operation");
        
        System.out.println("✅ Server started successfully!");
        System.out.println("   Server Name: " + instance.getServerName());
        System.out.println("   Process ID:  " + instance.getPid());
        System.out.println("   Port:        " + instance.getPort());
        System.out.println("   URL:         http://localhost:" + instance.getPort());
        System.out.println("   Web Root:    " + currentDir);
    }
    
    private static void handleServerStop(LuceeServerManager serverManager, Path currentDir) throws Exception {
        Timer.start("Server Stop Operation");
        System.out.println("Stopping server for: " + currentDir);
        boolean stopped = serverManager.stopServer(currentDir);
        Timer.stop("Server Stop Operation");
        
        if (stopped) {
            System.out.println("✅ Server stopped successfully.");
        } else {
            System.out.println("ℹ️  No running server found for this directory.");
        }
    }
    
    private static void handleServerStatus(LuceeServerManager serverManager, Path currentDir) throws Exception {
        Timer.start("Server Status Operation");
        LuceeServerManager.ServerStatus status = serverManager.getServerStatus(currentDir);
        Timer.stop("Server Status Operation");
        
        System.out.println("Server status for: " + currentDir);
        
        if (status.isRunning()) {
            System.out.println("✅ Server is RUNNING");
            System.out.println("   Server Name: " + status.getServerName());
            System.out.println("   Process ID:  " + status.getPid());
            System.out.println("   Port:        " + status.getPort());
            System.out.println("   URL:         http://localhost:" + status.getPort());
        } else {
            System.out.println("❌ Server is NOT RUNNING");
        }
    }
    
    private static void handleServerList(LuceeServerManager serverManager) throws Exception {
        Timer.start("Server List Operation");
        List<LuceeServerManager.ServerInfo> servers = serverManager.listServers();
        Timer.stop("Server List Operation");
        
        if (servers.isEmpty()) {
            System.out.println("No server instances found.");
            return;
        }
        
        System.out.println("Server instances:");
        System.out.println();
        System.out.printf("%-20s %-10s %-8s %-10s %s\n", "NAME", "STATUS", "PID", "PORT", "DIRECTORY");
        System.out.println("─".repeat(80));
        
        for (LuceeServerManager.ServerInfo server : servers) {
            String status = server.isRunning() ? "RUNNING" : "STOPPED";
            String pid = server.getPid() > 0 ? String.valueOf(server.getPid()) : "-";
            String port = server.getPort() > 0 ? String.valueOf(server.getPort()) : "-";
            
            System.out.printf("%-20s %-10s %-8s %-10s %s\n", 
                server.getServerName(), status, pid, port, server.getServerDir());
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
        } else {
            // No script file found, but we might have flags only
            // In this case, default to terminal mode
            result.scriptFile = null; // Will be set to "terminal" in main()
        }
        
        return result;
    }
    
}

