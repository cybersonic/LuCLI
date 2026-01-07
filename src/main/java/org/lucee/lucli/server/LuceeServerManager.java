package org.lucee.lucli.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.net.URI;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;

import org.lucee.lucli.deps.ExtensionDependencyInstaller;

/**
 * Manages Lucee server instances - downloading, configuring, starting, and stopping servers
 */
public class LuceeServerManager {
    
    /**
     * Runtime overrides for Java agent activation when starting a server.
     * This does not modify persisted configuration in lucee.json.
     */
    public static class AgentOverrides {
        public boolean disableAllAgents;
        public Set<String> includeAgents;  // When non-empty, defines the exact set of agents to enable
        public Set<String> enableAgents;
        public Set<String> disableAgents;
    }
    
    private static final String LUCEE_CDN_URL_TEMPLATE = "https://cdn.lucee.org/lucee-express-{version}.zip";
    private static final String DEFAULT_VERSION = "6.2.2.91";
    
    private final Path lucliHome;
    private final Path expressDir;
    private final Path serversDir;
    
    public LuceeServerManager() throws IOException {
        this.lucliHome = getLucliHome();
        this.expressDir = lucliHome.resolve("express");
        this.serversDir = lucliHome.resolve("servers");
        
        // Ensure directories exist
        Files.createDirectories(expressDir);
        Files.createDirectories(serversDir);
    }
    
    /**
     * Get the servers directory where all server instances are stored
     */
    public Path getServersDir() {
        return serversDir;
    }
    
    /**
     * Get the LuCLI home directory
     */
    private static Path getLucliHome() {
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = Paths.get(userHome, ".lucli").toString();
        }
        return Paths.get(lucliHomeStr);
    }
    
/**
     * Start a Lucee server for the current project directory
     */
    public ServerInstance startServer(Path projectDir, String versionOverride) throws Exception {
        return startServer(projectDir, versionOverride, false, null, null, null, null);
    }
    
    /**
     * Start a Lucee server with options for handling existing servers
     */
    public ServerInstance startServer(Path projectDir, String versionOverride, boolean forceReplace, String customName) throws Exception {
        return startServer(projectDir, versionOverride, forceReplace, customName, null, null, null);
    }
    
    /**
     * Start a Lucee server with agent overrides (no environment)
     */
    public ServerInstance startServer(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                      AgentOverrides agentOverrides) throws Exception {
        return startServer(projectDir, versionOverride, forceReplace, customName, agentOverrides, null, null);
    }
    
    /**
     * Core server startup method that also accepts agent overrides and environment name.
     * 
     * @param projectDir The project directory
     * @param versionOverride Optional version override (can be null)
     * @param forceReplace Whether to force replace an existing server
     * @param customName Optional custom name (can be null)
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name to apply (can be null)
     * @return ServerInstance for the started server
     */
    public ServerInstance startServer(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                      AgentOverrides agentOverrides, String environment) throws Exception {
        return startServer(projectDir, versionOverride, forceReplace, customName, agentOverrides, environment, null);
    }
    
    /**
     * Core server startup method that also accepts an explicit configuration file name.
     *
     * @param projectDir The project directory
     * @param versionOverride Optional version override (can be null)
     * @param forceReplace Whether to force replace an existing server
     * @param customName Optional custom name (can be null)
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name to apply (can be null)
     * @param configFileName Optional configuration file name (e.g., "lucee-static.json"). When null, uses "lucee.json".
     * @return ServerInstance for the started server
     */
    public ServerInstance startServer(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                      AgentOverrides agentOverrides, String environment, String configFileName) throws Exception {
        return startServerInternal(projectDir, versionOverride, forceReplace, customName, agentOverrides, environment, configFileName, false);
    }
    
    /**
     * Run a Lucee server in foreground mode (similar to catalina.sh run).
     * This starts Tomcat in-process with console output streaming.
     * When the process is stopped (Ctrl+C), the server stops.
     * 
     * @param projectDir The project directory
     * @param versionOverride Optional version override (can be null)
     * @param forceReplace Whether to force replace an existing server
     * @param customName Optional custom name (can be null)
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name to apply (can be null)
     */
    public void runServerForeground(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                    AgentOverrides agentOverrides, String environment) throws Exception {
        runServerForeground(projectDir, versionOverride, forceReplace, customName, agentOverrides, environment, null);
    }
    
    /**
     * Run a Lucee server in foreground mode (similar to catalina.sh run) with an explicit config file.
     * This starts Tomcat in-process with console output streaming.
     * When the process is stopped (Ctrl+C), the server stops.
     *
     * @param projectDir The project directory
     * @param versionOverride Optional version override (can be null)
     * @param forceReplace Whether to force replace an existing server
     * @param customName Optional custom name (can be null)
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name to apply (can be null)
     * @param configFileName Optional configuration file name (e.g., "lucee-static.json"). When null, uses "lucee.json".
     */
    public void runServerForeground(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                    AgentOverrides agentOverrides, String environment, String configFileName) throws Exception {
        startServerInternal(projectDir, versionOverride, forceReplace, customName, agentOverrides, environment, configFileName, true);
    }
    
    /**
     * Internal server startup method that handles both background and foreground modes.
     * 
     * @param projectDir The project directory
     * @param versionOverride Optional version override (can be null)
     * @param forceReplace Whether to force replace an existing server
     * @param customName Optional custom name (can be null)
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name to apply (can be null)
     * @param configFileName Optional configuration file name (e.g., "lucee-static.json"). When null, uses "lucee.json".
     * @param foreground If true, runs in foreground mode (blocks); if false, runs in background
     * @return ServerInstance (only when foreground=false; returns null when foreground=true)
     */
    private ServerInstance startServerInternal(Path projectDir, String versionOverride, boolean forceReplace, String customName,
                                      AgentOverrides agentOverrides, String environment, String configFileName, boolean foreground) throws Exception {
        // Determine which configuration file to load
        String cfgFile = (configFileName != null && !configFileName.trim().isEmpty())
                ? configFileName
                : "lucee.json";
        // Load configuration
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir, cfgFile);
        
        // Apply environment overrides if specified
        if (environment != null && !environment.trim().isEmpty()) {
            config = LuceeServerConfig.applyEnvironment(config, environment);
        }
        
        // Override version if specified
        if (versionOverride != null && !versionOverride.trim().isEmpty()) {
            config.version = versionOverride;
        }
        
        // Use custom name if provided
        if (customName != null && !customName.trim().isEmpty()) {
            config.name = customName.trim();
        }
        
        // Disable browser opening for foreground mode
        if (foreground) {
            config.openBrowser = false;
        }
        
        // Check if server with this name already exists
        Path existingServerDir = serversDir.resolve(config.name);
        if (Files.exists(existingServerDir)) {
            // Check if the existing server is running
            boolean isRunning = isServerRunning(config.name);
            
            if (isRunning) {
                throw new IllegalStateException("Server '" + config.name + "' is already running. Please stop it first or use a different name.");
            }
            
            // Check if the existing server was created for this project directory
            ServerInfo existingServerInfo = getExistingServerForProject(projectDir, config.name);
            
            if (existingServerInfo != null) {
                // This server exists and matches the current project directory, just restart it
                System.out.println("Restarting existing server '" + config.name + "' for this project...");
                // Don't throw an exception - continue with server startup
            } else if (!forceReplace) {
                // Server exists but doesn't match this project directory
                String suggestedName = LuceeServerConfig.getUniqueServerName(config.name, serversDir);
                throw new ServerConflictException(config.name, suggestedName, false);
            } else {
                // Force replace: delete existing server directory
                deleteServerDirectory(existingServerDir);
            }
        }
        
        // Check if there's a running server for this project directory
        ServerInstance existingInstance = getRunningServer(projectDir);
        if (existingInstance != null) {
            throw new IllegalStateException("Server already running for project: " + existingInstance.getServerName() + 
                                          " (PID: " + existingInstance.getPid() + ", Port: " + existingInstance.getPort() + ")");
        }
        
        // Ensure Lucee Express is available
        Path luceeExpressDir = ensureLuceeExpress(config.version);
        
        // Resolve port conflicts right before starting server to avoid race conditions
        LuceeServerConfig.PortConflictResult portResult = LuceeServerConfig.resolvePortConflicts(config, false, this);
        
        if (portResult.hasConflicts) {
            // Check for specific server conflicts and provide helpful messages
            String httpPortServer = getServerUsingPort(config.port);
            String shutdownPortServer = getServerUsingPort(LuceeServerConfig.getShutdownPort(config.port));
            String jmxPortServer = null;
            if (config.monitoring != null && config.monitoring.jmx != null) {
                jmxPortServer = getServerUsingPort(config.monitoring.jmx.port);
            }
            
            StringBuilder errorMessage = new StringBuilder("Cannot start server - port conflicts detected:\n\n");
            
            if (!LuceeServerConfig.isPortAvailable(config.port)) {
                if (httpPortServer != null) {
                    errorMessage.append("• HTTP port ").append(config.port)
                            .append(" is being used by Lucee server '").append(httpPortServer).append("'\n")
                            .append("  Use: lucli server stop ").append(httpPortServer).append(" (to stop the server)\n")
                            .append("  Or change the port in your lucee.json file\n\n");
                } else {
                    errorMessage.append("• HTTP port ").append(config.port)
                            .append(" is already in use by another process\n")
                            .append("  Use: lsof -i :").append(config.port).append(" (to see what's using the port)\n")
                            .append("  Or change the port in your lucee.json file\n\n");
                }
            }
            
            int shutdownPort = LuceeServerConfig.getShutdownPort(config.port);
            if (!LuceeServerConfig.isPortAvailable(shutdownPort)) {
                if (shutdownPortServer != null) {
                    errorMessage.append("• Shutdown port ").append(shutdownPort)
                            .append(" (HTTP port + 1000) is being used by Lucee server '").append(shutdownPortServer).append("'\n")
                            .append("  Use: lucli server stop ").append(shutdownPortServer).append(" (to stop the server)\n")
                            .append("  Or change the HTTP port in your lucee.json file\n\n");
                } else {
                    errorMessage.append("• Shutdown port ").append(shutdownPort)
                            .append(" (HTTP port + 1000) is already in use by another process\n")
                            .append("  Use: lsof -i :").append(shutdownPort).append(" (to see what's using the port)\n")
                            .append("  Or change the HTTP port in your lucee.json file\n\n");
                }
            }
            
            if (config.monitoring != null && config.monitoring.jmx != null && config.monitoring.enabled && !LuceeServerConfig.isPortAvailable(config.monitoring.jmx.port)) {
                if (jmxPortServer != null) {
                    errorMessage.append("• JMX port ").append(config.monitoring.jmx.port)
                            .append(" is being used by Lucee server '").append(jmxPortServer).append("'\n")
                            .append("  Use: lucli server stop ").append(jmxPortServer).append(" (to stop the server)\n")
                            .append("  Or change the JMX port in your lucee.json file\n\n");
                } else {
                    errorMessage.append("• JMX port ").append(config.monitoring.jmx.port)
                            .append(" is already in use by another process\n")
                            .append("  Use: lsof -i :").append(config.monitoring.jmx.port).append(" (to see what's using the port)\n")
                            .append("  Or change the JMX port in your lucee.json file\n\n");
                }
            }

            if (LuceeServerConfig.isHttpsEnabled(config) && !LuceeServerConfig.isPortAvailable(LuceeServerConfig.getEffectiveHttpsPort(config))) {
                int httpsPort = LuceeServerConfig.getEffectiveHttpsPort(config);
                errorMessage.append("• HTTPS port ").append(httpsPort)
                        .append(" is already in use by another process\n")
                        .append("  Use: lsof -i :").append(httpsPort).append(" (to see what's using the port)\n")
                        .append("  Or change https.port in your lucee.json file (or disable https)\n\n");
            }
            
            throw new IllegalStateException(errorMessage.toString().trim());
        }
        
        // Build port information message
        StringBuilder portInfo = new StringBuilder();
        if (foreground) {
            portInfo.append("Running server '\"").append(config.name).append("\' in foreground mode:");
        } else {
            portInfo.append("Starting server '\"").append(config.name).append("\' on:");
        }
        portInfo.append("\n  HTTP port:     ").append(config.port);
        portInfo.append("\n  Shutdown port: ").append(LuceeServerConfig.getEffectiveShutdownPort(config));
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            portInfo.append("\n  JMX port:      ").append(config.monitoring.jmx.port);
        }
        if (LuceeServerConfig.isHttpsEnabled(config)) {
            portInfo.append("\n  HTTPS port:    ").append(LuceeServerConfig.getEffectiveHttpsPort(config));
            portInfo.append("\n  HTTPS redirect:").append(LuceeServerConfig.isHttpsRedirectEnabled(config) ? " enabled" : " disabled");
        }
        if (foreground) {
            portInfo.append("\n\nPress Ctrl+C to stop the server\n");
        }
        
        System.out.println(portInfo.toString());
        
        // Create server instance directory
        Path serverInstanceDir = serversDir.resolve(config.name);
        Files.createDirectories(serverInstanceDir);
        
        // Generate Tomcat configuration
        TomcatConfigGenerator configGenerator = new TomcatConfigGenerator();
        // When forceReplace is true (e.g. --force), also allow overwriting project-level
        // WEB-INF config files (web.xml, urlrewrite.xml, UrlRewriteFilter JAR). Without
        // --force, we preserve any existing project configuration files.
        boolean overwriteProjectConfig = forceReplace;
        configGenerator.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir, overwriteProjectConfig);

        // Write CFConfig (.CFConfig.json) into the Lucee context if configured in lucee.json.
        // This treats lucee.json as the source of truth and .CFConfig.json as a derived file.
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, serverInstanceDir);
        
        // Deploy extension dependencies to lucee-server/deploy folder (always deploy)
        deployExtensionsForServer(projectDir, serverInstanceDir);
        
        // Launch the server process
        ServerInstance instance = launchServerProcess(serverInstanceDir, config, projectDir, luceeExpressDir, agentOverrides, environment, foreground);
        
        // For background mode only: wait for startup and open browser
        if (!foreground) {
            // Wait for server to start
            waitForServerStartup(instance, 30); // 30 second timeout
            
            // Open the browser if enabled
            openBrowserForServer(instance, config);
        }
        
        return instance;
    }
    
    /**
     * Stop a server for the given project directory
     */
    public boolean stopServer(Path projectDir) throws IOException {
        ServerInstance instance = getRunningServer(projectDir);
        if (instance == null) {
            return false; // Server not running
        }
        
        return stopServer(instance);
    }
    
    /**
     * Stop a specific server instance
     */
    public boolean stopServer(ServerInstance instance) throws IOException {
        if (instance.getPid() <= 0) {
            return false;
        }
        
        try {
            // Try graceful shutdown first
            ProcessHandle processHandle = ProcessHandle.of(instance.getPid()).orElse(null);
            if (processHandle != null && processHandle.isAlive()) {
                processHandle.destroy();
                
                // Wait for graceful shutdown
                boolean terminated = processHandle.onExit().orTimeout(10, TimeUnit.SECONDS)
                    .handle((ph, ex) -> !ph.isAlive()).join();
                
                if (!terminated && processHandle.isAlive()) {
                    // Force kill if graceful shutdown fails
                    processHandle.destroyForcibly();
                }
                
                // Remove PID file
                Files.deleteIfExists(instance.getServerDir().resolve("server.pid"));
                
                return true;
            }
        } catch (Exception e) {
            // Fallback: remove stale PID file
            Files.deleteIfExists(instance.getServerDir().resolve("server.pid"));
        }
        
        return false;
    }
    
    /**
     * Get the status of a server for the given project directory
     */
    public ServerStatus getServerStatus(Path projectDir) throws IOException {
        ServerInstance instance = getRunningServer(projectDir);
        if (instance == null) {
            return new ServerStatus(false, null, -1, -1, null);
        }
        
        boolean isRunning = isProcessRunning(instance.getPid());
        return new ServerStatus(isRunning, instance.getServerName(), instance.getPid(), 
                              instance.getPort(), instance.getServerDir());
    }
    
    /**
     * List all server instances (running and stopped)
     */
    public List<ServerInfo> listServers() throws IOException {
        List<ServerInfo> servers = new ArrayList<>();
        
        if (!Files.exists(serversDir)) {
            return servers;
        }
        
        try (var stream = Files.list(serversDir)) {
            for (Path serverDir : stream.filter(Files::isDirectory).toList()) {
                String serverName = serverDir.getFileName().toString();
                
                // Try to read PID file
                Path pidFile = serverDir.resolve("server.pid");
                long pid = -1;
                int port = -1;
                boolean isRunning = false;
                
                if (Files.exists(pidFile)) {
                    try {
                        String pidContent = Files.readString(pidFile).trim();
                        String[] parts = pidContent.split(":");
                        if (parts.length >= 2) {
                            pid = Long.parseLong(parts[0]);
                            port = Integer.parseInt(parts[1]);
                            isRunning = isProcessRunning(pid);
                        }
                    } catch (Exception e) {
                        // Invalid PID file, server is not running
                    }
                }
                
                // Try to read project directory from .project-path marker file
                Path projectPathFile = serverDir.resolve(".project-path");
                Path projectDir = null;
                if (Files.exists(projectPathFile)) {
                    try {
                        String projectPathStr = Files.readString(projectPathFile).trim();
                        projectDir = Paths.get(projectPathStr);
                    } catch (Exception e) {
                        // Ignore invalid project path file
                    }
                }
                
                // Read environment if present
                String environment = readEnvironment(serverDir);
                
                servers.add(new ServerInfo(serverName, pid, port, isRunning, serverDir, projectDir, environment));
            }
        }
        
        return servers;
    }
    
    /**
     * Get running server instance for a project directory
     */
    public ServerInstance getRunningServer(Path projectDir) throws IOException {
        try {
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            Path serverDir = serversDir.resolve(config.name);
            Path pidFile = serverDir.resolve("server.pid");
            
            if (!Files.exists(pidFile)) {
                return null;
            }
            
            String pidContent = Files.readString(pidFile).trim();
            String[] parts = pidContent.split(":");
            if (parts.length < 2) {
                return null;
            }
            
            long pid = Long.parseLong(parts[0]);
            int port = Integer.parseInt(parts[1]);
            
            if (!isProcessRunning(pid)) {
                // Remove stale PID file
                Files.deleteIfExists(pidFile);
                return null;
            }
            
            return new ServerInstance(config.name, pid, port, serverDir, projectDir);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Read environment name from .environment file in server directory
     */
    private String readEnvironment(Path serverDir) {
        Path envFile = serverDir.resolve(".environment");
        if (Files.exists(envFile)) {
            try {
                return Files.readString(envFile).trim();
            } catch (Exception e) {
                // Ignore invalid environment file
            }
        }
        return null;
    }
    
    /**
     * Check if a process is running
     */
    private boolean isProcessRunning(long pid) {
        if (pid <= 0) {
            return false;
        }
        
        try {
            ProcessHandle processHandle = ProcessHandle.of(pid).orElse(null);
            return processHandle != null && processHandle.isAlive();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a server with given name is currently running
     */
    private boolean isServerRunning(String serverName) {
        Path serverDir = serversDir.resolve(serverName);
        Path pidFile = serverDir.resolve("server.pid");
        
        if (!Files.exists(pidFile)) {
            return false;
        }
        
        try {
            String pidContent = Files.readString(pidFile).trim();
            String[] parts = pidContent.split(":");
            if (parts.length >= 1) {
                long pid = Long.parseLong(parts[0]);
                return isProcessRunning(pid);
            }
        } catch (Exception e) {
            // Invalid PID file
        }
        
        return false;
    }
    
    /**
     * Check if an existing server directory was created for the given project directory
     * Returns the server information if found and matches, null otherwise
     */
    private ServerInfo getExistingServerForProject(Path projectDir, String serverName) throws IOException {
        Path serverDir = serversDir.resolve(serverName);
        
        if (!Files.exists(serverDir)) {
            return null;
        }
        
        // Check if there's a project path marker file or if we can determine the original project path
        Path projectPathFile = serverDir.resolve(".project-path");
        if (Files.exists(projectPathFile)) {
            try {
                String storedProjectPath = Files.readString(projectPathFile).trim();
                Path storedPath = Paths.get(storedProjectPath).normalize();
                Path currentPath = projectDir.normalize();
                if (storedPath.equals(currentPath)) {
                    // This server was created for the current project directory
                    Path pidFile = serverDir.resolve("server.pid");
                    long pid = -1;
                    int port = -1;
                    boolean isRunning = false;
                    
                    if (Files.exists(pidFile)) {
                        try {
                            String pidContent = Files.readString(pidFile).trim();
                            String[] parts = pidContent.split(":");
                            if (parts.length >= 2) {
                                pid = Long.parseLong(parts[0]);
                                port = Integer.parseInt(parts[1]);
                                isRunning = isProcessRunning(pid);
                            }
                        } catch (Exception e) {
                            // Invalid PID file
                        }
                    }
                    
                    // Read environment if present
                    String environment = readEnvironment(serverDir);
                    
                    return new ServerInfo(serverName, pid, port, isRunning, serverDir, storedPath, environment);
                }
            } catch (Exception e) {
                // Ignore invalid project path file
            }
        }
        
        return null;
    }
    
    /**
     * Get server information by server name
     */
    public ServerInfo getServerInfoByName(String serverName) throws IOException {
        if (!Files.exists(serversDir)) {
            return null;
        }
        
        Path serverDir = serversDir.resolve(serverName);
        if (!Files.exists(serverDir)) {
            return null;
        }
        
        // Try to read PID file
        Path pidFile = serverDir.resolve("server.pid");
        long pid = -1;
        int port = -1;
        boolean isRunning = false;
        
        if (Files.exists(pidFile)) {
            try {
                String pidContent = Files.readString(pidFile).trim();
                String[] parts = pidContent.split(":");
                if (parts.length >= 2) {
                    pid = Long.parseLong(parts[0]);
                    port = Integer.parseInt(parts[1]);
                    isRunning = isProcessRunning(pid);
                }
            } catch (Exception e) {
                // Invalid PID file, server is not running
            }
        }
        
        // Try to read project directory from .project-path marker file
        Path projectPathFile = serverDir.resolve(".project-path");
        Path projectDir = null;
        if (Files.exists(projectPathFile)) {
            try {
                String projectPathStr = Files.readString(projectPathFile).trim();
                projectDir = Paths.get(projectPathStr);
            } catch (Exception e) {
                // Ignore invalid project path file
            }
        }
        
        // Read environment if present
        String environment = readEnvironment(serverDir);
        
        return new ServerInfo(serverName, pid, port, isRunning, serverDir, projectDir, environment);
    }
    
    /**
     * Stop a server by name
     */
    public boolean stopServerByName(String serverName) throws IOException {
        ServerInfo serverInfo = getServerInfoByName(serverName);
        if (serverInfo == null) {
            return false; // Server not found
        }
        
        if (!serverInfo.isRunning()) {
            return false; // Server not running
        }
        
        // Create a ServerInstance for the stopServer method
        ServerInstance instance = new ServerInstance(serverInfo.getServerName(), 
                                                   serverInfo.getPid(), 
                                                   serverInfo.getPort(), 
                                                   serverInfo.getServerDir(), 
                                                   serverInfo.getProjectDir());
        
        return stopServer(instance);
    }
    
    /**
     * Prune (remove) the server for the current project directory
     * Only removes stopped servers
     */
    public PruneResult pruneServer(Path projectDir) throws IOException {
        ServerInstance instance = getRunningServer(projectDir);
        if (instance != null) {
            // Server is still running, cannot prune
            return new PruneResult(instance.getServerName(), false, "Server is still running");
        }
        
        // Load config to get server name
        try {
            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            Path serverDir = serversDir.resolve(config.name);
            
            if (!Files.exists(serverDir)) {
                return new PruneResult(config.name, false, "Server directory not found");
            }
            
            // Delete server directory
            deleteServerDirectory(serverDir);
            return new PruneResult(config.name, true, "Server pruned successfully");
            
        } catch (Exception e) {
            return new PruneResult("unknown", false, "Failed to load server config: " + e.getMessage());
        }
    }
    
    /**
     * Prune (remove) a specific server by name
     * Only removes stopped servers
     */
    public PruneResult pruneServerByName(String serverName) throws IOException {
        ServerInfo serverInfo = getServerInfoByName(serverName);
        
        if (serverInfo == null) {
            return new PruneResult(serverName, false, "Server not found");
        }
        
        if (serverInfo.isRunning()) {
            return new PruneResult(serverName, false, "Server is still running");
        }
        
        // Delete server directory
        deleteServerDirectory(serverInfo.getServerDir());
        return new PruneResult(serverName, true, "Server pruned successfully");
    }
    
    /**
     * Prune (remove) all stopped servers
     * Returns a summary of what was pruned and what wasn't
     */
    public PruneAllResult pruneAllStoppedServers() throws IOException {
        List<ServerInfo> servers = listServers();
        List<PruneResult> pruned = new ArrayList<>();
        List<PruneResult> skipped = new ArrayList<>();
        
        for (ServerInfo server : servers) {
            if (server.isRunning()) {
                skipped.add(new PruneResult(server.getServerName(), false, "Server is running"));
            } else {
                try {
                    deleteServerDirectory(server.getServerDir());
                    pruned.add(new PruneResult(server.getServerName(), true, "Pruned successfully"));
                } catch (Exception e) {
                    skipped.add(new PruneResult(server.getServerName(), false, "Failed to delete: " + e.getMessage()));
                }
            }
        }
        
        return new PruneAllResult(pruned, skipped);
    }
    
    /**
     * Check if any of our managed servers is using a specific port
     * Returns the server name if found, null if no managed server is using the port
     */
    public String getServerUsingPort(int port) throws IOException {
        if (!Files.exists(serversDir)) {
            return null;
        }
        
        try (var stream = Files.list(serversDir)) {
            for (Path serverDir : stream.filter(Files::isDirectory).toList()) {
                Path pidFile = serverDir.resolve("server.pid");
                
                if (Files.exists(pidFile)) {
                    try {
                        String pidContent = Files.readString(pidFile).trim();
                        String[] parts = pidContent.split(":");
                        if (parts.length >= 2) {
                            long pid = Long.parseLong(parts[0]);
                            int serverPort = Integer.parseInt(parts[1]);
                            
                            // Check if this server is using the requested port and is still running
                            if ((serverPort == port || LuceeServerConfig.getShutdownPort(serverPort) == port) 
                                && isProcessRunning(pid)) {
                                return serverDir.getFileName().toString();
                            }
                        }
                    } catch (Exception e) {
                        // Invalid PID file, skip
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Delete a server directory and all its contents
     */
    private void deleteServerDirectory(Path serverDir) throws IOException {
        if (!Files.exists(serverDir)) {
            return;
        }
        
        // Walk the file tree and delete everything
        Files.walk(serverDir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }
    

    /**
     * Public helper to open the browser for the currently running server
     * associated with the given project directory.
     *
     * Returns true if a running server was found (and a browser attempt was made),
     * false if no running server exists for the project.
     */
    public boolean openBrowser(Path projectDir) throws IOException {
        return openBrowser(projectDir, null);
    }

    /**
     * Public helper to open the browser for a running server, either:
     *  - by explicit server name (if serverName is non-null), or
     *  - by the current project directory (fallback).
     *
     * Returns true if a running server was found and a browser attempt was made.
     */
    public boolean openBrowser(Path projectDir, String serverName) throws IOException {
        ServerInstance instance;
        LuceeServerConfig.ServerConfig config;

        try {
            if (serverName != null && !serverName.trim().isEmpty()) {
                // Look up by name
                ServerInfo info = getServerInfoByName(serverName.trim());
                if (info == null || !info.isRunning()) {
                    return false;
                }

                Path effectiveProjectDir = info.getProjectDir() != null
                        ? info.getProjectDir()
                        : projectDir;

                config = LuceeServerConfig.loadConfig(effectiveProjectDir);
                instance = new ServerInstance(
                        info.getServerName(),
                        info.getPid(),
                        info.getPort(),
                        info.getServerDir(),
                        effectiveProjectDir
                );
            } else {
                // Default: running server for this project directory
                instance = getRunningServer(projectDir);
                if (instance == null) {
                    return false;
                }
                config = LuceeServerConfig.loadConfig(projectDir);
            }

            openBrowserForServer(instance, config);
            return true;
        } catch (Exception e) {
            System.err.println("Could not open browser for running server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open the configured browser URL (or default) for a started server.
     * This is best-effort and should not cause server startup to fail.
     */
    private void openBrowserForServer(ServerInstance instance, LuceeServerConfig.ServerConfig config) {
        if (config == null || !config.openBrowser) {
            return;
        }

        String url = config.openBrowserURL;
        if (url == null || url.trim().isEmpty()) {
            String host = (config.host == null || config.host.trim().isEmpty()) ? "localhost" : config.host.trim();
            if (LuceeServerConfig.isHttpsEnabled(config)) {
                url = "https://" + host + ":" + LuceeServerConfig.getEffectiveHttpsPort(config) + "/";
            } else {
                url = "http://" + host + ":" + instance.getPort() + "/";
            }
        }

        try {
            // Prefer Desktop API when available and not headless
            if (!GraphicsEnvironment.isHeadless()
                    && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }

            // Fallback: small OS-specific commands
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "\"\"", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            System.err.println("Could not open browser for URL " + url + ": " + e.getMessage());
        }
    }



    /**
     * Ensure Lucee Express for the specified version is available
     */
    public Path ensureLuceeExpress(String version) throws Exception {
        Path versionDir = expressDir.resolve(version);
        
        if (Files.exists(versionDir)) {
            // Already downloaded
            return versionDir;
        }
        
        // Download and extract Lucee Express
        String downloadUrl = LUCEE_CDN_URL_TEMPLATE.replace("{version}", version);
        Path zipFile = expressDir.resolve("lucee-express-" + version + ".zip");
        
        System.out.println("Downloading Lucee Express " + version + "...");
        downloadFile(downloadUrl, zipFile);
        
        System.out.println("Extracting Lucee Express...");
        extractZipFile(zipFile, versionDir);
        
        // Set execute permissions on shell scripts
        setExecutePermissions(versionDir);
        
        // Clean up zip file
        Files.deleteIfExists(zipFile);
        
        return versionDir;
    }
    
    /**
     * Download a file from URL with progress bar
     */
    private void downloadFile(String urlString, Path destinationFile) throws IOException {
        Files.createDirectories(destinationFile.getParent());
        
        URL url = new URL(urlString);
        try {
            var connection = url.openConnection();
            long contentLength = connection.getContentLengthLong();
            
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(destinationFile)) {
                
                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                int bytesRead;
                long lastProgressUpdate = 0;
                
                // Show initial progress
                if (contentLength > 0) {
                    System.out.print("\r[" + " ".repeat(50) + "] 0% (0 MB / " + formatMB(contentLength) + " MB)");
                    System.out.flush();
                }
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Update progress every 100ms or when download completes
                    long currentTime = System.currentTimeMillis();
                    if (contentLength > 0 && (currentTime - lastProgressUpdate > 100 || bytesRead < buffer.length)) {
                        double progress = (double) totalBytesRead / contentLength;
                        int progressChars = (int) (progress * 50);
                        
                        String progressBar = "█".repeat(progressChars) + " ".repeat(50 - progressChars);
                        String percentage = String.format("%.1f", progress * 100);
                        
                        System.out.print("\r[" + progressBar + "] " + percentage + "% (" + 
                                       formatMB(totalBytesRead) + " MB / " + formatMB(contentLength) + " MB)");
                        System.out.flush();
                        lastProgressUpdate = currentTime;
                    }
                }
                
                // Show completion
                if (contentLength > 0) {
                    System.out.println("\r[" + "█".repeat(50) + "] 100.0% (" + 
                                     formatMB(totalBytesRead) + " MB / " + formatMB(contentLength) + " MB) - Download complete!");
                } else {
                    // If content length was unknown, just show total downloaded
                    System.out.println("Download complete! (" + formatMB(totalBytesRead) + " MB)");
                }
            }
        } catch (IOException e) {
            // Fallback to simple download if progress fails
            System.out.println("\nProgress display failed, continuing with simple download...");
            try (InputStream in = url.openStream();
                 OutputStream out = Files.newOutputStream(destinationFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
    
    /**
     * Format bytes as MB with 1 decimal place
     */
    private String formatMB(long bytes) {
        return String.format("%.1f", bytes / 1024.0 / 1024.0);
    }
    
    /**
     * Extract a ZIP file
     */
    private void extractZipFile(Path zipFile, Path destinationDir) throws IOException {
        Files.createDirectories(destinationDir);
        
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            
            while ((entry = zip.getNextEntry()) != null) {
                Path entryPath = destinationDir.resolve(entry.getName());
                
                // Security check: ensure path is within destination directory
                if (!entryPath.normalize().startsWith(destinationDir.normalize())) {
                    throw new IOException("ZIP entry is outside of destination directory: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zip.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }
    
    /**
     * Set execute permissions on shell scripts in the Lucee Express directory
     */
    private void setExecutePermissions(Path luceeExpressDir) throws IOException {
        // Set execute permissions on startup and shutdown scripts
        Path startupScript = luceeExpressDir.resolve("startup.sh");
        Path shutdownScript = luceeExpressDir.resolve("shutdown.sh");
        
        if (Files.exists(startupScript)) {
            startupScript.toFile().setExecutable(true);
        }
        
        if (Files.exists(shutdownScript)) {
            shutdownScript.toFile().setExecutable(true);
        }
        
        // Set execute permissions on bin scripts
        Path binDir = luceeExpressDir.resolve("bin");
        if (Files.exists(binDir)) {
            try (var stream = Files.list(binDir)) {
                stream.filter(p -> p.toString().endsWith(".sh"))
                      .forEach(script -> script.toFile().setExecutable(true));
            }
        }
    }
    
    /**
     * Deploy extension dependencies (.lex files) to server's lucee-server/deploy folder.
     * Called before server startup.
     */
    private void deployExtensionsForServer(Path projectDir, Path serverInstanceDir) {
        try {
            // Load lock file to get installed extension dependencies
            org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(projectDir);
            
            java.util.List<org.lucee.lucli.deps.LockedDependency> allExtensions = new java.util.ArrayList<>();
            
            // Collect extension dependencies from both prod and dev
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDependencies().values()) {
                if ("extension".equals(dep.getType())) {
                    allExtensions.add(dep);
                }
            }
            
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDevDependencies().values()) {
                if ("extension".equals(dep.getType())) {
                    allExtensions.add(dep);
                }
            }
            
            if (!allExtensions.isEmpty()) {
                // Deploy extensions that have URL or path
                ExtensionDependencyInstaller.deployExtensions(allExtensions, serverInstanceDir);
            }
        } catch (Exception e) {
            // Log but don't fail server startup if extension deployment fails
            System.err.println("Warning: Failed to deploy extensions: " + e.getMessage());
        }
    }
    
    /**
     * Build LUCEE_EXTENSIONS environment variable from extension dependencies.
     * Returns comma-separated list of extension IDs.
     */
    private String buildLuceeExtensions(Path projectDir) {
        try {
            // Load lock file to get installed dependencies
            org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(projectDir);
            
            java.util.List<String> extensionIds = new java.util.ArrayList<>();
            
            // Check production dependencies
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDependencies().values()) {
                if ("extension".equals(dep.getType()) && dep.getId() != null && !dep.getId().trim().isEmpty()) {
                    extensionIds.add(dep.getId().trim());
                }
            }
            
            // Check dev dependencies
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDevDependencies().values()) {
                if ("extension".equals(dep.getType()) && dep.getId() != null && !dep.getId().trim().isEmpty()) {
                    extensionIds.add(dep.getId().trim());
                }
            }
            
            return String.join(",", extensionIds);
        } catch (Exception e) {
            // If we can't read lock file or it doesn't exist, return empty string
            return "";
        }
    }
    
    /**
     * Launch the server process using either startup script (background) or catalina run (foreground).
     * 
     * @param serverInstanceDir The server instance directory
     * @param config Server configuration
     * @param projectDir Project directory
     * @param luceeExpressDir Lucee Express directory
     * @param agentOverrides Agent overrides
     * @param environment Environment name
     * @param foreground If true, uses 'catalina run' and blocks; if false, uses 'startup' script and returns immediately
     * @return ServerInstance (only when foreground=false; foreground=true blocks until shutdown)
     */
    private ServerInstance launchServerProcess(Path serverInstanceDir, LuceeServerConfig.ServerConfig config, 
                                             Path projectDir, Path luceeExpressDir,
                                             AgentOverrides agentOverrides, String environment, boolean foreground) throws Exception {
        
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        
        // Build command based on foreground mode
        List<String> command = new ArrayList<>();
        Path scriptPath;
        
        if (foreground) {
            // Use catalina.sh run for foreground mode
            String scriptName = isWindows ? "catalina.bat" : "catalina.sh";
            scriptPath = luceeExpressDir.resolve("bin").resolve(scriptName);
            if (!Files.exists(scriptPath) || (!isWindows && !Files.isExecutable(scriptPath))) {
                throw new Exception("Lucee Express catalina script not found or not executable: " + scriptPath);
            }
            command.add(scriptPath.toString());
            command.add("run");
        } else {
            // Use startup.sh for background mode
            String scriptName = isWindows ? "startup.bat" : "startup.sh";
            scriptPath = luceeExpressDir.resolve(scriptName);
            if (!Files.exists(scriptPath) || (!isWindows && !Files.isExecutable(scriptPath))) {
                throw new Exception("Lucee Express startup script not found or not executable: " + scriptPath);
            }
            command.add(scriptPath.toString());
        }
        
        // Create process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(luceeExpressDir.toFile());
        
        if (foreground) {
            // Inherit I/O streams for foreground mode
            pb.inheritIO();
        } else {
            // Redirect to log files for background mode
            pb.redirectOutput(serverInstanceDir.resolve("logs/server.out").toFile());
            pb.redirectError(serverInstanceDir.resolve("logs/server.err").toFile());
        }
        
        // Set environment variables for Tomcat configuration
        Map<String, String> env = pb.environment();
        env.put("CATALINA_BASE", serverInstanceDir.toString());
        env.put("CATALINA_HOME", serverInstanceDir.toString());
        
        // Set JVM options through CATALINA_OPTS
        List<String> catalinaOpts = buildCatalinaOpts(config, agentOverrides, projectDir);
        env.put("CATALINA_OPTS", String.join(" ", catalinaOpts));
        
        // Set other Tomcat environment variables
        Path pidFile = serverInstanceDir.resolve("server.pid");
        env.put("CATALINA_PID", pidFile.toString());
        env.put("CATALINA_OUT", serverInstanceDir.resolve("logs/catalina.out").toString());

        if(config.admin.password != null && config.admin.password.length() > 0){
            env.put("LUCEE_ADMIN_PASSWORD", config.admin.password);
        }
        
        // Set LUCEE_EXTENSIONS environment variable if extensions are configured
        String luceeExtensions = buildLuceeExtensions(projectDir);
        if (luceeExtensions != null && !luceeExtensions.isEmpty()) {
            env.put("LUCEE_EXTENSIONS", luceeExtensions);
        }
        
        // Write project path marker file to track which project this server belongs to
        Files.writeString(serverInstanceDir.resolve(".project-path"), projectDir.toAbsolutePath().toString());
        
        // Write environment marker file if environment was specified
        if (environment != null && !environment.trim().isEmpty()) {
            Files.writeString(serverInstanceDir.resolve(".environment"), environment.trim());
        }
        
        if (foreground) {
            // Add shutdown hook to cleanup PID file on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(pidFile);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }));
        }
        
        // Start the process
        Process process = pb.start();
        
        // Write PID file
        String pidContent = process.pid() + ":" + config.port;
        Files.writeString(pidFile, pidContent);
        
        if (foreground) {
            // Foreground mode: wait for process to complete
            try {
                // Wait for the process to exit (blocks until Ctrl+C or server stops)
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    System.err.println("\nServer exited with code: " + exitCode);
                } else {
                    System.out.println("\nServer stopped successfully.");
                }
            } catch (InterruptedException e) {
                // Handle interruption (Ctrl+C)
                System.out.println("\nShutting down server...");
                process.destroy();
                
                // Wait a bit for graceful shutdown
                try {
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        // Force kill if not shut down within 10 seconds
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ex) {
                    process.destroyForcibly();
                }
            } finally {
                // Cleanup PID file
                Files.deleteIfExists(pidFile);
            }
            return null; // Foreground mode doesn't return an instance since it blocks
        } else {
            // Background mode: return immediately with instance
            return new ServerInstance(config.name, process.pid(), config.port, serverInstanceDir, projectDir);
        }
    }
    
    /**
     * Find Java executable
     */
    private String findJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path javaExe = Paths.get(javaHome, "bin", "java");
            if (Files.exists(javaExe)) {
                return javaExe.toString();
            }
        }
        return "java"; // Fallback to PATH
    }
    
    /**
     * Build JVM options (CATALINA_OPTS) including memory, JMX, Lucee extensions, and any configured agents.
     *
     * Package-private so it can be reused by other server components and
     * exercised directly from tests.
     */
    List<String> buildCatalinaOpts(LuceeServerConfig.ServerConfig config, AgentOverrides overrides, Path projectDir) {
        List<String> opts = new ArrayList<>();
        
        // Base memory settings
        opts.add("-Xms" + config.jvm.minMemory);
        opts.add("-Xmx" + config.jvm.maxMemory);
        
        // JMX configuration if monitoring is enabled
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            opts.add("-Dcom.sun.management.jmxremote");
            opts.add("-Dcom.sun.management.jmxremote.port=" + config.monitoring.jmx.port);
            opts.add("-Dcom.sun.management.jmxremote.authenticate=false");
            opts.add("-Dcom.sun.management.jmxremote.ssl=false");
        }
        
        // Lucee extensions configuration - set as JVM property so it persists across restarts
        String luceeExtensions = buildLuceeExtensions(projectDir);
        if (luceeExtensions != null && !luceeExtensions.isEmpty()) {
            opts.add("-Dlucee.extensions=" + luceeExtensions);
        }
        
        // Determine which agents are active for this startup
        Set<String> activeAgents = resolveActiveAgents(config, overrides);
        if (activeAgents != null && !activeAgents.isEmpty() && config.agents != null) {
            for (String agentId : activeAgents) {
                LuceeServerConfig.AgentConfig agentConfig = config.agents.get(agentId);
                if (agentConfig != null && agentConfig.jvmArgs != null) {
                    opts.addAll(Arrays.asList(agentConfig.jvmArgs));
                }
            }
        }
        
        // Finally append any additional raw JVM args from config
        if (config.jvm.additionalArgs != null) {
            opts.addAll(Arrays.asList(config.jvm.additionalArgs));
        }
        
        return opts;
    }
    
    /**
     * Compute the set of active agent IDs based on config and optional overrides.
     */
    private Set<String> resolveActiveAgents(LuceeServerConfig.ServerConfig config, AgentOverrides overrides) {
        if (config.agents == null || config.agents.isEmpty()) {
            return Set.of();
        }
        
        // If no overrides are provided, use agents enabled in config
        if (overrides == null) {
            Set<String> enabled = new HashSet<>();
            for (Map.Entry<String, LuceeServerConfig.AgentConfig> entry : config.agents.entrySet()) {
                if (entry.getValue() != null && entry.getValue().enabled) {
                    enabled.add(entry.getKey());
                }
            }
            return enabled;
        }
        
        // If includeAgents is specified, that list defines the active set
        if (overrides.includeAgents != null && !overrides.includeAgents.isEmpty()) {
            Set<String> result = new HashSet<>();
            for (String id : overrides.includeAgents) {
                if (config.agents.containsKey(id)) {
                    result.add(id);
                }
            }
            return result;
        }
        
        // Start from agents enabled in config
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, LuceeServerConfig.AgentConfig> entry : config.agents.entrySet()) {
            if (entry.getValue() != null && entry.getValue().enabled) {
                active.add(entry.getKey());
            }
        }
        
        // Apply disable-all override
        if (overrides.disableAllAgents) {
            active.clear();
        }
        
        // Apply per-agent enables
        if (overrides.enableAgents != null) {
            for (String id : overrides.enableAgents) {
                if (config.agents.containsKey(id)) {
                    active.add(id);
                }
            }
        }
        
        // Apply per-agent disables
        if (overrides.disableAgents != null) {
            active.removeAll(overrides.disableAgents);
        }
        
        return active;
    }
    
    /**
     * Public helper for callers that need to know which agents are active
     * for a given config and overrides combination (e.g. for CLI output).
     */
    public Set<String> getActiveAgentsForConfig(LuceeServerConfig.ServerConfig config, AgentOverrides overrides) {
        return resolveActiveAgents(config, overrides);
    }
    
    /**
     * Build classpath for Tomcat
     */
    private String buildClasspath(Path serverInstanceDir) {
        // Include bootstrap.jar and tomcat-juli.jar from bin directory, plus all lib JARs
        String separator = System.getProperty("path.separator");
        return serverInstanceDir.resolve("bin/bootstrap.jar").toString() + separator +
               serverInstanceDir.resolve("bin/tomcat-juli.jar").toString() + separator +
               serverInstanceDir.resolve("lib/*").toString();
    }
    
    /**
     * Wait for server to start up
     */
    private void waitForServerStartup(ServerInstance instance, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (LuceeServerConfig.isPortAvailable(instance.getPort())) {
                Thread.sleep(1000); // Port still available, server not started yet
            } else {
                // Port is bound, server likely started
                System.out.println("Server started successfully on port " + instance.getPort());
                return;
            }
        }
        
        throw new Exception("Server startup timed out after " + timeoutSeconds + " seconds");
    }
    
    /**
     * Server instance information
     */
    public static class ServerInstance {
        private final String serverName;
        private final long pid;
        private final int port;
        private final Path serverDir;
        private final Path projectDir;
        
        public ServerInstance(String serverName, long pid, int port, Path serverDir, Path projectDir) {
            this.serverName = serverName;
            this.pid = pid;
            this.port = port;
            this.serverDir = serverDir;
            this.projectDir = projectDir;
        }
        
        public String getServerName() { return serverName; }
        public long getPid() { return pid; }
        public int getPort() { return port; }
        public Path getServerDir() { return serverDir; }
        public Path getProjectDir() { return projectDir; }
    }
    
    /**
     * Server status information
     */
    public static class ServerStatus {
        private final boolean running;
        private final String serverName;
        private final long pid;
        private final int port;
        private final Path serverDir;
        
        public ServerStatus(boolean running, String serverName, long pid, int port, Path serverDir) {
            this.running = running;
            this.serverName = serverName;
            this.pid = pid;
            this.port = port;
            this.serverDir = serverDir;
        }
        
        public boolean isRunning() { return running; }
        public String getServerName() { return serverName; }
        public long getPid() { return pid; }
        public int getPort() { return port; }
        public Path getServerDir() { return serverDir; }
    }
    
    /**
     * Server information for listing
     */
    public static class ServerInfo {
        private final String serverName;
        private final long pid;
        private final int port;
        private final boolean running;
        private final Path serverDir;
        private final Path projectDir;
        private final String environment;
        
        public ServerInfo(String serverName, long pid, int port, boolean running, Path serverDir, Path projectDir, String environment) {
            this.serverName = serverName;
            this.pid = pid;
            this.port = port;
            this.running = running;
            this.serverDir = serverDir;
            this.projectDir = projectDir;
            this.environment = environment;
        }
        
        public String getServerName() { return serverName; }
        public long getPid() { return pid; }
        public int getPort() { return port; }
        public boolean isRunning() { return running; }
        public Path getServerDir() { return serverDir; }
        public Path getProjectDir() { return projectDir; }
        public String getEnvironment() { return environment; }
    }
    
    /**
     * Result of a server prune operation
     */
    public static class PruneResult {
        private final String serverName;
        private final boolean success;
        private final String message;
        
        public PruneResult(String serverName, boolean success, String message) {
            this.serverName = serverName;
            this.success = success;
            this.message = message;
        }
        
        public String getServerName() { return serverName; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    /**
     * Result of a prune all operation
     */
    public static class PruneAllResult {
        private final List<PruneResult> pruned;
        private final List<PruneResult> skipped;
        
        public PruneAllResult(List<PruneResult> pruned, List<PruneResult> skipped) {
            this.pruned = pruned;
            this.skipped = skipped;
        }
        
        public List<PruneResult> getPruned() { return pruned; }
        public List<PruneResult> getSkipped() { return skipped; }
    }
}
