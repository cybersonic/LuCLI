package org.lucee.lucli.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles Lucee server configuration from lucee.json files
 */
public class LuceeServerConfig {
    
    public static class ServerConfig {
        public String name;
        public String version = "6.2.2.91";
        public int port = 8080;
        public String webroot = "./";
        public MonitoringConfig monitoring = new MonitoringConfig();
        public JvmConfig jvm = new JvmConfig();
    }
    
    public static class MonitoringConfig {
        public boolean enabled = true;
        public JmxConfig jmx = new JmxConfig();
    }
    
    public static class JmxConfig {
        public int port = 8999;
    }
    
    public static class JvmConfig {
        public String maxMemory = "512m";
        public String minMemory = "128m";
        public String[] additionalArgs = new String[0];
    }
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    /**
     * Load configuration from lucee.json in the specified directory
     */
    public static ServerConfig loadConfig(Path projectDir) throws IOException {
        Path configFile = projectDir.resolve("lucee.json");
        
        if (!Files.exists(configFile)) {
            // Create default configuration
            ServerConfig defaultConfig = createDefaultConfig(projectDir);
            saveConfig(defaultConfig, configFile);
            return defaultConfig;
        }
        
        ServerConfig config = objectMapper.readValue(configFile.toFile(), ServerConfig.class);
        
        // Set default name if not specified - use only the last part of the path
        if (config.name == null || config.name.trim().isEmpty()) {
            config.name = projectDir.getFileName().toString();
        }
        
        // Don't resolve port conflicts here - do it just before starting server
        // This prevents race conditions where ports become unavailable between config load and server start
        
        return config;
    }
    
    /**
     * Save configuration to lucee.json
     */
    public static void saveConfig(ServerConfig config, Path configFile) throws IOException {
        objectMapper.writeValue(configFile.toFile(), config);
    }
    
    /**
     * Create default configuration for a project
     */
    private static ServerConfig createDefaultConfig(Path projectDir) {
        ServerConfig config = new ServerConfig();
        config.name = projectDir.getFileName().toString();
        config.port = findAvailablePort(8080, 8000, 8999);
        config.monitoring.jmx.port = findAvailablePort(8999, 8000, 8999);
        return config;
    }
    
    /**
     * Find an available port starting from the preferred port
     */
    public static int findAvailablePort(int preferredPort, int rangeStart, int rangeEnd) {
        // First try the preferred port
        if (isPortAvailable(preferredPort)) {
            return preferredPort;
        }
        
        // Then search in the range
        for (int port = rangeStart; port <= rangeEnd; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        
        // If no port in range is available, try system-assigned port
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to find available port", e);
        }
    }
    
    /**
     * Check if a port is available
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Get the server name (with conflict resolution)
     */
    public static String getUniqueServerName(String baseName, Path lucliServersDir) {
        String serverName = baseName;
        int counter = 1;
        
        while (Files.exists(lucliServersDir.resolve(serverName))) {
            serverName = baseName + "-" + counter;
            counter++;
        }
        
        return serverName;
    }
    
    /**
     * Result of port conflict resolution
     */
    public static class PortConflictResult {
        public final boolean hasConflicts;
        public final String message;
        public final ServerConfig updatedConfig;
        
        public PortConflictResult(boolean hasConflicts, String message, ServerConfig updatedConfig) {
            this.hasConflicts = hasConflicts;
            this.message = message;
            this.updatedConfig = updatedConfig;
        }
    }
    
    /**
     * Resolve port conflicts for all ports used by the server
     * This should be called right before starting the server to avoid race conditions
     * 
     * @param config The server configuration
     * @param allowPortReassignment Whether to automatically reassign ports or fail on conflicts
     * @param serverManager Optional server manager to check for specific server conflicts
     * @return PortConflictResult with conflict information and resolved config
     */
    public static PortConflictResult resolvePortConflicts(ServerConfig config, boolean allowPortReassignment, Object serverManager) {
        StringBuilder conflictMessages = new StringBuilder();
        boolean hasConflicts = false;
        int originalHttpPort = config.port;
        int originalJmxPort = config.monitoring != null && config.monitoring.jmx != null ? config.monitoring.jmx.port : -1;
        
        // Check for internal port conflicts within the same configuration
        if (config.monitoring != null && config.monitoring.jmx != null) {
            if (config.port == config.monitoring.jmx.port) {
                hasConflicts = true;
                conflictMessages.append("HTTP port (").append(config.port)
                        .append(") and JMX port (").append(config.monitoring.jmx.port)
                        .append(") cannot be the same. Please use different ports in your lucee.json file.\n");
            }
            
            int shutdownPort = getShutdownPort(config.port);
            if (shutdownPort == config.monitoring.jmx.port) {
                hasConflicts = true;
                conflictMessages.append("Shutdown port (").append(shutdownPort)
                        .append(") and JMX port (").append(config.monitoring.jmx.port)
                        .append(") cannot be the same. The shutdown port is calculated as HTTP port + 1000. ");
                conflictMessages.append("Please choose a different HTTP port or JMX port in your lucee.json file.\n");
            }
        }
        
        // Check HTTP port
        if (!isPortAvailable(config.port)) {
            hasConflicts = true;
            conflictMessages.append("HTTP port ").append(config.port).append(" is already in use");
            
            if (allowPortReassignment) {
                int newPort = findAvailablePort(config.port, 8000, 8999);
                conflictMessages.append(", reassigning to port ").append(newPort);
                config.port = newPort;
            } else {
                conflictMessages.append(". Please stop the service using this port or choose a different port.");
            }
            conflictMessages.append("\n");
        }
        
        // Check shutdown port
        int shutdownPort = getShutdownPort(config.port);
        if (!isPortAvailable(shutdownPort)) {
            hasConflicts = true;
            conflictMessages.append("Shutdown port ").append(shutdownPort).append(" (HTTP port + 1000) is already in use");
            
            if (allowPortReassignment) {
                // Find a new HTTP port such that HTTP+1000 is also available
                boolean foundPair = false;
                for (int httpPort = 8000; httpPort <= 8999; httpPort++) {
                    int correspondingShutdownPort = httpPort + 1000;
                    if (isPortAvailable(httpPort) && isPortAvailable(correspondingShutdownPort)) {
                        conflictMessages.append(", reassigning HTTP port to ").append(httpPort);
                        conflictMessages.append(" (shutdown port will be ").append(correspondingShutdownPort).append(")");
                        config.port = httpPort;
                        foundPair = true;
                        break;
                    }
                }
                if (!foundPair) {
                    conflictMessages.append(", could not find available HTTP+shutdown port pair");
                }
            } else {
                conflictMessages.append(". Please stop the service using this port or choose a different HTTP port.");
            }
            conflictMessages.append("\n");
        }
        
        // Check JMX port
        if (config.monitoring != null && config.monitoring.jmx != null) {
            if (!isPortAvailable(config.monitoring.jmx.port)) {
                hasConflicts = true;
                conflictMessages.append("JMX port ").append(config.monitoring.jmx.port).append(" is already in use");
                
                if (allowPortReassignment) {
                    int newJmxPort = findAvailablePort(config.monitoring.jmx.port, 8000, 8999);
                    conflictMessages.append(", reassigning to port ").append(newJmxPort);
                    config.monitoring.jmx.port = newJmxPort;
                } else {
                    conflictMessages.append(". Please stop the service using this port or choose a different JMX port.");
                }
                conflictMessages.append("\n");
            }
        }
        
        // Create a summary message
        String message;
        if (!hasConflicts) {
            message = "All ports are available";
        } else if (allowPortReassignment) {
            message = "Port conflicts detected and resolved:\n" + conflictMessages.toString().trim();
        } else {
            message = "Port conflicts detected:\n" + conflictMessages.toString().trim();
        }
        
        return new PortConflictResult(hasConflicts, message, config);
    }
    
    /**
     * Get the shutdown port for a given HTTP port
     */
    public static int getShutdownPort(int httpPort) {
        return httpPort + 1000;
    }
    
    /**
     * Resolve webroot to absolute path
     */
    public static Path resolveWebroot(ServerConfig config, Path projectDir) {
        Path webroot = Paths.get(config.webroot);
        if (webroot.isAbsolute()) {
            return webroot;
        }
        return projectDir.resolve(webroot).normalize();
    }
}
