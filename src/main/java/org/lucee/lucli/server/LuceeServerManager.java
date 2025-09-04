package org.lucee.lucli.server;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages Lucee server instances - downloading, configuring, starting, and stopping servers
 */
public class LuceeServerManager {
    
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
        return startServer(projectDir, versionOverride, false, null);
    }
    
    /**
     * Start a Lucee server with options for handling existing servers
     */
    public ServerInstance startServer(Path projectDir, String versionOverride, boolean forceReplace, String customName) throws Exception {
        // Load configuration
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
        
        // Override version if specified
        if (versionOverride != null && !versionOverride.trim().isEmpty()) {
            config.version = versionOverride;
        }
        
        // Use custom name if provided
        if (customName != null && !customName.trim().isEmpty()) {
            config.name = customName.trim();
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
            
            if (config.monitoring != null && config.monitoring.jmx != null && !LuceeServerConfig.isPortAvailable(config.monitoring.jmx.port)) {
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
            
            throw new IllegalStateException(errorMessage.toString().trim());
        }
        
        // Build port information message
        StringBuilder portInfo = new StringBuilder();
        portInfo.append("Starting server '").append(config.name).append("' on:");
        portInfo.append("\n  HTTP port:     ").append(config.port);
        portInfo.append("\n  Shutdown port: ").append(LuceeServerConfig.getShutdownPort(config.port));
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            portInfo.append("\n  JMX port:      ").append(config.monitoring.jmx.port);
        }
        
        System.out.println(portInfo.toString());
        
        // Create server instance directory
        Path serverInstanceDir = serversDir.resolve(config.name);
        Files.createDirectories(serverInstanceDir);
        
        // Generate Tomcat configuration
        TomcatConfigGenerator configGenerator = new TomcatConfigGenerator();
        configGenerator.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir);
        
        // Start the server process
        ServerInstance instance = launchServerProcess(serverInstanceDir, config, projectDir, luceeExpressDir);
        
        // Wait for server to start
        waitForServerStartup(instance, 30); // 30 second timeout
        
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
                
                servers.add(new ServerInfo(serverName, pid, port, isRunning, serverDir, projectDir));
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
                    
                    return new ServerInfo(serverName, pid, port, isRunning, serverDir, storedPath);
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
        
        return new ServerInfo(serverName, pid, port, isRunning, serverDir, projectDir);
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
     * Ensure Lucee Express for the specified version is available
     */
    private Path ensureLuceeExpress(String version) throws Exception {
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
     * Launch the server process using Lucee Express startup script
     */
    private ServerInstance launchServerProcess(Path serverInstanceDir, LuceeServerConfig.ServerConfig config, 
                                             Path projectDir, Path luceeExpressDir) throws Exception {
        
        // Use the Lucee Express startup script
        Path startupScript = luceeExpressDir.resolve("startup.sh");
        if (!Files.exists(startupScript) || !Files.isExecutable(startupScript)) {
            throw new Exception("Lucee Express startup script not found or not executable: " + startupScript);
        }
        
        // Build command to run the startup script
        List<String> command = new ArrayList<>();
        command.add(startupScript.toString());
        
        // Create process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(luceeExpressDir.toFile());
        pb.redirectOutput(serverInstanceDir.resolve("logs/server.out").toFile());
        pb.redirectError(serverInstanceDir.resolve("logs/server.err").toFile());
        
        // Set environment variables for Tomcat configuration
        Map<String, String> env = pb.environment();
        env.put("CATALINA_BASE", serverInstanceDir.toString());
        env.put("CATALINA_HOME", serverInstanceDir.toString()); // Same as CATALINA_BASE since we copy all files
        
        // Set JVM options through CATALINA_OPTS
        List<String> catalinaOpts = new ArrayList<>();
        catalinaOpts.add("-Xms" + config.jvm.minMemory);
        catalinaOpts.add("-Xmx" + config.jvm.maxMemory);
        
        // Add JMX configuration if monitoring is enabled
        if (config.monitoring.enabled) {
            catalinaOpts.add("-Dcom.sun.management.jmxremote");
            catalinaOpts.add("-Dcom.sun.management.jmxremote.port=" + config.monitoring.jmx.port);
            catalinaOpts.add("-Dcom.sun.management.jmxremote.authenticate=false");
            catalinaOpts.add("-Dcom.sun.management.jmxremote.ssl=false");
        }
        
        // Add additional JVM arguments
        catalinaOpts.addAll(Arrays.asList(config.jvm.additionalArgs));
        
        // Join all CATALINA_OPTS into a single string
        env.put("CATALINA_OPTS", String.join(" ", catalinaOpts));
        
        // Set other Tomcat environment variables
        env.put("CATALINA_PID", serverInstanceDir.resolve("server.pid").toString());
        env.put("CATALINA_OUT", serverInstanceDir.resolve("logs/catalina.out").toString());
        
        Process process = pb.start();
        
        // Write PID file (Tomcat should also write one, but we'll track it ourselves)
        String pidContent = process.pid() + ":" + config.port;
        Files.writeString(serverInstanceDir.resolve("server.pid"), pidContent);
        
        // Write project path marker file to track which project this server belongs to
        Files.writeString(serverInstanceDir.resolve(".project-path"), projectDir.toAbsolutePath().toString());
        
        return new ServerInstance(config.name, process.pid(), config.port, serverInstanceDir, projectDir);
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
        
        public ServerInfo(String serverName, long pid, int port, boolean running, Path serverDir, Path projectDir) {
            this.serverName = serverName;
            this.pid = pid;
            this.port = port;
            this.running = running;
            this.serverDir = serverDir;
            this.projectDir = projectDir;
        }
        
        public String getServerName() { return serverName; }
        public long getPid() { return pid; }
        public int getPort() { return port; }
        public boolean isRunning() { return running; }
        public Path getServerDir() { return serverDir; }
        public Path getProjectDir() { return projectDir; }
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
