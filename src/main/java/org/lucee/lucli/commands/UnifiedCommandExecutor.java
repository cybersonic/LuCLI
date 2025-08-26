package org.lucee.lucli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Timer;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.ServerConflictException;
import org.lucee.lucli.monitoring.MonitorCommand;
import org.lucee.lucli.server.LogCommand;
import org.lucee.lucli.modules.ModuleCommand;

/**
 * Unified command executor that provides single implementation for all commands
 * This ensures feature parity between CLI and Terminal modes
 */
public class UnifiedCommandExecutor {
    
    private final boolean isTerminalMode;
    private final Path currentWorkingDirectory;
    
    public UnifiedCommandExecutor(boolean isTerminalMode, Path currentWorkingDirectory) {
        this.isTerminalMode = isTerminalMode;
        this.currentWorkingDirectory = currentWorkingDirectory != null ? 
            currentWorkingDirectory : Paths.get(System.getProperty("user.dir"));
    }
    
    /**
     * Execute a command and return the result as a string (for terminal mode)
     * or output directly to console (for CLI mode)
     */
    public String executeCommand(String command, String[] args) {
        try {
            switch (command.toLowerCase()) {
                case "server":
                    return executeServerCommand(args);
                case "modules":
                    return executeModulesCommand(args);
                case "monitor":
                    return executeMonitorCommand(args);
                default:
                    return formatOutput("❌ Unknown command: " + command, true);
            }
        } catch (Exception e) {
            String errorMsg = "❌ Command failed: " + e.getMessage();
            if (LuCLI.verbose || LuCLI.debug) {
                errorMsg += "\n" + getStackTrace(e);
            }
            return formatOutput(errorMsg, true);
        }
    }
    
    /**
     * Execute server commands (start, stop, status, list, monitor, log)
     */
    private String executeServerCommand(String[] args) throws Exception {
        if (args.length == 0) {
            return formatOutput("❌ server: missing subcommand\n💡 Usage: server [start|stop|status|list|monitor|log] [options]", true);
        }
        
        String subCommand = args[0];
        LuceeServerManager serverManager = new LuceeServerManager();
        
        Timer.start("Server " + subCommand + " Command");
        
        try {
            switch (subCommand) {
                case "start":
                    return handleServerStart(serverManager, args);
                case "stop":  
                    return handleServerStop(serverManager, args);
                case "status":
                    return handleServerStatus(serverManager, args);
                case "list":
                    return handleServerList(serverManager, args);
                case "monitor":
                    return handleServerMonitor(Arrays.copyOfRange(args, 1, args.length));
                case "log":
                    return handleServerLog(Arrays.copyOfRange(args, 1, args.length));
                default:
                    return formatOutput("❌ Unknown server command: " + subCommand + 
                        "\n💡 Available commands: start, stop, status, list, monitor, log", true);
            }
        } finally {
            Timer.stop("Server " + subCommand + " Command");
        }
    }
    
    private String handleServerStart(LuceeServerManager serverManager, String[] args) throws Exception {
        String versionOverride = null;
        boolean forceReplace = false;
        String customName = null;
        Path projectDir = currentWorkingDirectory; // Default to current directory
        
        // Parse additional arguments (skip "start")
        for (int i = 1; i < args.length; i++) {
            if ((args[i].equals("--version") || args[i].equals("-v")) && i + 1 < args.length) {
                versionOverride = args[i + 1];
                i++; // Skip next argument
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                forceReplace = true;
            } else if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                customName = args[i + 1];
                i++; // Skip next argument
            } else if (!args[i].startsWith("-") && i == 1) {
                // If the first non-option argument after "start" is provided, use it as project directory
                projectDir = Paths.get(args[i]);
            }
        }
        
        try {
            StringBuilder result = new StringBuilder();
            if (isTerminalMode) {
                // Shorter output for terminal mode
                result.append("Starting Lucee server...\n");
            } else {
                result.append("Starting Lucee server in: ").append(projectDir).append("\n");
            }
            
            LuceeServerManager.ServerInstance instance = serverManager.startServer(projectDir, versionOverride, forceReplace, customName);
            
            // Load configuration to get monitoring/JMX port info
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            
            result.append("✅ Server started successfully!\n");
            result.append("   Server Name:   ").append(instance.getServerName()).append("\n");
            result.append("   Process ID:    ").append(instance.getPid()).append("\n");
            result.append("   HTTP Port:     ").append(instance.getPort()).append("\n");
            result.append("   Shutdown Port: ").append(LuceeServerConfig.getShutdownPort(instance.getPort())).append("\n");
            
            if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
                result.append("   JMX Port:      ").append(config.monitoring.jmx.port).append("\n");
            }
            
            result.append("   URL:           http://localhost:").append(instance.getPort()).append("\n");
            result.append("   Web Root:      ").append(projectDir);
            
            return formatOutput(result.toString(), false);
            
        } catch (ServerConflictException e) {
            StringBuilder result = new StringBuilder();
            result.append("⚠️  ").append(e.getMessage()).append("\n\n");
            result.append("Choose an option:\n");
            result.append("  1. Replace the existing server (delete and recreate):\n");
            
            if (isTerminalMode) {
                result.append("     server start --force\n\n");
                result.append("  2. Create server with suggested name '").append(e.getSuggestedName()).append("':\n");
                result.append("     server start --name ").append(e.getSuggestedName()).append("\n\n");
                result.append("  3. Create server with custom name:\n");
                result.append("     server start --name <your-name>\n\n");
            } else {
                result.append("     lucli server start --force\n\n");
                result.append("  2. Create server with suggested name '").append(e.getSuggestedName()).append("':\n");
                result.append("     lucli server start --name ").append(e.getSuggestedName()).append("\n\n");
                result.append("  3. Create server with custom name:\n");
                result.append("     lucli server start --name <your-name>\n\n");
            }
            
            result.append("💡 Use --force to replace existing servers, or --name to specify a different name.");
            
            return formatOutput(result.toString(), true);
        }
    }
    
    private String handleServerStop(LuceeServerManager serverManager, String[] args) throws Exception {
        String serverName = null;
        
        // Parse --name flag (skip "stop")
        for (int i = 1; i < args.length; i++) {
            if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                break;
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if (serverName != null) {
            // Stop server by name
            if (!isTerminalMode) {
                result.append("Stopping server: ").append(serverName).append("\n");
            }
            boolean stopped = serverManager.stopServerByName(serverName);
            
            if (stopped) {
                result.append("✅ Server '").append(serverName).append("' stopped successfully.");
            } else {
                result.append("ℹ️  Server '").append(serverName).append("' not found or not running.");
            }
        } else {
            // Stop server for current directory
            if (!isTerminalMode) {
                result.append("Stopping server for: ").append(currentWorkingDirectory).append("\n");
            }
            boolean stopped = serverManager.stopServer(currentWorkingDirectory);
            
            if (stopped) {
                result.append("✅ Server stopped successfully.");
            } else {
                result.append("ℹ️  No running server found for this directory.");
            }
        }
        
        return formatOutput(result.toString(), false);
    }
    
    private String handleServerStatus(LuceeServerManager serverManager, String[] args) throws Exception {
        String serverName = null;
        
        // Parse --name flag (skip "status")
        for (int i = 1; i < args.length; i++) {
            if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                break;
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if (serverName != null) {
            // Get status for server by name
            LuceeServerManager.ServerInfo serverInfo = serverManager.getServerInfoByName(serverName);
            
            if (serverInfo == null) {
                result.append("❌ Server '").append(serverName).append("' not found.");
                return formatOutput(result.toString(), true);
            }
            
            result.append("Server status for '").append(serverName).append("':\n");
            
            if (serverInfo.isRunning()) {
                result.append("✅ Server is RUNNING\n");
                result.append("   Server Name:   ").append(serverInfo.getServerName()).append("\n");
                result.append("   Process ID:    ").append(serverInfo.getPid()).append("\n");
                result.append("   Port:          ").append(serverInfo.getPort()).append("\n");
                result.append("   URL:           http://localhost:").append(serverInfo.getPort()).append("\n");
                if (serverInfo.getProjectDir() != null) {
                    result.append("   Web Root:      ").append(serverInfo.getProjectDir()).append("\n");
                }
                result.append("   Server Dir:    ").append(serverInfo.getServerDir());
            } else {
                result.append("❌ Server is NOT RUNNING\n");
                if (serverInfo.getProjectDir() != null) {
                    result.append("   Web Root:      ").append(serverInfo.getProjectDir()).append("\n");
                }
                result.append("   Server Dir:    ").append(serverInfo.getServerDir());
            }
        } else {
            // Get status for current directory
            LuceeServerManager.ServerStatus status = serverManager.getServerStatus(currentWorkingDirectory);
            
            result.append("Server status for: ").append(currentWorkingDirectory).append("\n");
            
            if (status.isRunning()) {
                result.append("✅ Server is RUNNING\n");
                result.append("   Server Name: ").append(status.getServerName()).append("\n");
                result.append("   Process ID:  ").append(status.getPid()).append("\n");
                result.append("   Port:        ").append(status.getPort()).append("\n");
                result.append("   URL:         http://localhost:").append(status.getPort());
            } else {
                result.append("❌ Server is NOT RUNNING");
            }
        }
        
        return formatOutput(result.toString(), false);
    }
    
    private String handleServerList(LuceeServerManager serverManager, String[] args) throws Exception {
        List<LuceeServerManager.ServerInfo> servers = serverManager.listServers();
        
        if (servers.isEmpty()) {
            return formatOutput("No server instances found.", false);
        }
        
        StringBuilder result = new StringBuilder();
        result.append("Server instances:\n\n");
        
        if (isTerminalMode) {
            // Condensed format for terminal
            result.append(String.format("%-20s %-10s %-8s %-10s %s\n", "NAME", "STATUS", "PID", "PORT", "DIRECTORY"));
            result.append("─".repeat(80)).append("\n");
        } else {
            // Full format for CLI  
            result.append(String.format("%-20s %-10s %-8s %-10s %-40s %s\n", "NAME", "STATUS", "PID", "PORT", "WEBROOT", "SERVER DIR"));
            result.append("─".repeat(120)).append("\n");
        }
        
        for (LuceeServerManager.ServerInfo server : servers) {
            String status = server.isRunning() ? "RUNNING" : "STOPPED";
            String pid = server.getPid() > 0 ? String.valueOf(server.getPid()) : "-";
            String port = server.getPort() > 0 ? String.valueOf(server.getPort()) : "-";
            
            if (isTerminalMode) {
                // Condensed format
                result.append(String.format("%-20s %-10s %-8s %-10s %s\n", 
                    server.getServerName(), status, pid, port, server.getServerDir()));
            } else {
                // Full format
                String webroot = server.getProjectDir() != null ? server.getProjectDir().toString() : "<unknown>";
                
                // Truncate long paths for better display
                if (webroot.length() > 38) {
                    webroot = "..." + webroot.substring(webroot.length() - 35);
                }
                
                result.append(String.format("%-20s %-10s %-8s %-10s %-40s %s\n", 
                    server.getServerName(), status, pid, port, webroot, server.getServerDir()));
            }
        }
        
        return formatOutput(result.toString().trim(), false);
    }
    
    private String handleServerMonitor(String[] args) {
        if (isTerminalMode) {
            // In terminal mode, we can't start the interactive monitor, so show instructions
            return formatOutput("🖥️  Starting JMX monitoring dashboard...\n" +
                   "⚠️  Note: This will exit the terminal session and start the interactive monitor.\n" +
                   "💡 To start monitoring, use: java -jar lucli.jar server monitor\n" +
                   "❌ Direct monitor command from terminal not yet supported.", false);
        } else {
            // In CLI mode, start the monitor directly
            MonitorCommand.executeMonitor(args);
            return null; // MonitorCommand handles its own output and doesn't return
        }
    }
    
    private String handleServerLog(String[] args) {
        if (isTerminalMode) {
            // In terminal mode, we can't easily handle interactive log viewing
            return formatOutput("📋 Server log viewing\n" +
                   "💡 To view logs interactively, use: java -jar lucli.jar server log\n" +
                   "❌ Interactive log viewing from terminal not yet supported.", false);
        } else {
            // In CLI mode, start the log command directly
            LogCommand.executeLog(args);
            return null; // LogCommand handles its own output and doesn't return
        }
    }
    
    /**
     * Execute modules commands
     */
    private String executeModulesCommand(String[] args) throws Exception {
        if (isTerminalMode) {
            // In terminal mode, provide basic module info
            StringBuilder result = new StringBuilder();
            result.append("📦 LuCLI Modules\n");
            result.append("💡 For full module management, use: java -jar lucli.jar modules [command]\n");
            result.append("❌ Full module management from terminal not yet supported.");
            return formatOutput(result.toString(), false);
        } else {
            // In CLI mode, execute the full module command
            ModuleCommand.executeModule(args);
            return null; // ModuleCommand handles its own output and doesn't return
        }
    }
    
    /**
     * Execute monitor command directly
     */
    private String executeMonitorCommand(String[] args) {
        return handleServerMonitor(args);
    }
    
    /**
     * Format output for the appropriate mode
     */
    private String formatOutput(String message, boolean isError) {
        if (isTerminalMode) {
            // Return the message for the terminal to display
            return message;
        } else {
            // Print directly to console for CLI mode
            if (isError) {
                System.err.println(message);
                System.exit(1);
            } else {
                System.out.println(message);
            }
            return null;
        }
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
