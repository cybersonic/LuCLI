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

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.deps.ExtensionDependencyInstaller;
import org.lucee.lucli.server.runtime.LuceeExpressRuntimeProvider;
import org.lucee.lucli.server.runtime.RuntimeProvider;
import org.lucee.lucli.server.runtime.TomcatRuntimeProvider;
import org.lucee.lucli.server.runtime.DockerRuntimeProvider;
import org.lucee.lucli.server.runtime.JettyRuntimeProvider;

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

    // Lucee engine JAR variants
    private static final String LUCEE_JAR_STANDARD_TEMPLATE = "https://cdn.lucee.org/lucee-{version}.jar";
    private static final String LUCEE_JAR_LIGHT_TEMPLATE = "https://cdn.lucee.org/lucee-light-{version}.jar";
    private static final String LUCEE_JAR_ZERO_TEMPLATE = "https://cdn.lucee.org/lucee-zero-{version}.jar";

    private final Path lucliHome;
    private final Path expressDir;
    private final Path serversDir;
    private final Path jarsDir;

    /**
     * Registered runtime providers keyed by their runtime type strings
     * (e.g. "lucee-express", "tomcat"). Providers are per-manager so they
     * can reuse this manager's helper methods and directories.
     */
    private final java.util.Map<String, RuntimeProvider> runtimeProviders = new java.util.HashMap<>();

    /**
     * Default provider used when runtime.type is omitted or unknown.
     * Currently this is always the Lucee Express provider.
     */
    private final RuntimeProvider defaultRuntimeProvider;
    
    public LuceeServerManager() throws IOException {
        this.lucliHome = getLucliHome();
        this.expressDir = lucliHome.resolve("express");
        this.serversDir = lucliHome.resolve("servers");
        this.jarsDir = lucliHome.resolve("jars");
        
        // Ensure directories exist
        Files.createDirectories(expressDir);
        Files.createDirectories(serversDir);
        Files.createDirectories(jarsDir);

        // Register built-in runtime providers.
        RuntimeProvider express = new LuceeExpressRuntimeProvider();
        this.defaultRuntimeProvider = express;
        registerRuntimeProvider(express);
        registerRuntimeProvider(new TomcatRuntimeProvider());
        registerRuntimeProvider(new DockerRuntimeProvider());
        registerRuntimeProvider(new JettyRuntimeProvider());
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


    private void registerRuntimeProvider(RuntimeProvider provider) {
        if (provider == null || provider.getType() == null) {
            return;
        }
        runtimeProviders.put(provider.getType(), provider);
    }

    private RuntimeProvider getRuntimeProvider(String type) {
        if (type == null || type.trim().isEmpty()) {
            return defaultRuntimeProvider;
        }
        RuntimeProvider provider = runtimeProviders.get(type);
        return provider != null ? provider : defaultRuntimeProvider;
    }

    /**
     * Run a transient "sandbox" server in foreground mode without writing lucee.json
     * in the project directory or persisting the server instance after shutdown.
     *
     * @param projectDir The project directory (used for webroot and dependency resolution)
     * @param versionOverride Optional Lucee version override (can be null)
     * @param forceReplace Whether to allow overwriting an existing sandbox server directory
     * @param customName Optional custom server name (can be null); when null a unique sandbox name is generated
     * @param agentOverrides Optional agent overrides (can be null)
     * @param environment Optional environment name (currently only persisted as metadata)
     * @param webrootOverride Optional webroot override for this sandbox run
     * @param portOverride Optional HTTP port override for this sandbox run
     * @param enableLuceeOverride Optional one-shot Lucee enable/disable override for this sandbox run
     */
    public void runServerForegroundSandbox(Path projectDir,
                                           String versionOverride,
                                           boolean forceReplace,
                                           String customName,
                                           AgentOverrides agentOverrides,
                                           String environment,
                                           String webrootOverride,
                                           Integer portOverride,
                                           Boolean enableLuceeOverride) throws Exception {
        // Build an in-memory configuration; this does not create or modify lucee.json
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.createDefaultConfig(projectDir);

        // Apply CLI overrides
        if (versionOverride != null && !versionOverride.trim().isEmpty()) {
            config.version = versionOverride.trim();
        }

        if (customName != null && !customName.trim().isEmpty()) {
            config.name = customName.trim();
        } else {
            // Generate a unique sandbox name based on the project directory
            String baseName = projectDir.getFileName() != null
                    ? projectDir.getFileName().toString()
                    : "sandbox";
            String sandboxBase = baseName + "-sandbox";
            config.name = LuceeServerConfig.getUniqueServerName(sandboxBase, serversDir);
        }

        if (webrootOverride != null && !webrootOverride.trim().isEmpty()) {
            config.webroot = webrootOverride.trim();
        }

        if (portOverride != null) {
            config.port = portOverride;
        }

        if (enableLuceeOverride != null) {
            config.enableLucee = enableLuceeOverride.booleanValue();
        }

        // Do not auto-open browser for sandbox foreground runs
        config.openBrowser = false;

        // Ensure Lucee Express is available for the chosen version
        Path luceeExpressDir = ensureLuceeExpress(config.version);

        // Resolve port conflicts just before startup using the same logic as normal servers
        LuceeServerConfig.PortConflictResult portResult = LuceeServerConfig.resolvePortConflicts(config, false, this);
        if (portResult.hasConflicts) {
            throw new IllegalStateException(portResult.message);
        }
        config = portResult.updatedConfig;

        // Log port information for the sandbox server
        StringBuilder portInfo = new StringBuilder();
        portInfo.append("Running sandbox server '\"")
                .append(config.name)
                .append("\" in foreground mode:\n");
        portInfo.append("  HTTP port:     ").append(config.port).append("\n");
        portInfo.append("  Shutdown port: ").append(LuceeServerConfig.getEffectiveShutdownPort(config)).append("\n");
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            portInfo.append("  JMX port:      ").append(config.monitoring.jmx.port).append("\n");
        }
        if (LuceeServerConfig.isHttpsEnabled(config)) {
            portInfo.append("  HTTPS port:    ").append(LuceeServerConfig.getEffectiveHttpsPort(config)).append("\n");
            portInfo.append("  HTTPS redirect:")
                    .append(LuceeServerConfig.isHttpsRedirectEnabled(config) ? " enabled" : " disabled")
                    .append("\n");
        }
        portInfo.append("\nPress Ctrl+C to stop the server\n");
        System.out.println(portInfo.toString());

        // Create sandbox server instance directory under ~/.lucli/servers
        Path serverInstanceDir = serversDir.resolve(config.name);
        if (Files.exists(serverInstanceDir) && forceReplace) {
            deleteServerDirectory(serverInstanceDir);
        }
        Files.createDirectories(serverInstanceDir);

        // Mark this server as sandbox so we can clean it up on stop/prune
        try {
            Files.writeString(serverInstanceDir.resolve(".sandbox"), "sandbox");
        } catch (IOException e) {
            System.err.println("Warning: Failed to write sandbox marker: " + e.getMessage());
        }

        try {
            // Generate CATALINA_BASE configuration from the Express CATALINA_HOME
            org.lucee.lucli.server.runtime.CatalinaBaseConfigGenerator configGenerator =
                    new org.lucee.lucli.server.runtime.CatalinaBaseConfigGenerator();
            configGenerator.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir, 0, forceReplace);

            // Write CFConfig (.CFConfig.json) if present in the in-memory configuration
            LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, serverInstanceDir);

            // Deploy any extension dependencies to the sandbox server
            deployExtensionsForServer(projectDir, serverInstanceDir);

            // Copy welcome index.cfm if Lucee is enabled and no index.cfm exists in webroot
            if (config.enableLucee) {
                copyWelcomeIndexIfMissing(config, projectDir);
            }

            // Launch foreground process; this blocks until shutdown
            launchTomcatProcess(luceeExpressDir, serverInstanceDir, config, projectDir,
                                agentOverrides, environment, true, "lucee-express");
        } finally {
            // After the foreground process exits, clean up the sandbox server directory
            try {
                deleteServerDirectory(serverInstanceDir);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete sandbox server directory "
                        + serverInstanceDir + ": " + e.getMessage());
            }
        }
    }
    
/**
     * Start a transient "sandbox" server in background mode without writing lucee.json
     * in the project directory. The server directory is automatically removed after
     * the server is stopped or pruned.
     */
    public ServerInstance startServerSandbox(Path projectDir,
                                             String versionOverride,
                                             boolean forceReplace,
                                             String customName,
                                             AgentOverrides agentOverrides,
                                             String environment,
                                             String webrootOverride,
                                             Integer portOverride,
                                             Boolean enableLuceeOverride) throws Exception {
        // Build an in-memory configuration; this does not create or modify lucee.json
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.createDefaultConfig(projectDir);

        // Apply CLI overrides
        if (versionOverride != null && !versionOverride.trim().isEmpty()) {
            config.version = versionOverride.trim();
        }

        if (customName != null && !customName.trim().isEmpty()) {
            config.name = customName.trim();
        } else {
            String baseName = projectDir.getFileName() != null
                    ? projectDir.getFileName().toString()
                    : "sandbox";
            String sandboxBase = baseName + "-sandbox";
            config.name = LuceeServerConfig.getUniqueServerName(sandboxBase, serversDir);
        }

        if (webrootOverride != null && !webrootOverride.trim().isEmpty()) {
            config.webroot = webrootOverride.trim();
        }

        if (portOverride != null) {
            config.port = portOverride;
        }

        if (enableLuceeOverride != null) {
            config.enableLucee = enableLuceeOverride.booleanValue();
        }

        // For sandbox background servers, do not auto-open browser by default
        config.openBrowser = false;

        // Ensure Lucee Express is available for the chosen version
        Path luceeExpressDir = ensureLuceeExpress(config.version);

        // Resolve port conflicts just before startup using the same logic as normal servers
        LuceeServerConfig.PortConflictResult portResult = LuceeServerConfig.resolvePortConflicts(config, false, this);
        if (portResult.hasConflicts) {
            throw new IllegalStateException(portResult.message);
        }
        config = portResult.updatedConfig;

        // Create sandbox server instance directory under ~/.lucli/servers
        Path serverInstanceDir = serversDir.resolve(config.name);
        if (Files.exists(serverInstanceDir) && forceReplace) {
            deleteServerDirectory(serverInstanceDir);
        }
        Files.createDirectories(serverInstanceDir);

        // Mark this server as sandbox so we can clean it up on stop/prune
        try {
            Files.writeString(serverInstanceDir.resolve(".sandbox"), "sandbox");
        } catch (IOException e) {
            System.err.println("Warning: Failed to write sandbox marker: " + e.getMessage());
        }

        // Generate CATALINA_BASE configuration from the Express CATALINA_HOME
        org.lucee.lucli.server.runtime.CatalinaBaseConfigGenerator sandboxConfigGen =
                new org.lucee.lucli.server.runtime.CatalinaBaseConfigGenerator();
        sandboxConfigGen.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir, 0, forceReplace);

        // Write CFConfig (.CFConfig.json) if present in the in-memory configuration
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, serverInstanceDir);

        // Deploy any extension dependencies to the sandbox server
        deployExtensionsForServer(projectDir, serverInstanceDir);

        // Copy welcome index.cfm if Lucee is enabled and no index.cfm exists in webroot
        if (config.enableLucee) {
            copyWelcomeIndexIfMissing(config, projectDir);
        }

        // Launch background process; this returns immediately
        ServerInstance instance = launchTomcatProcess(luceeExpressDir, serverInstanceDir, config, projectDir,
                                                     agentOverrides, environment, false, "lucee-express");

        // Optionally wait for startup before returning (same timeout as normal start)
        waitForServerStartup(instance, 30);

        return instance;
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

        // Determine environment key for locking
        String envKey = (environment == null || environment.trim().isEmpty())
                ? "_default"
                : environment.trim();

        // Optionally auto-install extension dependencies based on
        // dependencySettings.autoInstallOnServerStart before starting the
        // server, so that moved/updated extensions are reflected in the lock
        // file without requiring an explicit `lucli deps install`.
        autoInstallExtensionDependenciesIfEnabled(projectDir, environment);

        // Load configuration, applying server lock when present (lenient behaviour)
        LuceeServerConfig.ServerConfig config;
        org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(projectDir);
        org.lucee.lucli.config.LuceeLockFile.ServerLock envLock = lockFile.getServerLock(envKey);

        if (envLock != null && envLock.locked && envLock.effectiveConfig != null) {
            // Use locked effective config snapshot
            config = envLock.effectiveConfig;

            // Detect drift between current lucee.json and the hash stored with the lock
            Path cfgPath = projectDir.resolve(cfgFile);
            String currentHash = org.lucee.lucli.config.LuceeLockFile.computeConfigHash(cfgPath);
            String lockedHash = envLock.configHash;

            if (currentHash != null && lockedHash != null && !currentHash.equals(lockedHash)) {
                System.err.println("⚠️  Server configuration is LOCKED for env '" + envKey + "' (lucee-lock.json)\n" +
                        "    - Using locked configuration snapshot\n" +
                        "    - Detected changes in " + cfgFile + " since the lock was created (hash mismatch)\n" +
                        "    - To roll these changes into the lock:\n" +
                        "        lucli server lock" + ("_default".equals(envKey) ? "" : " --env=" + envKey) + " --update\n" +
                        "    - To remove the lock:\n" +
                        "        lucli server unlock" + ("_default".equals(envKey) ? "" : " --env=" + envKey));
            } else {
                System.out.println("ℹ️  Using locked server configuration for env '" + envKey + "' (lucee-lock.json)");
            }
        } else {
            // No active lock: load configuration from lucee.json as usual
            config = LuceeServerConfig.loadConfig(projectDir, cfgFile);

            // Apply environment overrides if specified
            if (environment != null && !environment.trim().isEmpty()) {
                config = LuceeServerConfig.applyEnvironment(config, environment, projectDir);
            }
        }
        
        // Resolve secrets only for actual server startup (not for generic config reads)
        LuceeServerConfig.resolveSecretPlaceholders(config, projectDir);
        
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
        
        // At this point the logical server name and environment are resolved
        // and any existing LuCLI-managed server directories have been checked.
        // Delegate the actual runtime-specific startup to a RuntimeProvider.

        LuceeServerConfig.RuntimeConfig runtimeConfig = LuceeServerConfig.getEffectiveRuntime(config);
        RuntimeProvider provider = getRuntimeProvider(runtimeConfig.type);
        return provider.start(this, config, projectDir, environment, agentOverrides, foreground, forceReplace);
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
        Path serverDir = instance.getServerDir();
        Path pidFile = serverDir.resolve("server.pid");
        Path sandboxMarker = serverDir.resolve(".sandbox");
        boolean isSandbox = Files.exists(sandboxMarker);

        // Docker-managed servers: stop via docker stop + docker rm.
        Path dockerMarker = serverDir.resolve(".docker-container");
        if (Files.exists(dockerMarker)) {
            String containerName = Files.readString(dockerMarker).trim();
            boolean stopped = org.lucee.lucli.server.runtime.DockerRuntimeProvider
                    .stopDockerContainer(containerName);
            Files.deleteIfExists(pidFile);
            Files.deleteIfExists(dockerMarker);
            if (isSandbox) {
                deleteServerDirectory(serverDir);
            }
            return stopped;
        }

        // Jetty-managed servers: graceful shutdown via STOP.PORT / STOP.KEY.
        Path jettyStopPortFile = serverDir.resolve(".jetty-stop-port");
        Path jettyStopKeyFile = serverDir.resolve(".jetty-stop-key");
        Path jettyHomeFile = serverDir.resolve(".jetty-home");
        if (Files.exists(jettyStopPortFile) && Files.exists(jettyStopKeyFile) && Files.exists(jettyHomeFile)) {
            try {
                int stopPort = Integer.parseInt(Files.readString(jettyStopPortFile).trim());
                String stopKey = Files.readString(jettyStopKeyFile).trim();
                Path jettyHome = Paths.get(Files.readString(jettyHomeFile).trim());

                boolean stopped = JettyRuntimeProvider.stopJettyServer(jettyHome, stopPort, stopKey);
                if (stopped) {
                    // Wait briefly for process to exit
                    if (instance.getPid() > 0) {
                        ProcessHandle ph = ProcessHandle.of(instance.getPid()).orElse(null);
                        if (ph != null && ph.isAlive()) {
                            try {
                                ph.onExit().orTimeout(10, TimeUnit.SECONDS).join();
                            } catch (Exception ignored) {
                                ph.destroyForcibly();
                            }
                        }
                    }
                    Files.deleteIfExists(pidFile);
                    if (isSandbox) {
                        deleteServerDirectory(serverDir);
                    }
                    return true;
                }
                // If Jetty stop command failed, fall through to generic PID-based stop
            } catch (Exception e) {
                // Fall through to generic stop
            }
        }

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
                Files.deleteIfExists(pidFile);

                // If this was a sandbox server, remove the entire server directory
                if (isSandbox) {
                    deleteServerDirectory(serverDir);
                }
                
                return true;
            }
        } catch (Exception e) {
            // Fallback: remove stale PID file
            Files.deleteIfExists(pidFile);
            if (isSandbox) {
                deleteServerDirectory(serverDir);
            }
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
        
        boolean isRunning = isProcessRunning(instance.getPid(), instance.getServerDir());
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
                            isRunning = isProcessRunning(pid, serverDir);
                        }
                    } catch (Exception e) {
                        // Invalid PID file, server is not running
                    }
                }
                
                // Docker fallback: container may be running without a valid server.pid
                if (!isRunning) {
                    Path dockerMarker = serverDir.resolve(".docker-container");
                    if (Files.exists(dockerMarker)) {
                        try {
                            String containerName = Files.readString(dockerMarker).trim();
                            if (org.lucee.lucli.server.runtime.DockerRuntimeProvider
                                    .isDockerContainerRunning(containerName)) {
                                isRunning = true;
                                pid = -1;
                                if (port <= 0) {
                                    port = recoverDockerPort(serverDir);
                                }
                            }
                        } catch (Exception ignored) {
                        }
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
            
            if (Files.exists(pidFile)) {
                String pidContent = Files.readString(pidFile).trim();
                String[] parts = pidContent.split(":");
                if (parts.length >= 2) {
                    long pid = Long.parseLong(parts[0]);
                    int port = Integer.parseInt(parts[1]);
                    
                    if (isProcessRunning(pid, serverDir)) {
                        return new ServerInstance(config.name, pid, port, serverDir, projectDir);
                    }
                    // Stale PID file
                    Files.deleteIfExists(pidFile);
                }
            }
            
            // Fallback: Docker container may still be running even if server.pid
            // was deleted (e.g. by an older LuCLI build).
            ServerInstance dockerInstance = getDockerFallbackInstance(config.name, serverDir, projectDir);
            if (dockerInstance != null) {
                return dockerInstance;
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check for a running Docker container when server.pid is missing.
     * If the .docker-container marker exists and the container is running,
     * reconstruct a ServerInstance from the config port.
     */
    private ServerInstance getDockerFallbackInstance(String serverName, Path serverDir, Path projectDir) {
        try {
            Path dockerMarker = serverDir.resolve(".docker-container");
            if (!Files.exists(dockerMarker)) {
                return null;
            }
            String containerName = Files.readString(dockerMarker).trim();
            if (!org.lucee.lucli.server.runtime.DockerRuntimeProvider.isDockerContainerRunning(containerName)) {
                return null;
            }
            // Recover port from lucee.json via project path marker
            int port = recoverDockerPort(serverDir);
            // Re-create server.pid so future operations work normally
            if (port > 0) {
                Files.writeString(serverDir.resolve("server.pid"), "-1:" + port);
            }
            return new ServerInstance(serverName, -1L, port, serverDir, projectDir);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Try to recover the HTTP port for a Docker server from its project config.
     */
    private int recoverDockerPort(Path serverDir) {
        try {
            Path projectPathFile = serverDir.resolve(".project-path");
            if (Files.exists(projectPathFile)) {
                Path projectDir = Paths.get(Files.readString(projectPathFile).trim());
                LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
                return config.port;
            }
        } catch (Exception ignored) {
        }
        return -1;
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
    
    public void checkAndReportPortConflicts(LuceeServerConfig.ServerConfig config, LuceeServerConfig.PortConflictResult portResult)
                throws IOException {
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
        }

     private void displayPortDetails(LuceeServerConfig.ServerConfig config, boolean foreground) {
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
        }


    /**
     * Check if a process is running.
     * For Docker servers (PID <= 0) this checks the container status.
     */
    private boolean isProcessRunning(long pid, Path serverDir) {
        if (pid <= 0) {
            // Docker-managed server: check container status
            if (serverDir != null) {
                Path dockerMarker = serverDir.resolve(".docker-container");
                if (Files.exists(dockerMarker)) {
                    try {
                        String containerName = Files.readString(dockerMarker).trim();
                        return org.lucee.lucli.server.runtime.DockerRuntimeProvider
                                .isDockerContainerRunning(containerName);
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            return false;
        }
        
        try {
            ProcessHandle processHandle = ProcessHandle.of(pid).orElse(null);
            return processHandle != null && processHandle.isAlive();
        } catch (Exception e) {
            return false;
        }
    }

    /** Convenience overload for callers without a server dir reference. */
    private boolean isProcessRunning(long pid) {
        return isProcessRunning(pid, null);
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
                return isProcessRunning(pid, serverDir);
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
    public void openBrowserForServer(ServerInstance instance, LuceeServerConfig.ServerConfig config) {
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
     * Ensure the Lucee engine JAR for the specified version and variant is available.
     *
     * @param version the Lucee version (e.g. "6.2.4.24"); when null/blank, DEFAULT_VERSION is used
     * @param variant one of "standard", "light", or "zero" (case-insensitive)
     * @return the path to the downloaded JAR inside the LuCLI jars directory
     */
    public Path ensureLuceeJar(String version, String variant) throws Exception {
        String effectiveVersion = (version == null || version.trim().isEmpty())
                ? DEFAULT_VERSION
                : version.trim();

        String effectiveVariant = (variant == null || variant.trim().isEmpty())
                ? "standard"
                : variant.trim().toLowerCase();

        String downloadUrl;
        String jarFileName;

        switch (effectiveVariant) {
            case "standard":
                downloadUrl = LUCEE_JAR_STANDARD_TEMPLATE.replace("{version}", effectiveVersion);
                jarFileName = "lucee-" + effectiveVersion + ".jar";
                break;
            case "light":
                downloadUrl = LUCEE_JAR_LIGHT_TEMPLATE.replace("{version}", effectiveVersion);
                jarFileName = "lucee-light-" + effectiveVersion + ".jar";
                break;
            case "zero":
                downloadUrl = LUCEE_JAR_ZERO_TEMPLATE.replace("{version}", effectiveVersion);
                jarFileName = "lucee-zero-" + effectiveVersion + ".jar";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown Lucee JAR variant '" + variant + "'. Supported variants: standard, light, zero.");
        }

        Path jarPath = jarsDir.resolve(jarFileName);
        if (Files.exists(jarPath)) {
            // Already downloaded
            return jarPath;
        }

        System.out.println("Downloading Lucee " + effectiveVariant + " JAR " + effectiveVersion + "...");
        downloadFile(downloadUrl, jarPath);

        return jarPath;
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
     * Auto-install extension dependencies when dependencySettings.
     * autoInstallOnServerStart is enabled. This re-runs the
     * ExtensionDependencyInstaller for all extension dependencies declared in
     * lucee.json (and optionally devDependencies, depending on
     * dependencySettings.installDevDependencies) and updates lucee-lock.json.
     *
     * Git/CFML dependencies are left to the explicit `lucli deps install`
     * command for now; this hook focuses on keeping extensions in sync so that
     * moved or updated .lex files are reflected automatically when starting
     * a server.
     */
    private void autoInstallExtensionDependenciesIfEnabled(Path projectDir, String environment) {
        try {
            org.lucee.lucli.config.LuceeJsonConfig depConfig =
                    org.lucee.lucli.config.LuceeJsonConfig.load(projectDir);

            // Apply environment-specific overrides for dependencySettings if
            // requested. We swallow invalid environments here so that server
            // startup can still proceed (LuceeServerConfig will perform its
            // own strict environment validation).
            if (environment != null && !environment.trim().isEmpty()) {
                try {
                    depConfig.applyEnvironment(environment.trim());
                } catch (IllegalArgumentException ignored) {
                    // No matching environment for dependencies; ignore.
                }
            }

            org.lucee.lucli.config.DependencySettingsConfig settings = depConfig.getDependencySettings();
            if (settings == null || !settings.isAutoInstallOnServerStart()) {
                return; // Feature disabled
            }

            java.util.List<org.lucee.lucli.config.DependencyConfig> prodDeps = depConfig.parseDependencies();
            java.util.List<org.lucee.lucli.config.DependencyConfig> devDeps = java.util.List.of();

            Boolean installDevOverride = settings.getInstallDevDependencies();
            boolean installDev = (installDevOverride != null) ? installDevOverride.booleanValue() : true;
            if (installDev) {
                devDeps = depConfig.parseDevDependencies();
            }

            if ((prodDeps == null || prodDeps.isEmpty()) && (devDeps == null || devDeps.isEmpty())) {
                return; // Nothing to do
            }

            // Read existing lock file so we can preserve non-extension entries
            org.lucee.lucli.config.LuceeLockFile existingLock =
                    org.lucee.lucli.config.LuceeLockFile.read(projectDir);

            java.util.Map<String, org.lucee.lucli.deps.LockedDependency> newProd =
                    new java.util.LinkedHashMap<>(existingLock.getDependencies());
            java.util.Map<String, org.lucee.lucli.deps.LockedDependency> newDev =
                    new java.util.LinkedHashMap<>(existingLock.getDevDependencies());

            ExtensionDependencyInstaller extInstaller = new ExtensionDependencyInstaller(projectDir);

            // Re-install all extension dependencies, overriding any existing
            // lock entries for those names. This is cheap and ensures that
            // moved/updated .lex paths are kept in sync.
            if (prodDeps != null) {
                for (org.lucee.lucli.config.DependencyConfig dep : prodDeps) {
                    if (!"extension".equals(dep.getType())) {
                        continue;
                    }
                    try {
                        org.lucee.lucli.deps.LockedDependency locked = extInstaller.install(dep);
                        newProd.put(dep.getName(), locked);
                    } catch (Exception e) {
                        System.err.println(
                            "Warning: Failed to auto-install extension dependency '" + dep.getName() + "': " + e.getMessage()
                        );
                    }
                }
            }

            if (devDeps != null) {
                for (org.lucee.lucli.config.DependencyConfig dep : devDeps) {
                    if (!"extension".equals(dep.getType())) {
                        continue;
                    }
                    try {
                        org.lucee.lucli.deps.LockedDependency locked = extInstaller.install(dep);
                        newDev.put(dep.getName(), locked);
                    } catch (Exception e) {
                        System.err.println(
                            "Warning: Failed to auto-install dev extension dependency '" + dep.getName() + "': " + e.getMessage()
                        );
                    }
                }
            }

            org.lucee.lucli.config.LuceeLockFile updated = new org.lucee.lucli.config.LuceeLockFile();
            updated.setDependencies(newProd);
            updated.setDevDependencies(newDev);
            updated.write(projectDir.toFile());
        } catch (Exception e) {
            System.err.println("Warning: Failed to auto-install extension dependencies: " + e.getMessage());
        }
    }
    
    /**
     * Deploy extension dependencies (.lex files) to server's lucee-server/deploy folder.
     * Called before server startup.
     */
    public void deployExtensionsForServer(Path projectDir, Path serverInstanceDir) {
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
     * Copy the welcome index.cfm to the webroot if it doesn't exist.
     * This provides a helpful landing page for new projects.
     * 
     * @param config Server configuration containing webroot
     * @param projectDir Project directory
     */
    private void copyWelcomeIndexIfMissing(LuceeServerConfig.ServerConfig config, Path projectDir) {
        try {
            Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
            Path indexFile = webroot.resolve("index.cfm");
            
            // Only copy if index.cfm doesn't exist
            if (Files.exists(indexFile)) {
                LuCLI.printDebug("LuceeServerManager", "index.cfm already exists at: " + indexFile);
                return;
            }
            
            // Ensure webroot directory exists
            Files.createDirectories(webroot);
            
            // Copy welcome-index.cfm from resources
            try (InputStream is = getClass().getResourceAsStream("/examples/welcome-index.cfm")) {
                if (is == null) {
                    // Resource not found - log for debugging
                    LuCLI.printDebug("LuceeServerManager", "Welcome index template not found in resources at /examples/welcome-index.cfm");
                    return;
                }
                Files.copy(is, indexFile);
                System.out.println("📄 Created welcome index.cfm in webroot");
            }
        } catch (Exception e) {
            // Log but don't fail server startup
            System.err.println("Warning: Could not create welcome index.cfm: " + e.getMessage());
        }
    }
    
    /**
     * Build LUCEE_EXTENSIONS environment variable from extension dependencies.
     *
     * Only provider-based extensions (source = "extension-provider") are
     * included. Each entry is formatted as:
     *   ID
     *   ID;version=x.y.z
     * and entries are comma-separated.
     */
    public static String buildLuceeExtensions(Path projectDir) {
        try {
            // Load lock file to get installed dependencies
            org.lucee.lucli.config.LuceeLockFile lockFile = org.lucee.lucli.config.LuceeLockFile.read(projectDir);
            
            java.util.List<String> entries = new java.util.ArrayList<>();
            
            // Production dependencies
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDependencies().values()) {
                addExtensionEntryIfProviderBased(dep, entries);
            }
            
            // Dev dependencies
            for (org.lucee.lucli.deps.LockedDependency dep : lockFile.getDevDependencies().values()) {
                addExtensionEntryIfProviderBased(dep, entries);
            }
            
            return String.join(",", entries);
        } catch (Exception e) {
            // If we can't read lock file or it doesn't exist, return empty string
            return "";
        }
    }

    /**
     * Add a LUCEE_EXTENSIONS entry for the given locked dependency when it is
     * a provider-based extension with a resolved ID.
     */
    private static void addExtensionEntryIfProviderBased(org.lucee.lucli.deps.LockedDependency dep,
                                                         java.util.List<String> entries) {
        if (!"extension".equals(dep.getType())) {
            return;
        }
        if (!"extension-provider".equals(dep.getSource())) {
            return; // path/url-based extensions are handled via deploy directory
        }
        if (dep.getId() == null || dep.getId().trim().isEmpty()) {
            return; // unknown slug or unresolved ID
        }

        StringBuilder sb = new StringBuilder(dep.getId().trim());
        String version = dep.getVersion();
        if (version != null && !version.trim().isEmpty() && !"unknown".equals(version)) {
            sb.append(";version=").append(version.trim());
        }
        entries.add(sb.toString());
    }
    
    /**
     * Launch a Tomcat server process using separate CATALINA_HOME and CATALINA_BASE.
     * This is the unified launch method used by all runtime providers (Express and vendor Tomcat).
     *
     * @param catalinaHome    Read-only Tomcat installation (Express dir or vendor Tomcat)
     * @param catalinaBase    Per-server instance directory (~/.lucli/servers/&lt;name&gt;)
     * @param config          Server configuration
     * @param projectDir      Project directory
     * @param agentOverrides  Agent overrides (may be null)
     * @param environment     Environment name (may be null)
     * @param foreground      If true, uses 'catalina run' and blocks; if false, uses startup script
     * @param runtimeType     Runtime type marker written to .runtime-type (e.g. "lucee-express", "tomcat")
     * @return ServerInstance (only when foreground=false; foreground=true returns null)
     */
    public ServerInstance launchTomcatProcess(Path catalinaHome, Path catalinaBase,
                                              LuceeServerConfig.ServerConfig config,
                                              Path projectDir,
                                              AgentOverrides agentOverrides,
                                              String environment,
                                              boolean foreground,
                                              String runtimeType) throws Exception {

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // Build command — look for scripts in bin/ first, then root (Express compat)
        List<String> command = new ArrayList<>();
        Path scriptPath;

        if (foreground) {
            String scriptName = isWindows ? "catalina.bat" : "catalina.sh";
            scriptPath = catalinaHome.resolve("bin").resolve(scriptName);
            command.add(scriptPath.toString());
            command.add("run");
        } else {
            String scriptName = isWindows ? "startup.bat" : "startup.sh";
            scriptPath = catalinaHome.resolve("bin").resolve(scriptName);
            // Lucee Express puts startup.sh in root; fall back
            if (!Files.exists(scriptPath)) {
                scriptPath = catalinaHome.resolve(scriptName);
            }
            command.add(scriptPath.toString());
        }

        if (!Files.exists(scriptPath)) {
            throw new Exception("Tomcat script not found: " + scriptPath);
        }

        // Create logs dir before redirecting output
        Path logsDir = catalinaBase.resolve("logs");
        Files.createDirectories(logsDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(catalinaHome.toFile());

        if (foreground) {
            pb.inheritIO();
        } else {
            pb.redirectOutput(logsDir.resolve("server.out").toFile());
            pb.redirectError(logsDir.resolve("server.err").toFile());
        }

        // Environment variables
        Map<String, String> env = pb.environment();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(env);

        // Key: CATALINA_HOME != CATALINA_BASE
        env.put("CATALINA_HOME", catalinaHome.toString());
        env.put("CATALINA_BASE", catalinaBase.toString());

        List<String> catalinaOpts = buildCatalinaOpts(config, agentOverrides, projectDir);
        env.put("CATALINA_OPTS", String.join(" ", catalinaOpts));

        Path pidFile = catalinaBase.resolve("server.pid");
        // Use a separate file for CATALINA_PID so Tomcat's startup script
        // doesn't overwrite our PID:PORT format in server.pid
        Path catalinaPidFile = catalinaBase.resolve("catalina.pid");
        env.put("CATALINA_PID", catalinaPidFile.toString());
        env.put("CATALINA_OUT", logsDir.resolve("catalina.out").toString());

        if (config.admin != null && config.admin.password != null && !config.admin.password.isEmpty()) {
            env.put("LUCEE_ADMIN_PASSWORD", config.admin.password);
        }

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(projectDir);
        if (luceeExtensions != null && !luceeExtensions.isEmpty()) {
            env.put("LUCEE_EXTENSIONS", luceeExtensions);
        }

        if (config.envVars != null) {
            for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().trim().isEmpty() && entry.getValue() != null) {
                    env.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Marker files
        Files.writeString(catalinaBase.resolve(".project-path"), projectDir.toAbsolutePath().toString());
        if (environment != null && !environment.trim().isEmpty()) {
            Files.writeString(catalinaBase.resolve(".environment"), environment.trim());
        }
        if (runtimeType != null && !runtimeType.isEmpty()) {
            Files.writeString(catalinaBase.resolve(".runtime-type"), runtimeType);
        }

        if (foreground) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(pidFile);
                    Files.deleteIfExists(catalinaPidFile);
                } catch (IOException e) {
                    // Ignore
                }
            }));
        }

        Process process = pb.start();

        // Write LuCLI's PID:PORT format. The initial PID is the startup
        // script; Tomcat writes the actual Java PID to catalina.pid
        // asynchronously. We update server.pid once that file appears.
        long launcherPid = process.pid();
        Files.writeString(pidFile, launcherPid + ":" + config.port);

        // Wait briefly for Tomcat to write the real Java PID via CATALINA_PID
        long javaPid = launcherPid;
        for (int i = 0; i < 20; i++) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) { break; }
            if (Files.exists(catalinaPidFile)) {
                try {
                    String raw = Files.readString(catalinaPidFile).trim();
                    if (!raw.isEmpty()) {
                        javaPid = Long.parseLong(raw);
                        break;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        // Rewrite with the real Java PID so status/stop work correctly
        Files.writeString(pidFile, javaPid + ":" + config.port);

        if (foreground) {
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("\nServer exited with code: " + exitCode);
                } else {
                    System.out.println("\nServer stopped successfully.");
                }
            } catch (InterruptedException e) {
                System.out.println("\nShutting down server...");
                process.destroy();
                try {
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ex) {
                    process.destroyForcibly();
                }
            } finally {
                Files.deleteIfExists(pidFile);
                Files.deleteIfExists(catalinaPidFile);
            }
            return null;
        } else {
            return new ServerInstance(config.name, javaPid, config.port, catalinaBase, projectDir);
        }
    }

    /**
     * @deprecated Use {@link #launchTomcatProcess} instead. This wrapper preserves the old
     * CATALINA_HOME==CATALINA_BASE behaviour for callers that haven't migrated yet.
     */
    @Deprecated
    public ServerInstance launchServerProcess(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                             Path projectDir, Path luceeExpressDir,
                                             AgentOverrides agentOverrides, String environment, boolean foreground) throws Exception {
        return launchTomcatProcess(luceeExpressDir, serverInstanceDir, config, projectDir,
                agentOverrides, environment, foreground, "lucee-express");
    }
    
    /**
     * Launch a Jetty server process using separate JETTY_HOME and JETTY_BASE.
     *
     * Unlike Tomcat, Jetty launches via {@code java -jar $JETTY_HOME/start.jar}
     * with the working directory set to JETTY_BASE. JVM options are configured
     * via start.d/jvm.ini rather than environment variables.
     *
     * @param jettyHome       Read-only Jetty distribution
     * @param jettyBase       Per-server instance directory (~/.lucli/servers/&lt;name&gt;)
     * @param config          Server configuration
     * @param projectDir      Project directory
     * @param agentOverrides  Agent overrides (may be null)
     * @param environment     Environment name (may be null)
     * @param foreground      If true, blocks until server exits; if false, returns immediately
     * @param stopPort        STOP.PORT for graceful shutdown
     * @param stopKey         STOP.KEY for graceful shutdown
     * @return ServerInstance (only when foreground=false; foreground=true returns null)
     */
    public ServerInstance launchJettyProcess(Path jettyHome, Path jettyBase,
                                             LuceeServerConfig.ServerConfig config,
                                             Path projectDir,
                                             AgentOverrides agentOverrides,
                                             String environment,
                                             boolean foreground,
                                             int stopPort, String stopKey) throws Exception {

        String javaExe = findJavaExecutable();
        Path startJar = jettyHome.resolve("start.jar");

        // Build command: java -jar $JETTY_HOME/start.jar
        // Jetty resolves jetty.base from the working directory
        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.add("-jar");
        command.add(startJar.toString());

        // Create logs dir before redirecting output
        Path logsDir = jettyBase.resolve("logs");
        Files.createDirectories(logsDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        // Jetty resolves JETTY_BASE from the working directory
        pb.directory(jettyBase.toFile());

        if (foreground) {
            pb.inheritIO();
        } else {
            pb.redirectOutput(logsDir.resolve("jetty.out").toFile());
            pb.redirectError(logsDir.resolve("jetty.err").toFile());
        }

        // Environment variables
        Map<String, String> env = pb.environment();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(env);

        env.put("JETTY_HOME", jettyHome.toString());
        env.put("JETTY_BASE", jettyBase.toString());

        if (config.admin != null && config.admin.password != null && !config.admin.password.isEmpty()) {
            env.put("LUCEE_ADMIN_PASSWORD", config.admin.password);
        }

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(projectDir);
        if (luceeExtensions != null && !luceeExtensions.isEmpty()) {
            env.put("LUCEE_EXTENSIONS", luceeExtensions);
        }

        if (config.envVars != null) {
            for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().trim().isEmpty() && entry.getValue() != null) {
                    env.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Marker files
        Path pidFile = jettyBase.resolve("server.pid");
        Files.writeString(jettyBase.resolve(".project-path"), projectDir.toAbsolutePath().toString());
        if (environment != null && !environment.trim().isEmpty()) {
            Files.writeString(jettyBase.resolve(".environment"), environment.trim());
        }
        Files.writeString(jettyBase.resolve(".runtime-type"), "jetty");

        // Write Jetty stop markers so stopServer() can perform graceful shutdown
        Files.writeString(jettyBase.resolve(".jetty-stop-port"), String.valueOf(stopPort));
        Files.writeString(jettyBase.resolve(".jetty-stop-key"), stopKey);
        Files.writeString(jettyBase.resolve(".jetty-home"), jettyHome.toAbsolutePath().toString());

        if (foreground) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(pidFile);
                } catch (IOException e) {
                    // Ignore
                }
            }));
        }

        Process process = pb.start();
        long pid = process.pid();
        Files.writeString(pidFile, pid + ":" + config.port);

        if (foreground) {
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("\nServer exited with code: " + exitCode);
                } else {
                    System.out.println("\nServer stopped successfully.");
                }
            } catch (InterruptedException e) {
                System.out.println("\nShutting down Jetty server...");
                // Try graceful shutdown via STOP.PORT/STOP.KEY
                JettyRuntimeProvider.stopJettyServer(jettyHome, stopPort, stopKey);
                try {
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ex) {
                    process.destroyForcibly();
                }
            } finally {
                Files.deleteIfExists(pidFile);
            }
            return null;
        } else {
            return new ServerInstance(config.name, pid, config.port, jettyBase, projectDir);
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
     * Public so it can be reused by runtime providers and other server components,
     * and exercised directly from tests.
     */
    public List<String> buildCatalinaOpts(LuceeServerConfig.ServerConfig config, AgentOverrides overrides, Path projectDir) {
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
        
        // Lucee extensions configuration is passed via the LUCEE_EXTENSIONS
        // environment variable in launchServerProcess, which avoids quoting
        // issues with semicolons/commas inside CATALINA_OPTS.
        
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
    public void waitForServerStartup(ServerInstance instance, int timeoutSeconds) throws Exception {
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