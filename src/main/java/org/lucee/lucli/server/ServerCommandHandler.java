package org.lucee.lucli.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.Timer;
import org.lucee.lucli.monitoring.MonitorCommand;

/**
 * Server command handler that provides a single implementation for all server-related commands.
 * This ensures feature parity between CLI and Terminal modes.
 */
public class ServerCommandHandler {
    
    private final boolean isTerminalMode;
    private final Path currentWorkingDirectory;
    
    public ServerCommandHandler(boolean isTerminalMode, Path currentWorkingDirectory) {
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
        return formatOutput("❌ server: missing subcommand\n💡 Usage: server [start|run|stop|restart|status|list|prune|config|lock|unlock|monitor|log|debug] [options]", true);
        }
        
        String subCommand = args[0];
        LuceeServerManager serverManager = new LuceeServerManager();
        
        Timer.start("Server " + subCommand + " Command");
        
        try {
            switch (subCommand) {
                case "start":
                    return handleServerStart(serverManager, args);
                case "run":
                    return handleServerRun(serverManager, args);
                case "stop":  
                    return handleServerStop(serverManager, args);
                case "restart":
                    return handleServerRestart(serverManager, args);
                case "status":
                    return handleServerStatus(serverManager, args);
                case "info":
                    return handleServerInfo(serverManager, args);
                case "list":
                    return handleServerList(serverManager, args);
                case "prune":
                    return handleServerPrune(serverManager, args);
                case "config":
                    return handleServerConfig(serverManager, args);
                case "lock":
                    return handleServerLock(args);
                case "unlock":
                    return handleServerUnlock(args);
                case "monitor":
                    return handleServerMonitor(Arrays.copyOfRange(args, 1, args.length));
                case "log":
                    return handleServerLog(Arrays.copyOfRange(args, 1, args.length));
                case "debug":
                    return handleServerDebug(Arrays.copyOfRange(args, 1, args.length));
                default:
                    return formatOutput("❌ Unknown server command: " + subCommand + 
                        "\n💡 Available commands: start, run, stop, restart, status, list, prune, config, lock, unlock, monitor, log, debug", true);
            }
        } finally {
            Timer.stop("Server " + subCommand + " Command");
        }
    }
    
    private String handleServerStart(LuceeServerManager serverManager, String[] args) throws Exception {
        String versionOverride = null;
        boolean forceReplace = false;
        String customName = null;
        String configFileName = null;
        String environment = null;
        String webrootOverride = null;
        boolean dryRun = false;
        boolean includeLuceeConfig = false;
        boolean includeTomcatWeb = false;
        boolean includeTomcatServer = false;
        boolean includeHttpsKeystorePlan = false;
        boolean includeHttpsRedirectRules = false;
        Boolean enableLuceeOverride = null;
        boolean sandbox = false;
        Integer portOverride = null;
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
            } else if ((args[i].equals("--config") || args[i].equals("-c")) && i + 1 < args.length) {
                configFileName = args[i + 1];
                i++; // Skip next argument
            } else if ((args[i].equals("--env") || args[i].equals("--environment")) && i + 1 < args.length) {
                environment = args[i + 1];
                i++; // Skip next argument
            } else if (args[i].startsWith("--env=")) {
                environment = args[i].substring("--env=".length());
            } else if (args[i].startsWith("--environment=")) {
                environment = args[i].substring("--environment=".length());
            } else if ((args[i].equals("--webroot")) && i + 1 < args.length) {
                webrootOverride = args[i + 1];
                i++; // Skip next argument
            } else if (args[i].startsWith("--webroot=")) {
                webrootOverride = args[i].substring("--webroot=".length());
            } else if ((args[i].equals("--port") || args[i].equals("-p")) && i + 1 < args.length) {
                try {
                    portOverride = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignore) {
                    // ignore invalid port and fall back to defaults
                }
                i++; // Skip next argument
            } else if (args[i].startsWith("--port=")) {
                try {
                    portOverride = Integer.parseInt(args[i].substring("--port=".length()));
                } catch (NumberFormatException ignore) {
                    // ignore invalid port and fall back to defaults
                }
            } else if (args[i].equals("--dry-run")) {
                dryRun = true;
            } else if (args[i].equals("--include-tomcat-web")) {
                includeTomcatWeb = true;
            } else if (args[i].equals("--include-lucee")) {
                includeLuceeConfig = true;
            } else if (args[i].equals("--include-tomcat-server")) {
                includeTomcatServer = true;
            } else if (args[i].equals("--include-https-keystore-plan")) {
                includeHttpsKeystorePlan = true;
            } else if (args[i].equals("--include-https-redirect-rules")) {
                includeHttpsRedirectRules = true;
            } else if (args[i].equals("--include-all")) {
                // Convenience flag for debugging: show all available dry-run previews.
                includeTomcatWeb = true;
                includeTomcatServer = true;
                includeHttpsKeystorePlan = true;
                includeHttpsRedirectRules = true;
                includeLuceeConfig=true;
            } else if (args[i].equals("--disable-lucee")) {
                enableLuceeOverride = Boolean.FALSE;
            } else if (args[i].equals("--enable-lucee")) {
                enableLuceeOverride = Boolean.TRUE;
            } else if (args[i].equals("--sandbox")) {
                sandbox = true;
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

        // If sandbox mode is requested, disallow dry-run and preview flags which rely on lucee.json
        if (sandbox && (dryRun || includeLuceeConfig || includeTomcatWeb || includeTomcatServer
                || includeHttpsKeystorePlan || includeHttpsRedirectRules)) {
            return formatOutput("❌ --sandbox cannot be combined with --dry-run or preview flags (--include-*, --include-all).", true);
        }
        
        // Apply any configuration overrides to lucee.json before starting the server.
        if (!sandbox && (!configOverrides.isEmpty() || portOverride != null || enableLuceeOverride != null)) {
            String cfgFile = configFileName != null ? configFileName : "lucee.json";
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir, cfgFile);
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
            if (portOverride != null) {
                config.port = portOverride;
            }
            if (enableLuceeOverride != null) {
                config.enableLucee = enableLuceeOverride.booleanValue();
            }
            Path configFile = projectDir.resolve(configFileName != null ? configFileName : "lucee.json");
            LuceeServerConfig.saveConfig(config, configFile);
        }
        
        // Load final realized config for dry-run or actual startup
        String cfgFile = configFileName != null ? configFileName : "lucee.json";
        LuceeServerConfig.ServerConfig finalConfig = LuceeServerConfig.loadConfig(projectDir, cfgFile);
        
        // Apply environment overrides if --env flag was provided
        if (environment != null && !environment.trim().isEmpty()) {
            try {
                finalConfig = LuceeServerConfig.applyEnvironment(finalConfig, environment);
            } catch (IllegalArgumentException e) {
                return formatOutput("❌ " + e.getMessage(), true);
            }
        }

        // Apply one-shot webroot override (does not persist to lucee.json)
        if (webrootOverride != null && !webrootOverride.trim().isEmpty()) {
            finalConfig.webroot = webrootOverride.trim();
        }
        
        // Apply explicit Lucee enable/disable override, if provided
        if (enableLuceeOverride != null) {
            finalConfig.enableLucee = enableLuceeOverride.booleanValue();
            try {
                Path configPath = projectDir.resolve(cfgFile);
                LuceeServerConfig.saveConfig(finalConfig, configPath);
            } catch (IOException e) {
                System.err.println("Warning: Failed to persist enableLucee override to " + cfgFile + ": " + e.getMessage());
            }
        }
        
        // If dry-run, show what would happen and exit
        if (dryRun) {
            StringBuilder result = new StringBuilder();
            result.append("📋 DRY RUN: Server configuration that would be used:\n\n");
            result.append("Realized lucee.json for: ").append(projectDir);
            if (environment != null && !environment.trim().isEmpty()) {
                result.append(" (with environment: ").append(environment).append(")");
            }
            result.append("\n═══════════════════════════════════════════\n");
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalConfig);
                result.append(jsonString).append("\n");
            } catch (Exception e) {
                result.append("Error serializing config: ").append(e.getMessage());
            }
            result.append("═══════════════════════════════════════════\n");
            
            // Display Tomcat configuration files if requested
            if (includeTomcatWeb || includeTomcatServer || includeHttpsKeystorePlan || includeHttpsRedirectRules || includeLuceeConfig) {
                result.append("\nGenerated Server Preview:\n");
                result.append("═══════════════════════════════════════════\n");

                try {
                    // Get the Lucee Express directory
                    Path luceeExpressDir = serverManager.ensureLuceeExpress(finalConfig.version);
                    TomcatConfigGenerator tomcatGen = new TomcatConfigGenerator();
                    Path serverInstanceDir = serverManager.getServersDir().resolve(finalConfig.name);

                    if(includeLuceeConfig) {
                        result.append("\n📄 .CFConfig.json (patched, from lucee.json):\n");
                        result.append("────────────────────────────────────────────\n");
                        try {
                            com.fasterxml.jackson.databind.JsonNode cfConfig = LuceeServerConfig.resolveConfigurationNode(finalConfig, projectDir);
                            if (cfConfig == null || cfConfig.isNull()) {
                                result.append("No Lucee configuration defined (no configuration or configurationFile in lucee.json)\n");
                            } else {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                                String cfConfigJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfConfig);
                                result.append(cfConfigJson).append("\n");
                            }
                        } catch (Exception e) {
                            result.append("❌ Error generating .CFConfig.json preview: ").append(e.getMessage()).append("\n");
                        }
                        result.append("─────────────────────────────────────────\n");
                    }

                    if (includeTomcatServer) {
                        result.append("\n📄 server.xml (patched, from lucee.json):\n");
                        result.append("─────────────────────────────────────────\n");
                        try {
                            String serverXmlContent = tomcatGen.generatePatchedServerXmlContent(finalConfig, projectDir, serverInstanceDir, luceeExpressDir);
                            result.append(serverXmlContent).append("\n");
                        } catch (Exception e) {
                            result.append("❌ Error generating patched server.xml preview: ").append(e.getMessage()).append("\n");
                        }
                        result.append("─────────────────────────────────────────\n");
                    }

                    if (includeTomcatWeb) {
                        result.append("\n📄 web.xml (project view, with LuCLI patching):\n");
                        result.append("─────────────────────────────────────────\n");
                        try {
                            String webXmlContent = tomcatGen.generateWebXmlContent(finalConfig, projectDir, serverInstanceDir, luceeExpressDir);
                            result.append(webXmlContent).append("\n");
                        } catch (Exception e) {
                            result.append("❌ Error generating web.xml preview: ").append(e.getMessage()).append("\n");
                        }
                        result.append("─────────────────────────────────────────\n");
                    }

                    if (includeHttpsKeystorePlan) {
                        appendHttpsKeystorePlan(result, finalConfig, serverInstanceDir);
                    }

                    if (includeHttpsRedirectRules) {
                        String vendorServerXml = null;
                        try {
                            vendorServerXml = tomcatGen.generateServerXmlContent(finalConfig, serverInstanceDir, luceeExpressDir);
                        } catch (Exception e) {
                            // fall through; we'll still print a sensible default
                        }
                        appendHttpsRedirectRulesPlan(result, finalConfig, serverInstanceDir, vendorServerXml);
                    }

                    result.append("\n═══════════════════════════════════════════\n");
                } catch (Exception e) {
                    result.append("\n❌ Error building preview: ").append(e.getMessage()).append("\n");
                }
            }
            
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
        
        // Sandbox start: use in-memory configuration and background sandbox server
        if (sandbox) {
            try {
                LuceeServerManager.ServerInstance instance = serverManager.startServerSandbox(
                    projectDir,
                    versionOverride,
                    forceReplace,
                    customName,
                    agentOverrides,
                    environment,
                    webrootOverride,
                    portOverride,
                    enableLuceeOverride
                );
                StringBuilder result = new StringBuilder();
                result.append("Starting sandbox server in: ").append(projectDir).append("\n");
                result.append("   Server Name:   ").append(instance.getServerName()).append("\n");
                result.append("   Process ID:    ").append(instance.getPid()).append("\n");
                result.append("   Port:          ").append(instance.getPort()).append("\n");
                result.append("   Server Dir:    ").append(instance.getServerDir()).append("\n");
                result.append("   URL:           http://localhost:").append(instance.getPort()).append("/\n");
                result.append("(Sandbox server will be removed automatically when stopped)\n");
                return formatOutput(result.toString(), false);
            } catch (ServerConflictException e) {
                StringBuilder result = new StringBuilder();
                result.append("⚠️  ").append(e.getMessage()).append("\n\n");
                result.append("Choose an option:\n");
                result.append("  1. Replace the existing server (delete and recreate):\n");
                if (isTerminalMode) {
                    result.append("     server start --sandbox --force\n\n");
                } else {
                    result.append("     lucli server start --sandbox --force\n\n");
                }
                result.append("  2. Create server with suggested name '").append(e.getSuggestedName()).append("':\n");
                if (isTerminalMode) {
                    result.append("     server start --sandbox --name ").append(e.getSuggestedName()).append("\n\n");
                } else {
                    result.append("     lucli server start --sandbox --name ").append(e.getSuggestedName()).append("\n\n");
                }
                result.append("  3. Create server with custom name:\n");
                if (isTerminalMode) {
                    result.append("     server start --sandbox --name <your-name>\n\n");
                } else {
                    result.append("     lucli server start --sandbox --name <your-name>\n\n");
                }
                result.append("💡 Use --force to replace existing servers, or --name to specify a different name.");
                return formatOutput(result.toString(), true);
            }
        }
        
        try {
            StringBuilder result = new StringBuilder();
            boolean isLuceeEnabledForStartup = finalConfig.enableLucee;
            if (isTerminalMode) {
                // Shorter output for terminal mode
                if (isLuceeEnabledForStartup) {
                    result.append("Starting Lucee server...\n");
                } else {
                    result.append("Starting static server...\n");
                }
            } else {
                if (isLuceeEnabledForStartup) {
                    result.append("Starting Lucee server in: ").append(projectDir).append("\n");
                } else {
                    result.append("Starting static server in: ").append(projectDir).append("\n");
                }
            }
            
            LuceeServerManager.ServerInstance instance = serverManager.startServer(
                projectDir,
                versionOverride,
                forceReplace,
                customName,
                agentOverrides,
                environment,
                cfgFile
            );
            
            // Use the realized configuration (including environment and overrides) for summary output
            LuceeServerConfig.ServerConfig config = finalConfig;
            
            result.append("   Server Name:   ").append(instance.getServerName()).append("\n");
            result.append("   Process ID:    ").append(instance.getPid()).append("\n");

            // Shared summary (ports, webroot, server dir)
            appendServerSummary(result, config, projectDir, instance.getServerDir(), instance.getPort());

            result.append("   URL:           http://localhost:").append(instance.getPort()).append("\n");
            if (config.enableLucee && config.admin != null && config.admin.enabled) {
                result.append("   Admin URL:     http://localhost:")
                      .append(instance.getPort())
                      .append("/lucee/admin.cfm\n");
            }

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
    
    /**
     * Handle server run command - starts server in foreground mode with log streaming
     */
    private String handleServerRun(LuceeServerManager serverManager, String[] args) throws Exception {
        String versionOverride = null;
        boolean forceReplace = false;
        String customName = null;
        String configFileName = null;
        String environment = null;
        String webrootOverride = null;
        boolean sandbox = false;
        Integer portOverride = null;
        Boolean enableLuceeOverride = null;
        Path projectDir = currentWorkingDirectory; // Default to current directory
        
        LuceeServerManager.AgentOverrides agentOverrides = new LuceeServerManager.AgentOverrides();
        java.util.List<String> configOverrides = new java.util.ArrayList<>();
        
        // Parse additional arguments (skip "run")
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (("--version".equals(arg) || "-v".equals(arg)) && i + 1 < args.length) {
                versionOverride = args[++i];
            } else if ("--force".equals(arg) || "-f".equals(arg)) {
                forceReplace = true;
            } else if (("--name".equals(arg) || "-n".equals(arg)) && i + 1 < args.length) {
                customName = args[++i];
            } else if (("--config".equals(arg) || "-c".equals(arg)) && i + 1 < args.length) {
                configFileName = args[++i];
            } else if (("--env".equals(arg) || "--environment".equals(arg)) && i + 1 < args.length) {
                environment = args[++i];
            } else if (arg.startsWith("--env=")) {
                environment = arg.substring("--env=".length());
            } else if (arg.startsWith("--environment=")) {
                environment = arg.substring("--environment=".length());
            } else if (("--port".equals(arg) || "-p".equals(arg)) && i + 1 < args.length) {
                try {
                    portOverride = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignore) {
                    // ignore invalid port and fall back to defaults
                }
            } else if (arg.startsWith("--port=")) {
                try {
                    portOverride = Integer.parseInt(arg.substring("--port=".length()));
                } catch (NumberFormatException ignore) {
                    // ignore invalid port and fall back to defaults
                }
            } else if ("--webroot".equals(arg) && i + 1 < args.length) {
                webrootOverride = args[++i];
            } else if (arg.startsWith("--webroot=")) {
                webrootOverride = arg.substring("--webroot=".length());
            } else if ("--sandbox".equals(arg)) {
                sandbox = true;
            } else if ("--disable-lucee".equals(arg)) {
                enableLuceeOverride = Boolean.FALSE;
            } else if ("--enable-lucee".equals(arg)) {
                enableLuceeOverride = Boolean.TRUE;
            } else if ("--no-agents".equals(arg)) {
                agentOverrides.disableAllAgents = true;
            } else if ("--agents".equals(arg) && i + 1 < args.length) {
                String value = args[++i];
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (String part : value.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        ids.add(trimmed);
                    }
                }
                agentOverrides.includeAgents = ids;
            } else if ("--enable-agent".equals(arg) && i + 1 < args.length) {
                if (agentOverrides.enableAgents == null) {
                    agentOverrides.enableAgents = new java.util.HashSet<>();
                }
                agentOverrides.enableAgents.add(args[++i]);
            } else if ("--disable-agent".equals(arg) && i + 1 < args.length) {
                if (agentOverrides.disableAgents == null) {
                    agentOverrides.disableAgents = new java.util.HashSet<>();
                }
                agentOverrides.disableAgents.add(args[++i]);
            } else if (!arg.startsWith("-") && i == 1 && !arg.contains("=")) {
                // If the first non-option argument after "run" is provided and does not
                // look like a key=value override, treat it as the project directory.
                projectDir = Paths.get(arg);
            } else if (!arg.startsWith("-") && arg.contains("=")) {
                // Treat bare key=value arguments as configuration overrides that should
                // be applied to the configuration file before starting the server.
                configOverrides.add(arg);
            }
        }
        
        // Apply any configuration overrides to the selected configuration file before starting the server,
        // but only in normal (non-sandbox) mode so sandbox does not create/write lucee.json.
        if (!sandbox && (!configOverrides.isEmpty() || portOverride != null || enableLuceeOverride != null)) {
            String cfgFile = configFileName != null ? configFileName : "lucee.json";
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir, cfgFile);
            ServerConfigHelper configHelper = new ServerConfigHelper();
            for (String kv : configOverrides) {
                String[] parts = kv.split("=", 2);
                if (parts.length == 2) {
                    configHelper.setConfigValue(config, parts[0], parts[1]);
                }
            }
            if (portOverride != null) {
                config.port = portOverride;
            }
            if (enableLuceeOverride != null) {
                config.enableLucee = enableLuceeOverride.booleanValue();
            }
            Path configFile = projectDir.resolve(cfgFile);
            LuceeServerConfig.saveConfig(config, configFile);
        }
        
        // If no agent-related flags were actually set, avoid passing a non-null overrides object
        if (!agentOverrides.disableAllAgents &&
            (agentOverrides.includeAgents == null || agentOverrides.includeAgents.isEmpty()) &&
            (agentOverrides.enableAgents == null || agentOverrides.enableAgents.isEmpty()) &&
            (agentOverrides.disableAgents == null || agentOverrides.disableAgents.isEmpty())) {
            agentOverrides = null;
        }
        
        try {
            // Run server in foreground mode - this method blocks until server is stopped
            if (sandbox) {
                // Sandbox: transient foreground server with no lucee.json writes
                serverManager.runServerForegroundSandbox(projectDir, versionOverride, forceReplace, customName,
                        agentOverrides, environment, webrootOverride, portOverride, enableLuceeOverride);
            } else {
                String cfgFile = configFileName != null ? configFileName : "lucee.json";
                // For run, webrootOverride is handled inside LuceeServerManager when reading config;
                // here we just pass through the arguments-driven overrides.
                serverManager.runServerForeground(projectDir, versionOverride, forceReplace, customName,
                        agentOverrides, environment, cfgFile);
            }
            return ""; // Return empty string since output is streamed to console
            
        } catch (ServerConflictException e) {
            StringBuilder result = new StringBuilder();
            result.append("⚠️  ").append(e.getMessage()).append("\n\n");
            result.append("Choose an option:\n");
            result.append("  1. Replace the existing server (delete and recreate):\n");
            
            if (isTerminalMode) {
                result.append("     server run --force\n\n");
                result.append("  2. Create server with suggested name '").append(e.getSuggestedName()).append("':\n");
                result.append("     server run --name ").append(e.getSuggestedName()).append("\n\n");
                result.append("  3. Create server with custom name:\n");
                result.append("     server run --name <your-name>\n\n");
            } else {
                result.append("     lucli server run --force\n\n");
                result.append("  2. Create server with suggested name '").append(e.getSuggestedName()).append("':\n");
                result.append("     lucli server run --name ").append(e.getSuggestedName()).append("\n\n");
                result.append("  3. Create server with custom name:\n");
                result.append("     lucli server run --name <your-name>\n\n");
            }
            
            result.append("💡 Use --force to replace existing servers, or --name to specify a different name.");
            
            return formatOutput(result.toString(), true);
        }
    }
    
    private void appendHttpsKeystorePlan(StringBuilder result, LuceeServerConfig.ServerConfig config, Path serverInstanceDir) {
        result.append("\n🔐 HTTPS keystore plan:\n");
        result.append("─────────────────────────────────────────\n");

        if (!LuceeServerConfig.isHttpsEnabled(config)) {
            result.append("HTTPS is disabled (https.enabled=false)\n");
            result.append("─────────────────────────────────────────\n");
            return;
        }

        String host = LuceeServerConfig.getEffectiveHost(config);
        int httpsPort = LuceeServerConfig.getEffectiveHttpsPort(config);

        Path certsDir = serverInstanceDir.resolve("certs");
        Path keystorePath = certsDir.resolve("keystore.p12");
        Path passwordPath = certsDir.resolve("keystore.pass");

        result.append("Host:        ").append(host).append("\n");
        result.append("HTTPS port:   ").append(httpsPort).append("\n");
        result.append("Keystore:     ").append(keystorePath).append("\n");
        result.append("Password file:").append(passwordPath).append("\n");
        result.append("Alias:        lucli\n");

        // SANs must include localhost and the custom host to avoid browser name mismatch.
        result.append("SANs:\n");
        result.append("  - DNS:localhost\n");
        if (!"localhost".equalsIgnoreCase(host)) {
            result.append("  - DNS:").append(host).append("\n");
        }
        result.append("  - IP:127.0.0.1\n");

        result.append("Note: This is a plan only. Files are generated only when starting (no side effects in --dry-run).\n");
        result.append("─────────────────────────────────────────\n");
    }

    private void appendHttpsRedirectRulesPlan(StringBuilder result,
                                              LuceeServerConfig.ServerConfig config,
                                              Path serverInstanceDir,
                                              String vendorServerXml) {
        result.append("\n↪️  HTTPS redirect rules plan:\n");
        result.append("─────────────────────────────────────────\n");

        if (!LuceeServerConfig.isHttpsEnabled(config)) {
            result.append("HTTPS is disabled (https.enabled=false)\n");
            result.append("─────────────────────────────────────────\n");
            return;
        }

        if (!LuceeServerConfig.isHttpsRedirectEnabled(config)) {
            result.append("Redirect is disabled (https.redirect=false)\n");
            result.append("─────────────────────────────────────────\n");
            return;
        }

        String host = LuceeServerConfig.getEffectiveHost(config);
        int httpsPort = LuceeServerConfig.getEffectiveHttpsPort(config);

        String tomcatHostName = extractTomcatHostNameFromServerXml(vendorServerXml);
        Path rewriteConfigPath = serverInstanceDir
            .resolve("conf")
            .resolve("Catalina")
            .resolve(tomcatHostName)
            .resolve("rewrite.config");

        result.append("Rewrite config path: ").append(rewriteConfigPath).append("\n");
        result.append("Valve: org.apache.catalina.valves.rewrite.RewriteValve\n\n");

        String rules = "RewriteCond %{HTTPS} !=on\n" +
                       "RewriteRule ^/(.*)$ https://" + host + ":" + httpsPort + "/$1 [R=302,L]\n";

        result.append("rewrite.config contents:\n");
        result.append(rules);
        result.append("─────────────────────────────────────────\n");
    }

    private String extractTomcatHostNameFromServerXml(String serverXml) {
        if (serverXml == null || serverXml.trim().isEmpty()) {
            return "localhost";
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(serverXml.getBytes(StandardCharsets.UTF_8)));

            NodeList engines = document.getElementsByTagName("Engine");
            if (engines == null || engines.getLength() == 0 || !(engines.item(0) instanceof Element)) {
                return "localhost";
            }

            Element engine = (Element) engines.item(0);
            String defaultHost = engine.getAttribute("defaultHost");

            NodeList hosts = engine.getElementsByTagName("Host");
            if (hosts == null || hosts.getLength() == 0) {
                return (defaultHost != null && !defaultHost.isEmpty()) ? defaultHost : "localhost";
            }

            // Prefer the Host matching defaultHost.
            if (defaultHost != null && !defaultHost.isEmpty()) {
                for (int i = 0; i < hosts.getLength(); i++) {
                    if (hosts.item(i) instanceof Element) {
                        Element host = (Element) hosts.item(i);
                        if (defaultHost.equals(host.getAttribute("name"))) {
                            return defaultHost;
                        }
                    }
                }
            }

            // Fall back to the first Host name.
            for (int i = 0; i < hosts.getLength(); i++) {
                if (hosts.item(i) instanceof Element) {
                    Element host = (Element) hosts.item(i);
                    String name = host.getAttribute("name");
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }

            return (defaultHost != null && !defaultHost.isEmpty()) ? defaultHost : "localhost";
        } catch (Exception e) {
            return "localhost";
        }
    }

    private String handleServerStop(LuceeServerManager serverManager, String[] args) throws Exception {
        String serverName = null;
        boolean stopAll = false;
        
        // Parse --name and --all flags (skip "stop")
        for (int i = 1; i < args.length; i++) {
            if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                break;
            } else if (args[i].equals("--all")) {
                stopAll = true;
                break;
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if (stopAll) {
            // Stop all running servers
            List<LuceeServerManager.ServerInfo> servers = serverManager.listServers();
            List<LuceeServerManager.ServerInfo> runningServers = servers.stream()
                .filter(LuceeServerManager.ServerInfo::isRunning)
                .collect(java.util.stream.Collectors.toList());
            
            if (runningServers.isEmpty()) {
                result.append("ℹ️  No running servers found.");
            } else {
                result.append("Stopping all running servers...\n");
                int stopped = 0;
                for (LuceeServerManager.ServerInfo server : runningServers) {
                    try {
                        boolean success = serverManager.stopServerByName(server.getServerName());
                        if (success) {
                            result.append("✅ Stopped: ").append(server.getServerName()).append("\n");
                            stopped++;
                        } else {
                            result.append("❌ Failed to stop: ").append(server.getServerName()).append("\n");
                        }
                    } catch (Exception e) {
                        result.append("❌ Error stopping ").append(server.getServerName()).append(": ").append(e.getMessage()).append("\n");
                    }
                }
                result.append("\n✅ Stopped ").append(stopped).append(" of ").append(runningServers.size()).append(" servers.");
            }
        } else if (serverName != null) {
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
                result.append("   Server Name:   ").append(serverInfo.getServerName());
                if (serverInfo.getEnvironment() != null) {
                    result.append(" [env: ").append(serverInfo.getEnvironment()).append("]");
                }
                result.append("\n");
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

    /**
     * Handle server info command - show configuration overview without starting the server.
     */
    private String handleServerInfo(LuceeServerManager serverManager, String[] args) throws Exception {
        String configFileName = null;
        String environment = null;
        String webrootOverride = null;
        Path projectDir = currentWorkingDirectory; // Default to current directory

        // Parse additional arguments (skip "info")
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (("--config".equals(arg) || "-c".equals(arg)) && i + 1 < args.length) {
                configFileName = args[++i];
            } else if (arg.startsWith("--config=")) {
                configFileName = arg.substring("--config=".length());
            } else if (("--env".equals(arg) || "--environment".equals(arg)) && i + 1 < args.length) {
                environment = args[++i];
            } else if (arg.startsWith("--env=")) {
                environment = arg.substring("--env=".length());
            } else if (arg.startsWith("--environment=")) {
                environment = arg.substring("--environment=".length());
            } else if ("--webroot".equals(arg) && i + 1 < args.length) {
                webrootOverride = args[++i];
            } else if (arg.startsWith("--webroot=")) {
                webrootOverride = arg.substring("--webroot=".length());
            } else if (!arg.startsWith("-") && i == 1 && !arg.contains("=")) {
                // Treat first non-option argument as project directory
                projectDir = Paths.get(arg);
            }
        }

        String cfgFile = configFileName != null ? configFileName : "lucee.json";
        Path cfgPath = projectDir.resolve(cfgFile);

        // Honour "no lucee.json" requirement: do not create a default file here.
        if (!Files.exists(cfgPath)) {
            StringBuilder missing = new StringBuilder();
            missing.append("❌ Config file not found: ")
                   .append(cfgPath.toAbsolutePath())
                   .append("\n");
            missing.append("   Use 'lucli server new' to create a new lucee.json for this project.\n");
            return formatOutput(missing.toString(), true);
        }

        LuceeServerConfig.ServerConfig config;
        try {
            config = LuceeServerConfig.loadConfig(projectDir, cfgFile);
        } catch (IOException e) {
            return formatOutput("❌ Error reading configuration: " + e.getMessage(), true);
        }

        // Apply environment overrides if specified
        if (environment != null && !environment.trim().isEmpty()) {
            try {
                config = LuceeServerConfig.applyEnvironment(config, environment.trim());
            } catch (IllegalArgumentException e) {
                return formatOutput("❌ " + e.getMessage(), true);
            }
        }

        // Apply one-shot webroot override (does not persist to lucee.json)
        if (webrootOverride != null && !webrootOverride.trim().isEmpty()) {
            config.webroot = webrootOverride.trim();
        }

        StringBuilder result = new StringBuilder();
        result.append("Server configuration for: ").append(projectDir).append("\n");
        result.append("   Config File:   ").append(cfgPath.toAbsolutePath()).append("\n");
        result.append("   Server Name:   ").append(config.name).append("\n");
        if (environment != null && !environment.trim().isEmpty()) {
            result.append("   Environment:   ").append(environment.trim()).append("\n");
        }
        result.append("   Lucee Enabled: ").append(config.enableLucee ? "yes" : "no").append("\n");
        result.append("   Lucee Version: ").append(config.version).append("\n");

        // Expected server directory for this configuration
        Path serverDir = serverManager.getServersDir().resolve(config.name);

        // Shared summary (ports, webroot, server dir)
        appendServerSummary(result, config, projectDir, serverDir, null);

        if (LuceeServerConfig.isHttpsEnabled(config)) {
            result.append("   HTTPS Port:    ").append(LuceeServerConfig.getEffectiveHttpsPort(config)).append("\n");
            result.append("   HTTPS Redirect:")
                  .append(LuceeServerConfig.isHttpsRedirectEnabled(config) ? " enabled" : " disabled")
                  .append("\n");
        } else {
            result.append("   HTTPS:         disabled\n");
        }

        if (config.admin != null) {
            result.append("   Admin Enabled: ").append(config.admin.enabled ? "yes" : "no").append("\n");
        }

        // Show whether a server is currently running for this project
        // Show whether a server is currently running for this project
        LuceeServerManager.ServerStatus status = serverManager.getServerStatus(projectDir);
        result.append("   Status:        ");
        if (status.isRunning()) {
            result.append("RUNNING (PID ")
                  .append(status.getPid())
                  .append(", Port ")
                  .append(status.getPort())
                  .append(")");
        } else {
            result.append("NOT RUNNING");
        }

        return formatOutput(result.toString(), false);
    }

    /**
     * Append a shared server summary (ports, JMX, webroot, server dir).
     */
    private void appendServerSummary(StringBuilder result,
                                     LuceeServerConfig.ServerConfig config,
                                     Path projectDir,
                                     Path serverDir,
                                     Integer explicitHttpPort) {
        // Determine HTTP port (instance port when available, otherwise config.port)
        int httpPort = explicitHttpPort != null ? explicitHttpPort.intValue() : config.port;

        // Compute effective webroot for display
        Path effectiveWebroot = LuceeServerConfig.resolveWebroot(config, projectDir);

        result.append("   HTTP Port:     ").append(httpPort).append("\n");
        result.append("   Shutdown Port: ")
              .append(LuceeServerConfig.getEffectiveShutdownPort(config))
              .append("\n");

        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            result.append("   JMX Port:      ")
                  .append(config.monitoring.jmx.port)
                  .append("\n");
        }

        result.append("   Web Root:      ")
              .append(effectiveWebroot)
              .append("\n");

        if (serverDir != null) {
            result.append("   Server Dir:    ")
                  .append(serverDir)
                  .append("\n");
        }
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
            
            String serverNameDisplay = server.getServerName();
            if (server.getEnvironment() != null) {
                serverNameDisplay += " [" + server.getEnvironment() + "]";
            }
            
            String webroot = server.getProjectDir() != null ? server.getProjectDir().toString() : "<unknown>";
            if (isTerminalMode) {
                // Condensed format
                result.append(String.format("%-20s %-10s %-8s %-10s %s\n", 
                    serverNameDisplay, status, pid, port, webroot));
            } else {
                // Full format
                
                // Truncate long paths for better display
                if (webroot.length() > 38) {
                    webroot = "..." + webroot.substring(webroot.length() - 35);
                }
                
                result.append(String.format("%-20s %-10s %-8s %-10s %-40s %s\n", 
                    serverNameDisplay, status, pid, port, webroot, server.getServerDir()));
            }
        }
        
        return formatOutput(result.toString().trim(), false);
    }
    
    private String handleServerPrune(LuceeServerManager serverManager, String[] args) throws Exception {
        boolean pruneAll = false;
        String serverName = null;
        boolean force = false;
        
        // Parse arguments (skip "prune")
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--all") || args[i].equals("-a")) {
                pruneAll = true;
            } else if ((args[i].equals("--name") || args[i].equals("-n")) && i + 1 < args.length) {
                serverName = args[i + 1];
                i++; // Skip next argument
            } else if (args[i].equals("--force") || args[i].equals("-f")) {
                force = true;
            } else if (!args[i].startsWith("-")) {
                // Treat non-option argument as server name
                serverName = args[i];
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if (pruneAll) {
            // Get list of stopped servers before confirming
            java.util.List<LuceeServerManager.ServerInfo> servers = serverManager.listServers();
            java.util.List<LuceeServerManager.ServerInfo> stoppedServers = servers.stream()
                .filter(s -> !s.isRunning())
                .collect(java.util.stream.Collectors.toList());
            
            if (stoppedServers.isEmpty()) {
                return formatOutput("ℹ️  No stopped servers found to prune.", false);
            }
            
            // Show confirmation prompt unless --force is used
            if (!force) {
                result.append("⚠️  You are about to prune ").append(stoppedServers.size()).append(" stopped server(s):\n");
                for (LuceeServerManager.ServerInfo server : stoppedServers) {
                    result.append("   • ").append(server.getServerName()).append("\n");
                }
                result.append("\nThis will permanently delete server files and cannot be undone.\n");
                result.append("Are you sure? (y/N): ");
                
                // In CLI mode, we need user input
                System.out.print(result.toString());
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                scanner.close();
                if (!response.equals("y") && !response.equals("yes")) {
                    return formatOutput("❌ Prune operation cancelled.", false);
                }
                
                result.setLength(0); // Clear the buffer for the actual result
            }
            
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
            // Show confirmation prompt unless --force is used
            if (!force) {
                result.append("⚠️  You are about to prune server: ").append(serverName).append("\n");
                result.append("This will permanently delete server files and cannot be undone.\n");
                result.append("Are you sure? (y/N): ");
                
                System.out.print(result.toString());
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                
                if (!response.equals("y") && !response.equals("yes")) {
                    return formatOutput("❌ Prune operation cancelled.", false);
                }
                
                result.setLength(0);
            }
            
            // Prune specific server by name
            LuceeServerManager.PruneResult pruneResult = serverManager.pruneServerByName(serverName);
            
            if (pruneResult.isSuccess()) {
                result.append("✅ Server '").append(serverName).append("' pruned successfully.");
            } else {
                result.append("❌ Failed to prune server '").append(serverName).append("': ").append(pruneResult.getMessage());
                return formatOutput(result.toString(), true);
            }
        } else {
            // Get server name from config for confirmation prompt
            String serverNameFromConfig = null;
            try {
                LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(currentWorkingDirectory);
                serverNameFromConfig = config.name;
            } catch (Exception e) {
                return formatOutput("❌ No server configuration found for current directory.", true);
            }
            
            // Show confirmation prompt unless --force is used
            if (!force) {
                result.append("⚠️  You are about to prune server: ").append(serverNameFromConfig).append("\n");
                result.append("This will permanently delete server files and cannot be undone.\n");
                result.append("Are you sure? (y/N): ");
                
                System.out.print(result.toString());
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                
                if (!response.equals("y") && !response.equals("yes")) {
                    return formatOutput("❌ Prune operation cancelled.", false);
                }
                
                result.setLength(0);
            }
            
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

    private String handleServerLock(String[] args) throws Exception {
        // server lock [--env ENV] [--config FILE] [--update]
        String environment = null;
        String configFileName = null;
        boolean update = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (("--env".equals(arg) || "--environment".equals(arg)) && i + 1 < args.length) {
                environment = args[++i];
            } else if (arg.startsWith("--env=")) {
                environment = arg.substring("--env=".length());
            } else if (arg.startsWith("--environment=")) {
                environment = arg.substring("--environment=".length());
            } else if (("--config".equals(arg) || "-c".equals(arg)) && i + 1 < args.length) {
                configFileName = args[++i];
            } else if (arg.startsWith("--config=")) {
                configFileName = arg.substring("--config=".length());
            } else if ("--update".equals(arg)) {
                update = true;
            }
        }

        String envKey = (environment == null || environment.trim().isEmpty()) ? "_default" : environment.trim();
        String cfgFile = (configFileName != null && !configFileName.trim().isEmpty()) ? configFileName : "lucee.json";

        try {
            // Load effective config for this env
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(currentWorkingDirectory, cfgFile);
            if (environment != null && !environment.trim().isEmpty()) {
                config = LuceeServerConfig.applyEnvironment(config, environment.trim());
            }

            // For server lock, resolve secrets so the locked snapshot does not
            // depend on being able to read the secret store at runtime.
            LuceeServerConfig.resolveSecretPlaceholders(config, currentWorkingDirectory);

            // Compute hash of the underlying config file
            Path cfgPath = currentWorkingDirectory.resolve(cfgFile);
            String hash = org.lucee.lucli.config.LuceeLockFile.computeConfigHash(cfgPath);

            org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(currentWorkingDirectory);
            org.lucee.lucli.config.LuceeLockFile.ServerLock existing = lockFile.getServerLock(envKey);

            if (existing != null && existing.locked && !update) {
                StringBuilder msg = new StringBuilder();
                msg.append("❌ Server configuration is already locked for env '").append(envKey).append("'.\n");
                msg.append("   Use 'lucli server lock --env=").append("_default".equals(envKey) ? "" : envKey)
                   .append(" --update' to refresh from the current ").append(cfgFile).append(".\n");
                msg.append("   Or 'lucli server unlock");
                if (!"_default".equals(envKey)) {
                    msg.append(" --env=").append(envKey);
                }
                msg.append("' to remove the lock.\n");
                return formatOutput(msg.toString(), true);
            }

            org.lucee.lucli.config.LuceeLockFile.ServerLock newLock = new org.lucee.lucli.config.LuceeLockFile.ServerLock();
            newLock.locked = true;
            newLock.environment = envKey;
            newLock.configFile = cfgFile;
            newLock.configHash = hash;
            newLock.effectiveConfig = config;
            newLock.lockedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            lockFile.putServerLock(envKey, newLock);
            lockFile.write(currentWorkingDirectory.toFile());

            StringBuilder result = new StringBuilder();
            result.append("🔒 Server configuration locked for env '").append(envKey).append("'.\n");
            result.append("   Source file: ").append(cfgFile).append("\n");
            result.append("   Future 'lucli server start");
            if (!"_default".equals(envKey)) {
                result.append(" --env=").append(envKey);
            }
            result.append("' will use this locked configuration snapshot.\n");
            return formatOutput(result.toString(), false);
        } catch (Exception e) {
            return formatOutput("❌ Failed to lock server configuration: " + e.getMessage(), true);
        }
    }

    private String handleServerUnlock(String[] args) throws Exception {
        // server unlock [--env ENV]
        String environment = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (("--env".equals(arg) || "--environment".equals(arg)) && i + 1 < args.length) {
                environment = args[++i];
            } else if (arg.startsWith("--env=")) {
                environment = arg.substring("--env=".length());
            } else if (arg.startsWith("--environment=")) {
                environment = arg.substring("--environment=".length());
            }
        }

        String envKey = (environment == null || environment.trim().isEmpty()) ? "_default" : environment.trim();

        try {
            org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(currentWorkingDirectory);
            org.lucee.lucli.config.LuceeLockFile.ServerLock existing = lockFile.getServerLock(envKey);

            if (existing == null || !existing.locked) {
                StringBuilder msg = new StringBuilder();
                msg.append("ℹ️  No active server lock found for env '").append(envKey).append("'.\n");
                return formatOutput(msg.toString(), false);
            }

            existing.locked = false;
            lockFile.putServerLock(envKey, existing);
            lockFile.write(currentWorkingDirectory.toFile());

            StringBuilder result = new StringBuilder();
            result.append("🔓 Server configuration unlocked for env '").append(envKey).append("'.\n");
            result.append("   Future 'lucli server start");
            if (!"_default".equals(envKey)) {
                result.append(" --env=").append(envKey);
            }
            result.append("' will use live lucee.json configuration.\n");
            return formatOutput(result.toString(), false);
        } catch (Exception e) {
            return formatOutput("❌ Failed to unlock server configuration: " + e.getMessage(), true);
        }
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
            return formatOutput("❌ config get: missing key\n💡 Usage: server config get <key>\\n" +
                "💡 Example: server config get port\\n" +
                "💡 Example: server config get admin.enabled\\n" +
                "💡 Example: server config get serverDir (virtual key - shows Tomcat instance location)\\n" +
                "💡 Example: server config get configuration --env=prod (export Lucee CFConfig for an environment)", true);
        }
        
        String key = args[2];
        
        // Optional flags: --env / --environment, --config
        String environment = null;
        String configFileName = null;
        Path projectDir = currentWorkingDirectory;
        
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if (("--env".equals(arg) || "--environment".equals(arg)) && i + 1 < args.length) {
                environment = args[++i];
            } else if (arg.startsWith("--env=")) {
                environment = arg.substring("--env=".length());
            } else if (arg.startsWith("--environment=")) {
                environment = arg.substring("--environment=".length());
            } else if (("--config".equals(arg) || "-c".equals(arg)) && i + 1 < args.length) {
                configFileName = args[++i];
            } else if (arg.startsWith("--config=")) {
                configFileName = arg.substring("--config=".length());
            }
        }
        
        String cfgFile = configFileName != null && !configFileName.trim().isEmpty()
            ? configFileName.trim()
            : "lucee.json";
        
        try {
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir, cfgFile);
            
            // Apply environment overrides if specified
            if (environment != null && !environment.trim().isEmpty()) {
                try {
                    config = LuceeServerConfig.applyEnvironment(config, environment.trim());
                } catch (IllegalArgumentException e) {
                    return formatOutput("❌ " + e.getMessage(), true);
                }
            }
            
            // Special key: configuration -> export realized Lucee CFConfig JSON
            if ("configuration".equals(key)) {
                try {
                    com.fasterxml.jackson.databind.JsonNode cfConfig = LuceeServerConfig.resolveConfigurationNode(config, projectDir);
                    if (cfConfig == null || cfConfig.isNull()) {
                        return formatOutput("No Lucee configuration defined (no configuration or configurationFile in lucee.json)", false);
                    }
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                    String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfConfig);
                    // Output raw JSON so it can be piped or redirected easily
                    return formatOutput(jsonString, false);
                } catch (Exception e) {
                    return formatOutput("❌ Error resolving Lucee configuration: " + e.getMessage(), true);
                }
            }
            
            String value = null;
            
            // Handle virtual keys that don't exist in config but are computed
            if ("serverDir".equals(key)) {
                // serverDir is a virtual key that returns the server instance directory path
                Path lucliHome = Paths.get(System.getProperty("user.home"), ".lucli");
                Path serverInstanceDir = lucliHome.resolve("servers").resolve(config.name);
                value = serverInstanceDir.toString();
            } else {
                value = configHelper.getConfigValue(config, key);
            }
            
            StringBuilder result = new StringBuilder();
            
            // Warn if key is unknown but might still exist in the config
            if (!configHelper.isKnownKey(key) && !"serverDir".equals(key)) {
                result.append("⚠️  Unknown configuration key (may not be officially supported):\\n");
                result.append("  ⚠️  ").append(key).append("\\n\\n");
            }
            
            if (value != null) {
                result.append("✅ ").append(key).append("=\\n");
                result.append("   Value: ").append(value);
                return formatOutput(result.toString(), false);
            } else {
                result.append("❌ Configuration key '" + key + "' not found\\n\\n");
                result.append("Available keys:\\n");
                for (String availKey : configHelper.getAvailableKeys()) {
                    result.append("  • ").append(availKey).append("\\n");
                }
                result.append("\\nVirtual keys (read-only, computed values):\\n");
                result.append("  • serverDir - Location of Tomcat server instance (~/.lucli/servers/<name>)\\n");
                return formatOutput(result.toString(), true);
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

        // If not a dry-run, prevent modifying lucee.json when any environment is locked.
        if (!dryRun) {
            try {
                org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(currentWorkingDirectory);
                java.util.Set<String> lockedEnvs = lockFile.getLockedEnvironments();
                if (!lockedEnvs.isEmpty()) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("❌ Cannot modify lucee.json via 'server config set' because server configuration is LOCKED.\n");
                    msg.append("   Locked environments: ");
                    msg.append(String.join(", ", lockedEnvs));
                    msg.append("\n\nTo change configuration:\n");
                    msg.append("  - Unlock:        lucli server unlock --env=<env>\n");
                    msg.append("  - Or update lock: lucli server lock --env=<env> --update\n");
                    return formatOutput(msg.toString(), true);
                }
            } catch (Exception e) {
                // Fail-open on lock read errors; print a warning and continue.
                System.err.println("Warning: Failed to read lucee-lock.json for server lock check: " + e.getMessage());
            }
        }
        
        try {
            // Load current config
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(currentWorkingDirectory);
            
            // Track unknown keys for warnings
            java.util.List<String> unknownKeys = new java.util.ArrayList<>();
            
            // Apply all key=value pairs
            for (String keyValue : keyValuePairs) {
                if (!keyValue.contains("=")) {
                    return formatOutput("❌ Invalid format. Use key=value\n" +
                        "💡 Example: server config set version=6.2.2.91", true);
                }
                
                String[] parts = keyValue.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                // Check for virtual/read-only keys
                if ("serverDir".equals(key)) {
                    return formatOutput("❌ Cannot set 'serverDir' - it is a virtual read-only key computed from the server name\n" +
                        "💡 serverDir is always: ~/.lucli/servers/<server-name>", true);
                }
                
                // Warn if key is not known, but set it anyway
                if (!configHelper.isKnownKey(key)) {
                    unknownKeys.add(key);
                }
                
                configHelper.setConfigValue(config, key, value);
            }
            
            // If dry-run, show the resulting config without saving
            if (dryRun) {
                StringBuilder result = new StringBuilder();
                result.append("📋 DRY RUN: Configuration would be set to:\n\n");
                
                // Show warnings for unknown keys
                if (!unknownKeys.isEmpty()) {
                    result.append("⚠️  Unknown configuration keys (will be set anyway):\n");
                    for (String unknownKey : unknownKeys) {
                        result.append("  ⚠️  ").append(unknownKey).append("\n");
                    }
                    result.append("\n");
                }
                
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
            
            // Show warnings for unknown keys
            if (!unknownKeys.isEmpty()) {
                result.append("⚠️  Unknown configuration keys (set anyway):\n");
                for (String unknownKey : unknownKeys) {
                    result.append("  ⚠️  ").append(unknownKey).append("\n");
                }
                result.append("\n");
            }
            
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
