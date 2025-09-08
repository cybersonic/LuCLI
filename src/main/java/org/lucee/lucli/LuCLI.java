package org.lucee.lucli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.LogCommand;
import org.lucee.lucli.monitoring.MonitorCommand;
import org.lucee.lucli.modules.ModuleCommand;
import org.lucee.lucli.commands.UnifiedCommandExecutor;
import org.lucee.lucli.cflint.CFLintCommand;

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
        
        // Determine the actual command after flag parsing
        String command = parseResult.scriptFile != null ? parseResult.scriptFile : "terminal";
        
        try {
            // Configure Lucee directories BEFORE any Lucee initialization
            Timer.start("Configure Lucee Directories");
            configureLuceeDirectories();
            Timer.stop("Configure Lucee Directories");

            switch (command) {
                case "--version":
                    StringOutput.getInstance().println(getVersionInfo());
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
                    
                case "modules":
                    Timer.start("Module Command");
                    // Create filtered args array without flags for module command processing
                    String[] moduleArgs = parseResult.scriptArgs != null ? parseResult.scriptArgs : new String[0];
                    ModuleCommand.executeModule(moduleArgs);
                    Timer.stop("Module Command");
                    break;
                    
                case "cflint":
                    Timer.start("Lint Command");
                    // Create command line for CFLint processing
                    StringBuilder lintCommandLine = new StringBuilder("cflint");
                    if (parseResult.scriptArgs != null) {
                        for (String arg : parseResult.scriptArgs) {
                            lintCommandLine.append(" ").append(arg);
                        }
                    }
                    handleCFLintCommand(lintCommandLine.toString());
                    Timer.stop("Lint Command");
                    break;
                    
                default:
                    // First, check if this is a module command
                    Timer.start("Module Check");
                    if (ModuleCommand.moduleExists(command)) {
                        Timer.stop("Module Check");
                        Timer.start("Module Execution");
                        String[] moduleExecutionArgs = parseResult.scriptArgs != null ? parseResult.scriptArgs : new String[0];
                        ModuleCommand.executeModuleByName(command, moduleExecutionArgs);
                        Timer.stop("Module Execution");
                        break;
                    }
                    Timer.stop("Module Check");
                    
                    // If not a module, execute as CFML script
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
        } 
        catch(Exception e) {
            StringOutput.Quick.error("Error: " + e.getMessage());
            if (verbose || debug) {
                e.printStackTrace();
            }
            System.exit(EXIT_ERROR);

        }
        finally {
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
     * Handle lint commands using CFLintCommand
     */
    private static void handleCFLintCommand(String commandLine) throws Exception {
        CFLintCommand cfLintCommand = new CFLintCommand();
        boolean result = cfLintCommand.handleLintCommand(commandLine);
        // CFLint output is handled directly by the command, we just need to ensure completion
        if (!result) {
            System.exit(EXIT_ERROR);
        }
    }
    
    /**
     * Handle server commands using UnifiedCommandExecutor
     */
    private static void handleServerCommand(String[] args) {
        // Extract server subcommand and arguments
        String[] serverArgs;
        if (args.length >= 3) {
            // Remove "lucli" and "server" from args, keep the rest
            serverArgs = Arrays.copyOfRange(args, 2, args.length);
        } else {
            // No subcommand provided
            serverArgs = new String[0];
        }
        
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        UnifiedCommandExecutor executor = new UnifiedCommandExecutor(false, currentDir);
        
        // Execute server command - this will handle output directly and exit if needed
        executor.executeCommand("server", serverArgs);
    }
    
    private static void showServerHelp() {
        System.out.println(StringOutput.loadText("/text/server-help.txt"));
    }
    
    private static void handleServerStart(LuceeServerManager serverManager, Path currentDir, String[] args) throws Exception {
        Timer.start("Server Start Operation");
        String versionOverride = null;
        boolean forceReplace = false;
        String customName = null;
        Path projectDir = currentDir; // Default to current directory
        
        // Parse additional arguments
        for (int i = 2; i < args.length; i++) {
            if ((args[i].equals("--version") || args[i].equals("-v")) && i + 1 < args.length) {
                versionOverride = args[i + 1];
                i++; // Skip next argument
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                forceReplace = true;
            } else if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                customName = args[i + 1];
                i++; // Skip next argument
            } else if (!args[i].startsWith("-") && i == 3) {
                // If the first non-option argument after "start" is provided, use it as project directory
                projectDir = Paths.get(args[i]);
            }
        }
        
        try {
            System.out.println(StringOutput.msg("server.starting", projectDir));
            LuceeServerManager.ServerInstance instance = serverManager.startServer(projectDir, versionOverride, forceReplace, customName);
            Timer.stop("Server Start Operation");
            
            System.out.println(WindowsCompatibility.Symbols.SUCCESS + " " + StringOutput.msg("server.started"));
            System.out.println("   " + StringOutput.msg("server.name", instance.getServerName()));
            System.out.println("   " + StringOutput.msg("server.process.id", instance.getPid()));
            System.out.println("   " + StringOutput.msg("server.port", instance.getPort()));
            System.out.println("   " + StringOutput.msg("server.url", instance.getPort()));
            System.out.println("   " + StringOutput.msg("server.webroot", projectDir));
        } catch (org.lucee.lucli.server.ServerConflictException e) {
            Timer.stop("Server Start Operation");
            System.out.println();
            
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("ERROR_MESSAGE", e.getMessage());
            placeholders.put("SUGGESTED_NAME", e.getSuggestedName());
            
            System.out.println(StringOutput.loadTextWithPlaceholders("/text/server-conflict.txt", placeholders));
            System.exit(EXIT_ERROR);
        }
    }
    
    private static void handleServerStop(LuceeServerManager serverManager, Path currentDir, String[] args) throws Exception {
        Timer.start("Server Stop Operation");
        
        String serverName = null;
        
        // Parse --name flag
        for (int i = 2; i < args.length; i++) {
            if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                break;
            }
        }
        
        if (serverName != null) {
            // Stop server by name
            System.out.println(StringOutput.msg("server.stopping", serverName));
            boolean stopped = serverManager.stopServerByName(serverName);
            Timer.stop("Server Stop Operation");
            
            if (stopped) {
                System.out.println(WindowsCompatibility.Symbols.SUCCESS + " " + StringOutput.msg("server.stopped", serverName));
            } else {
                System.out.println(WindowsCompatibility.Symbols.INFO + "  " + StringOutput.msg("server.not.found", serverName));
            }
        } else {
            // Stop server for current directory
            System.out.println(StringOutput.msg("server.status.for", currentDir));
            boolean stopped = serverManager.stopServer(currentDir);
            Timer.stop("Server Stop Operation");
            
            if (stopped) {
                System.out.println(WindowsCompatibility.Symbols.SUCCESS + " " + StringOutput.msg("server.started"));
            } else {
                System.out.println(WindowsCompatibility.Symbols.INFO + "  " + StringOutput.msg("server.not.running.current"));
            }
        }
    }
    
    private static void handleServerStatus(LuceeServerManager serverManager, Path currentDir, String[] args) throws Exception {
        Timer.start("Server Status Operation");
        
        String serverName = null;
        
        // Parse --name flag
        for (int i = 2; i < args.length; i++) {
            if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                break;
            }
        }
        
        if (serverName != null) {
            // Look up server by name
            LuceeServerManager.ServerInfo serverInfo = serverManager.getServerInfoByName(serverName);
            Timer.stop("Server Status Operation");
            
            if (serverInfo == null) {
                System.out.println(WindowsCompatibility.Symbols.ERROR + " Server '" + serverName + "' not found.");
                return;
            }
            
            System.out.println("Server status for: " + serverName);
            
            if (serverInfo.isRunning()) {
                System.out.println(WindowsCompatibility.Symbols.SUCCESS + " Server is RUNNING");
                System.out.println("   Server Name: " + serverInfo.getServerName());
                System.out.println("   Process ID:  " + serverInfo.getPid());
                System.out.println("   Port:        " + serverInfo.getPort());
                System.out.println("   URL:         http://localhost:" + serverInfo.getPort());
                if (serverInfo.getProjectDir() != null) {
                    System.out.println("   Web Root:    " + serverInfo.getProjectDir());
                }
                System.out.println("   Server Dir:  " + serverInfo.getServerDir());
            } else {
                System.out.println(WindowsCompatibility.Symbols.ERROR + " Server is NOT RUNNING");
                if (serverInfo.getProjectDir() != null) {
                    System.out.println("   Web Root:    " + serverInfo.getProjectDir());
                }
                System.out.println("   Server Dir:  " + serverInfo.getServerDir());
            }
        } else {
            // Use current directory approach
            LuceeServerManager.ServerStatus status = serverManager.getServerStatus(currentDir);
            Timer.stop("Server Status Operation");
            
            System.out.println("Server status for: " + currentDir);
            
            if (status.isRunning()) {
                System.out.println(WindowsCompatibility.Symbols.SUCCESS + " Server is RUNNING");
                System.out.println("   Server Name: " + status.getServerName());
                System.out.println("   Process ID:  " + status.getPid());
                System.out.println("   Port:        " + status.getPort());
                System.out.println("   URL:         http://localhost:" + status.getPort());
            } else {
                System.out.println(WindowsCompatibility.Symbols.ERROR + " Server is NOT RUNNING");
            }
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
        System.out.printf("%-20s %-10s %-8s %-10s %-40s %s\n", "NAME", "STATUS", "PID", "PORT", "WEBROOT", "SERVER DIR");
        System.out.println("─".repeat(120));
        
        for (LuceeServerManager.ServerInfo server : servers) {
            String status = server.isRunning() ? "RUNNING" : "STOPPED";
            String pid = server.getPid() > 0 ? String.valueOf(server.getPid()) : "-";
            String port = server.getPort() > 0 ? String.valueOf(server.getPort()) : "-";
            String webroot = server.getProjectDir() != null ? server.getProjectDir().toString() : "<unknown>";
            
            // Truncate long paths for better display
            if (webroot.length() > 38) {
                webroot = "..." + webroot.substring(webroot.length() - 35);
            }
            
            System.out.printf("%-20s %-10s %-8s %-10s %-40s %s\n", 
                server.getServerName(), status, pid, port, webroot, server.getServerDir());
        }
    }
    
    /**
     * Handle server monitor command
     */
    private static void handleServerMonitor(String[] args) {
        Timer.start("Server Monitor Operation");
        
        // Create monitor arguments array (skip "lucli", "server", "monitor")
        String[] monitorArgs = new String[0];
        if (args.length > 3) {
            monitorArgs = Arrays.copyOfRange(args, 3, args.length);
        }
        
        // Execute the monitor command
        MonitorCommand.executeMonitor(monitorArgs);
        
        Timer.stop("Server Monitor Operation");
    }
    
    /**
     * Handle server log command
     */
    private static void handleServerLog(String[] args) {
        Timer.start("Server Log Operation");
        
        // Create log arguments array (skip "lucli", "server", "log")
        String[] logArgs = new String[0];
        if (args.length > 3) {
            logArgs = Arrays.copyOfRange(args, 3, args.length);
        }
        
        // Execute the log command
        LogCommand.executeLog(logArgs);
        
        Timer.stop("Server Log Operation");
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
        boolean updateModules = false;
        String[] moduleNames = null;
        boolean isTerminalMode = false;
        String terminalCommand = null;
    }
    
    private static ParseResult parseArgs(String[] args) {
        ParseResult result = new ParseResult();
        
        if (args.length == 0) {
            // Default to terminal mode when no arguments provided
            result.scriptFile = "terminal";
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
                result.invalidOption = true;
                return result;
            }
        }
        
        if (scriptFileIndex >= 0) {
            String potentialScript = args[scriptFileIndex];
            
            // Set verbose/debug mode before resolution for proper logging
            verbose = result.verbose;
            debug = result.debug;
            
            // Regular script/module resolution
            if (potentialScript.equals("terminal")) {
                result.scriptFile = "terminal";
                result.scriptArgs = new String[0];
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

