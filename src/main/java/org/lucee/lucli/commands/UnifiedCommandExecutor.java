package org.lucee.lucli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Timer;
import org.lucee.lucli.modules.ModuleCommand;
import org.lucee.lucli.monitoring.MonitorCommand;
import org.lucee.lucli.server.LogCommand;
import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.ServerConflictException;

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
        return formatOutput("❌ server: missing subcommand\n💡 Usage: server [start|stop|restart|status|list|prune|monitor|log|debug] [options]", true);
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
                case "restart":
                    return handleServerRestart(serverManager, args);
                case "status":
                    return handleServerStatus(serverManager, args);
                case "list":
                    return handleServerList(serverManager, args);
                case "prune":
                    return handleServerPrune(serverManager, args);
                case "config":
                    return handleServerConfig(serverManager, args);
                case "monitor":
                    return handleServerMonitor(Arrays.copyOfRange(args, 1, args.length));
                case "log":
                    return handleServerLog(Arrays.copyOfRange(args, 1, args.length));
                case "debug":
                    return handleServerDebug(Arrays.copyOfRange(args, 1, args.length));
                default:
                    return formatOutput("❌ Unknown server command: " + subCommand + 
                        "\n💡 Available commands: start, stop, restart, status, list, prune, config, monitor, log, debug", true);
            }
        } finally {
            Timer.stop("Server " + subCommand + " Command");
        }
    }
    
    private String handleServerStart(LuceeServerManager serverManager, String[] args) throws Exception {
        String versionOverride = null;
        boolean forceReplace = false;
        String customName = null;
        boolean dryRun = false;
        Path projectDir = currentWorkingDirectory; // Default to current directory
        
        LuceeServerManager.AgentOverrides agentOverrides = new LuceeServerManager.AgentOverrides();
        java.util.List<String> configOverrides = new java.util.ArrayList<>();
        
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
            } else if (args[i].equals("--dry-run")) {
                dryRun = true;
            } else if (args[i].equals("--no-agents")) {
                agentOverrides.disableAllAgents = true;
            } else if ((args[i].equals("--agents")) && i + 1 < args.length) {
                String value = args[i + 1];
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (String part : value.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        ids.add(trimmed);
                    }
                }
                agentOverrides.includeAgents = ids;
                i++; // Skip next argument
            } else if (args[i].equals("--enable-agent") && i + 1 < args.length) {
                if (agentOverrides.enableAgents == null) {
                    agentOverrides.enableAgents = new java.util.HashSet<>();
                }
                agentOverrides.enableAgents.add(args[i + 1]);
                i++; // Skip next argument
            } else if (args[i].equals("--disable-agent") && i + 1 < args.length) {
                if (agentOverrides.disableAgents == null) {
                    agentOverrides.disableAgents = new java.util.HashSet<>();
                }
                agentOverrides.disableAgents.add(args[i + 1]);
                i++; // Skip next argument
            } else if (!args[i].startsWith("-") && i == 1 && !args[i].contains("=")) {
                // If the first non-option argument after "start" is provided and does not
                // look like a key=value override, treat it as the project directory.
                projectDir = Paths.get(args[i]);
            } else if (!args[i].startsWith("-") && args[i].contains("=")) {
                // Treat bare key=value arguments as configuration overrides that should
                // be applied to lucee.json before starting the server.
                configOverrides.add(args[i]);
            }
        }
        
        // Apply any configuration overrides to lucee.json before starting the server.
        if (!configOverrides.isEmpty()) {
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            ServerConfigHelper configHelper = new ServerConfigHelper();
            for (String kv : configOverrides) {
                if (kv == null || !kv.contains("=")) {
                    continue;
                }
                String[] parts = kv.split("=", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : "";
                if (!key.isEmpty()) {
                    configHelper.setConfigValue(config, key, value);
                }
            }
            Path configFile = projectDir.resolve("lucee.json");
            LuceeServerConfig.saveConfig(config, configFile);
        }
        
        // Load final realized config for dry-run or actual startup
        LuceeServerConfig.ServerConfig finalConfig = LuceeServerConfig.loadConfig(projectDir);
        
        // If dry-run, show what would happen and exit
        if (dryRun) {
            StringBuilder result = new StringBuilder();
            result.append("📋 DRY RUN: Server configuration that would be used:\n\n");
            result.append("Realized lucee.json for: ").append(projectDir).append("\n");
            result.append("═══════════════════════════════════════════\n");
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalConfig);
                result.append(jsonString).append("\n");
            } catch (Exception e) {
                result.append("Error serializing config: ").append(e.getMessage());
            }
            result.append("═══════════════════════════════════════════\n");
            result.append("\n✅ Use without --dry-run to start the server with this config.\n");
            return formatOutput(result.toString(), false);
        }
        
        // If no agent-related flags were actually set, avoid passing a non-null overrides object
        if (!agentOverrides.disableAllAgents &&
            (agentOverrides.includeAgents == null || agentOverrides.includeAgents.isEmpty()) &&
            (agentOverrides.enableAgents == null || agentOverrides.enableAgents.isEmpty()) &&
            (agentOverrides.disableAgents == null || agentOverrides.disableAgents.isEmpty())) {
            agentOverrides = null;
        }
        
        try {
            StringBuilder result = new StringBuilder();
            if (isTerminalMode) {
                // Shorter output for terminal mode
                result.append("Starting Lucee server...\n");
            } else {
                result.append("Starting Lucee server in: ").append(projectDir).append("\n");
            }
            
            LuceeServerManager.ServerInstance instance = serverManager.startServer(projectDir, versionOverride, forceReplace, customName, agentOverrides);
            
            // Load configuration to get monitoring/JMX port and agent info
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            
           
            result.append("   Server Name:   ").append(instance.getServerName()).append("\n");
            result.append("   Process ID:    ").append(instance.getPid()).append("\n");
            result.append("   HTTP Port:     ").append(instance.getPort()).append("\n");
            result.append("   Shutdown Port: ").append(LuceeServerConfig.getEffectiveShutdownPort(config)).append("\n");
           
            
            if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
                result.append("   JMX Port:      ").append(config.monitoring.jmx.port).append("\n");
            }
            
            
            
            result.append("   URL:           http://localhost:").append(instance.getPort()).append("\n");
            result.append("   Admin URL:     http://localhost:").append(instance.getPort()).append("/lucee/admin.cfm\n");
            result.append("   Web Root:      ").append(projectDir).append("\n");
            result.append("   Server Dir:    ").append(instance.getServerDir()).append("\n");

            // Show active agents, if any
            java.util.Set<String> activeAgents = serverManager.getActiveAgentsForConfig(config, agentOverrides);
            if (activeAgents != null && !activeAgents.isEmpty()) {
                result.append("   Agents:        ").append(String.join(", ", activeAgents)).append("\n");
            }

            
            result.append("✅ Server started successfully!\n");
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
    
    private String handleServerRestart(LuceeServerManager serverManager, String[] args) throws Exception {

        handleServerStop(serverManager, args);
        handleServerStart(serverManager, args);
        return "";
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
            result.append(String.format("%-20s %-10s %-8s %-10s %s\n", "NAME", "STATUS", "PID", "PORT", "WEBROOT"));
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

            if(args.length > 1 && (args[1].equals("--running") || args[1].equals("-r")) && !server.isRunning()) {
                // Skip non-running servers if --running flag is set
                continue;
            }
            
            String webroot = server.getProjectDir() != null ? server.getProjectDir().toString() : "<unknown>";
            if (isTerminalMode) {
                // Condensed format
                result.append(String.format("%-20s %-10s %-8s %-10s %s\n", 
                    server.getServerName(), status, pid, port, webroot));
            } else {
                // Full format
                
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
    
    private String handleServerPrune(LuceeServerManager serverManager, String[] args) throws Exception {
        boolean pruneAll = false;
        String serverName = null;
        
        // Parse arguments (skip "prune")
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--all") || args[i].equals("-a")) {
                pruneAll = true;
            } else if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                i++; // Skip next argument
            } else if (!args[i].startsWith("-")) {
                // Treat non-option argument as server name
                serverName = args[i];
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if (pruneAll) {
            // Prune all stopped servers
            LuceeServerManager.PruneAllResult pruneResult = serverManager.pruneAllStoppedServers();
            
            if (pruneResult.getPruned().isEmpty() && pruneResult.getSkipped().isEmpty()) {
                result.append("ℹ️  No servers found to prune.");
            } else {
                if (!pruneResult.getPruned().isEmpty()) {
                    result.append("✅ Pruned servers:\n");
                    for (LuceeServerManager.PruneResult pr : pruneResult.getPruned()) {
                        result.append("   • ").append(pr.getServerName()).append(" - ").append(pr.getMessage()).append("\n");
                    }
                }
                
                if (!pruneResult.getSkipped().isEmpty()) {
                    if (!pruneResult.getPruned().isEmpty()) {
                        result.append("\n");
                    }
                    result.append("⚠️  Skipped servers:\n");
                    for (LuceeServerManager.PruneResult pr : pruneResult.getSkipped()) {
                        result.append("   • ").append(pr.getServerName()).append(" - ").append(pr.getMessage()).append("\n");
                    }
                }
                
                int prunedCount = pruneResult.getPruned().size();
                int skippedCount = pruneResult.getSkipped().size();
                result.append("\n📊 Summary: ").append(prunedCount).append(" pruned, ").append(skippedCount).append(" skipped");
            }
        } else if (serverName != null) {
            // Prune specific server by name
            LuceeServerManager.PruneResult pruneResult = serverManager.pruneServerByName(serverName);
            
            if (pruneResult.isSuccess()) {
                result.append("✅ Server '").append(serverName).append("' pruned successfully.");
            } else {
                result.append("❌ Failed to prune server '").append(serverName).append("': ").append(pruneResult.getMessage());
                return formatOutput(result.toString(), true);
            }
        } else {
            // Prune server for current directory
            LuceeServerManager.PruneResult pruneResult = serverManager.pruneServer(currentWorkingDirectory);
            
            if (pruneResult.isSuccess()) {
                result.append("✅ Server '").append(pruneResult.getServerName()).append("' pruned successfully.");
            } else {
                result.append("❌ Failed to prune server: ").append(pruneResult.getMessage());
                return formatOutput(result.toString(), true);
            }
        }
        
        return formatOutput(result.toString(), false);
    }
    
    private String handleServerConfig(LuceeServerManager serverManager, String[] args) throws Exception {
        if (args.length < 2) {
            StringBuilder result = new StringBuilder();
            result.append("❌ config: missing subcommand\n");
            result.append("💡 Usage: server config [get|set] [options]\n\n");
            result.append("Commands:\n");
            result.append("  get <key>              Get configuration value\n");
            result.append("  set <key=value>        Set configuration value\n");
            result.append("  set --no-cache         Clear Lucee version cache\n\n");
            result.append("Examples:\n");
            result.append("  server config get version\n");
            result.append("  server config set version=6.2.2.91\n");
            result.append("  server config set jvm.maxMemory=512m\n");
            result.append("  server config set port=8080\n");
            return formatOutput(result.toString(), true);
        }
        
        String configCommand = args[1];
        ServerConfigHelper configHelper = new ServerConfigHelper();
        
        switch (configCommand.toLowerCase()) {
            case "get":
                return handleConfigGet(configHelper, args);
            case "set":
                return handleConfigSet(configHelper, args);
            default:
                return formatOutput("❌ Unknown config command: " + configCommand + 
                    "\n💡 Available commands: get, set", true);
        }
    }
    
    private String handleConfigGet(ServerConfigHelper configHelper, String[] args) throws Exception {
        if (args.length < 3) {
            return formatOutput("❌ config get: missing key\n💡 Usage: server config get <key>", true);
        }
        
        String key = args[2];
        try {
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(currentWorkingDirectory);
            String value = configHelper.getConfigValue(config, key);
            
            if (value != null) {
                return formatOutput(key + "=" + value, false);
            } else {
                return formatOutput("❌ Configuration key '" + key + "' not found\n" +
                    "💡 Available keys: " + String.join(", ", configHelper.getAvailableKeys()), true);
            }
        } catch (Exception e) {
            return formatOutput("❌ Error reading configuration: " + e.getMessage(), true);
        }
    }
    
    private String handleConfigSet(ServerConfigHelper configHelper, String[] args) throws Exception {
        if (args.length < 3) {
            return formatOutput("❌ config set: missing key=value\n" +
                "💡 Usage: server config set <key=value> [<key=value>...]\n" +
                "💡 Example: server config set version=6.2.2.91\n" +
                "💡 Example: server config set port=8080 admin.enabled=false", true);
        }
        
        // Check for --dry-run flag
        boolean dryRun = false;
        java.util.List<String> keyValuePairs = new java.util.ArrayList<>();
        
        for (int i = 2; i < args.length; i++) {
            if ("--dry-run".equals(args[i])) {
                dryRun = true;
            } else if ("--no-cache".equals(args[i])) {
                configHelper.clearVersionCache();
            } else if (!args[i].startsWith("--")) {
                keyValuePairs.add(args[i]);
            }
        }
        
        if (keyValuePairs.isEmpty()) {
            return formatOutput("❌ config set: missing key=value\n" +
                "💡 Usage: server config set <key=value> [<key=value>...]", true);
        }
        
        try {
            // Load current config
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(currentWorkingDirectory);
            
            // Apply all key=value pairs
            for (String keyValue : keyValuePairs) {
                if (!keyValue.contains("=")) {
                    return formatOutput("❌ Invalid format. Use key=value\n" +
                        "💡 Example: server config set version=6.2.2.91", true);
                }
                
                String[] parts = keyValue.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                configHelper.setConfigValue(config, key, value);
            }
            
            // If dry-run, show the resulting config without saving
            if (dryRun) {
                StringBuilder result = new StringBuilder();
                result.append("📋 DRY RUN: Configuration would be set to:\n\n");
                result.append("Key=Value pairs:\n");
                for (String pair : keyValuePairs) {
                    result.append("  ✓ ").append(pair).append("\n");
                }
                result.append("\nResulting lucee.json:\n");
                result.append("═══════════════════════════════════════════\n");
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                    String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
                    result.append(jsonString).append("\n");
                } catch (Exception e) {
                    result.append("Error serializing config: ").append(e.getMessage()).append("\n");
                }
                result.append("═══════════════════════════════════════════\n");
                result.append("\n✅ Run without --dry-run to apply these changes.\n");
                return formatOutput(result.toString(), false);
            }
            
            // Save the config
            Path configFile = currentWorkingDirectory.resolve("lucee.json");
            LuceeServerConfig.saveConfig(config, configFile);
            
            StringBuilder result = new StringBuilder();
            result.append("✅ Configuration updated:\n");
            for (String pair : keyValuePairs) {
                result.append("  ✓ ").append(pair).append("\n");
            }
            
            return formatOutput(result.toString(), false);
        } catch (Exception e) {
            return formatOutput("❌ Error setting configuration: " + e.getMessage(), true);
        }
    }
    
    private String handleServerMonitor(String[] args) {
        if (isTerminalMode) {
            // In terminal mode, MonitorCommand returns error messages or null for success
            String result = MonitorCommand.executeMonitor(args);
            if (result != null) {
                // Error occurred, return error message for terminal display
                return formatOutput(result, true);
            } else {
                // Monitor started successfully (this won't usually return in terminal mode)
                return formatOutput("Monitor started successfully", false);
            }
        } else {
            // In CLI mode, start the monitor directly
            String result = MonitorCommand.executeMonitor(args);
            if (result != null) {
                // Error occurred
                return formatOutput(result, true);
            }
            return null; // MonitorCommand handles its own output for success case
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
    
    private String handleServerDebug(String[] args) throws Exception {
        // Execute debug command using Java implementation
        try {
            String result = org.lucee.lucli.debug.DebugCommand.executeDebug(args);
            return formatOutput(result, false);
        } catch (Exception e) {
            return formatOutput("❌ Debug command failed: " + e.getMessage() + 
                "\n💡 Make sure JMX is enabled on the target Lucee server", true);
        }
    }
    
    /**
     * Execute modules commands - Full feature parity between CLI and terminal modes
     */
    private String executeModulesCommand(String[] args) throws Exception {
        if (isTerminalMode) {
            // In terminal mode, capture output from ModuleCommand and return it
            // This provides full feature parity with CLI mode
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            
            try {
                // Redirect System.out/err to capture ModuleCommand output
                System.setOut(new java.io.PrintStream(baos));
                System.setErr(new java.io.PrintStream(baos));
                
                // Execute the full module command
                ModuleCommand.executeModule(args);
                
                // Get captured output
                String output = baos.toString().trim();
                return formatOutput(output.isEmpty() ? "✅ Module command completed" : output, false);
                
            } finally {
                // Always restore original streams
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        } else {
            // In CLI mode, execute the full module command directly
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
