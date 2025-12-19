    package org.lucee.lucli.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Handles Lucee server configuration from lucee.json files
 */
public class LuceeServerConfig {
    
    public static class ServerConfig {
        public String name;
        /**
         * Optional hostname used when constructing default URLs and when generating
         * self-signed HTTPS certificates. Defaults to "localhost" when omitted.
         */
        public String host;
        public String version = "6.2.2.91";
        /**
         * Primary HTTP port for Tomcat.
         */
        public int port = 8080;

        /**
         * Optional HTTPS configuration. When omitted or when enabled=false, LuCLI
         * will not configure an HTTPS connector.
         */
        public HttpsConfig https;
        /**
         * Optional explicit shutdown port. When null, the effective shutdown
         * port is derived from the HTTP port using getShutdownPort(port).
         * This field exists so users can pin a specific shutdown port in
         * lucee.json while preserving the current default behaviour.
         */
        public Integer shutdownPort;
        public String webroot = "./";
        public MonitoringConfig monitoring = new MonitoringConfig();
        public JvmConfig jvm = new JvmConfig();
        public UrlRewriteConfig urlRewrite = new UrlRewriteConfig();
        public AdminConfig admin = new AdminConfig();
        /**
         * When false, Lucee CFML servlets and CFML-specific mappings are removed
         * from web.xml so that Tomcat behaves as a static file server for the
         * configured webroot.
         */
        public boolean enableLucee = true;

        
        /**
         * When true, the Lucee REST servlet is enabled in web.xml.
         */
        public boolean enableREST = false;


        // Agent configurations by name
        public Map<String, AgentConfig> agents = new HashMap<>();

        // Browser Opening Behaviour
        public boolean openBrowser = true;
        public String openBrowserURL;

        /**
         * Optional Lucee server configuration to be written to lucee-server/context/.CFConfig.json.
         * When present, this JSON is treated as the source of truth for CFConfig.
         */
        public JsonNode configuration;

        /**
         * Optional path to an external CFConfig JSON file. Relative paths are resolved
         * against the project directory when starting the server.
         */
        public String configurationFile;
    }
    
    public static class HttpsConfig {
        public boolean enabled = false;
        /**
         * Optional HTTPS port. When null, defaults to 8443.
         */
        public Integer port;
        /**
         * When true, LuCLI configures Tomcat to redirect HTTP requests to HTTPS.
         * When null, defaults to true when HTTPS is enabled.
         */
        public Boolean redirect;
    }

    public static class AdminConfig {
        public boolean enabled = true;
    }
    
    public static class UrlRewriteConfig {
        public boolean enabled = true;
        public String routerFile = "index.cfm";
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
    
    public static class AgentConfig {
        public boolean enabled = false;
        public String[] jvmArgs = new String[0];
        public String description;
    }
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            // Keep lucee.json as small as possible by omitting null keys.
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    /**
     * Load configuration from lucee.json in the specified directory
     */
    public static ServerConfig loadConfig(Path projectDir) throws IOException {
        // Load .env file if it exists in the same directory as lucee.json
        loadEnvFile(projectDir);
        
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
        
        // Ensure urlRewrite is initialized for backward compatibility with older configs
        // that don't have this field in their JSON
        if (config.urlRewrite == null) {
            config.urlRewrite = new UrlRewriteConfig();
        }
        
        // Ensure admin is initialized for backward compatibility with older configs
        // that don't have this field in their JSON
        if (config.admin == null) {
            config.admin = new AdminConfig();
        }
        
        // Ensure agents is initialized for backward compatibility with older configs
        if (config.agents == null) {
            config.agents = new HashMap<>();
        }

        // Ensure browser config has sensible defaults for older configs
        // (Jackson will leave primitive boolean as false when field is absent,
        // so we only need to guard against null String.)
        if (config.openBrowserURL != null && config.openBrowserURL.trim().isEmpty()) {
            config.openBrowserURL = null;
        }

        // Perform environment variable substitution on string fields
        // Supports ${VAR_NAME} and ${VAR_NAME:-default_value}
        substituteEnvironmentVariables(config);
        
        // Assign a shutdown port if not explicitly set, checking for conflicts with existing servers
        if (config.shutdownPort == null) {
            assignShutdownPortIfNeeded(config);
        }
        
        // Don't resolve port conflicts here - do it just before starting server
        // This prevents race conditions where ports become unavailable between config load and server start
        
        return config;
    }

    /**
     * Effective hostname (defaults to "localhost").
     */
    public static String getEffectiveHost(ServerConfig config) {
        if (config == null || config.host == null || config.host.trim().isEmpty()) {
            return "localhost";
        }
        return config.host.trim();
    }

    /**
     * Whether HTTPS is enabled for this server.
     */
    public static boolean isHttpsEnabled(ServerConfig config) {
        return config != null && config.https != null && config.https.enabled;
    }

    /**
     * Effective HTTPS port (defaults to 8443).
     */
    public static int getEffectiveHttpsPort(ServerConfig config) {
        if (config == null || config.https == null || config.https.port == null) {
            return 8443;
        }
        return config.https.port.intValue();
    }

    /**
     * Whether HTTP->HTTPS redirect is enabled (defaults to true when HTTPS is enabled).
     */
    public static boolean isHttpsRedirectEnabled(ServerConfig config) {
        if (!isHttpsEnabled(config)) {
            return false;
        }
        if (config.https.redirect == null) {
            return true;
        }
        return config.https.redirect.booleanValue();
    }
    
    /**
     * Save configuration to lucee.json
     */
    public static void saveConfig(ServerConfig config, Path configFile) throws IOException {
        objectMapper.writeValue(configFile.toFile(), config);
    }
    
    /**
     * Load environment variables from a .env file in the project directory.
     * The .env file should be in the same directory as lucee.json.
     * Lines starting with # are treated as comments.
     * Supports KEY=VALUE format, including quoted values.
     * Environment variables in the file take precedence over system env vars.
     */
    private static void loadEnvFile(Path projectDir) {
        Path envFile = projectDir.resolve(".env");
        if (!Files.exists(envFile)) {
            return; // No .env file, skip
        }
        
        try {
            java.util.List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=VALUE
                int equalIndex = line.indexOf('=');
                if (equalIndex <= 0) {
                    continue; // Invalid line, skip
                }
                
                String key = line.substring(0, equalIndex).trim();
                String value = line.substring(equalIndex + 1).trim();
                
                // Remove quotes if present
                if ((value.startsWith("\"" ) && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                
                // Set as environment variable (will be used by System.getenv())
                // Note: We can't actually modify System.getenv(), so we store in a custom map
                envVariables.put(key, value);
            }
        } catch (IOException e) {
            // Log warning but don't fail if .env can't be read
            System.err.println("Warning: Could not load .env file: " + e.getMessage());
        }
    }
    
    // Map to store environment variables loaded from .env files
    private static final Map<String, String> envVariables = new java.util.HashMap<>();
    
    /**
     * Get an environment variable, checking .env loaded vars first, then system env vars
     */
    private static String getEnvVar(String name) {
        // Check .env file variables first
        if (envVariables.containsKey(name)) {
            return envVariables.get(name);
        }
        // Fall back to system environment variables
        return System.getenv(name);
    }
    
    /**
     * Substitute environment variables in all string fields.
     * Supports:
     *   ${VAR_NAME} - replaced with environment variable value
     *   ${VAR_NAME:-default} - replaced with env var or default if not set
     * 
     * This recursively processes the configuration object and nested JSON,
     * allowing environment variables to be used in all config values.
     */
    private static void substituteEnvironmentVariables(ServerConfig config) {
        // Top-level string fields
        if (config.version != null) {
            config.version = replaceEnvVars(config.version);
        }
        if (config.name != null) {
            config.name = replaceEnvVars(config.name);
        }
        if (config.host != null) {
            config.host = replaceEnvVars(config.host);
        }
        if (config.webroot != null) {
            config.webroot = replaceEnvVars(config.webroot);
        }
        if (config.openBrowserURL != null) {
            config.openBrowserURL = replaceEnvVars(config.openBrowserURL);
        }
        if (config.configurationFile != null) {
            config.configurationFile = replaceEnvVars(config.configurationFile);
        }
        
        // Substitute in JVM config
        if (config.jvm != null) {
            if (config.jvm.maxMemory != null) {
                config.jvm.maxMemory = replaceEnvVars(config.jvm.maxMemory);
            }
            if (config.jvm.minMemory != null) {
                config.jvm.minMemory = replaceEnvVars(config.jvm.minMemory);
            }
            if (config.jvm.additionalArgs != null) {
                for (int i = 0; i < config.jvm.additionalArgs.length; i++) {
                    config.jvm.additionalArgs[i] = replaceEnvVars(config.jvm.additionalArgs[i]);
                }
            }
        }
        
        // Substitute in URL Rewrite config
        if (config.urlRewrite != null) {
            if (config.urlRewrite.routerFile != null) {
                config.urlRewrite.routerFile = replaceEnvVars(config.urlRewrite.routerFile);
            }
        }
        
        // Recursively substitute in the configuration JSON object
        // This supports environment variables throughout the entire CFConfig
        if (config.configuration != null) {
            config.configuration = substituteInJsonNode(config.configuration);
        }
    }
    
    /**
     * Recursively substitute environment variables in a JSON node.
     * Handles strings, arrays, and nested objects.
     */
    private static JsonNode substituteInJsonNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        
        if (node.isTextual()) {
            // Replace environment variables in text values
            return objectMapper.getNodeFactory().textNode(replaceEnvVars(node.asText()));
        } else if (node.isArray()) {
            // Recursively process array elements
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode = 
                objectMapper.getNodeFactory().arrayNode();
            for (JsonNode element : node) {
                arrayNode.add(substituteInJsonNode(element));
            }
            return arrayNode;
        } else if (node.isObject()) {
            // Recursively process object fields
            com.fasterxml.jackson.databind.node.ObjectNode objNode = 
                objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                objNode.set(entry.getKey(), substituteInJsonNode(entry.getValue()));
            });
            return objNode;
        } else {
            // Return numbers, booleans, nulls as-is
            return node;
        }
    }
    
    /**
     * Replace environment variables in a string using ${VAR} or ${VAR:-default} syntax
     * Checks .env file variables first, then system environment variables
     */
    private static String replaceEnvVars(String value) {
        if (value == null) {
            return null;
        }
        
        // Pattern: ${VAR_NAME} or ${VAR_NAME:-default_value}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = null;
            
            // Check if it has a default value
            if (placeholder.contains(":-")) {
                String[] parts = placeholder.split(":-", 2);
                String varName = parts[0].trim();
                String defaultValue = parts[1].trim();
                replacement = getEnvVar(varName);
                if (replacement == null) {
                    replacement = defaultValue;
                }
            } else {
                // Just the variable name
                replacement = getEnvVar(placeholder);
                if (replacement == null) {
                    // Keep the placeholder if env var doesn't exist
                    replacement = "${" + placeholder + "}";
                }
            }
            
            // Escape backslashes and dollar signs in the replacement
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Create default configuration for a project, avoiding ports used by existing servers
     */
    public static ServerConfig createDefaultConfig(Path projectDir) {
        ServerConfig config = new ServerConfig();
        config.name = projectDir.getFileName().toString();
        
        // Try to find the LuCLI home directory to check existing servers
        Path lucliHome = getLucliHome();
        Path serversDir = lucliHome.resolve("servers");
        
        // Get all existing server ports to avoid conflicts
        Set<Integer> existingPorts = getExistingServerPorts(serversDir);
        
        // Find available HTTP port, avoiding existing server definitions
        config.port = findAvailablePortAvoidingExisting(8080, 8000, 8999, existingPorts);

        // Default shutdown port follows the traditional pattern of HTTP+1000.
        // This value can be overridden explicitly in lucee.json via
        // "shutdownPort" when users need a fixed, non-derived port.
        config.shutdownPort = getShutdownPort(config.port);
        
        // Find available JMX port, avoiding existing server definitions and the chosen HTTP/shutdown ports
        Set<Integer> portsToAvoid = new HashSet<>(existingPorts);
        portsToAvoid.add(config.port);              // Don't use same as HTTP port
        portsToAvoid.add(config.shutdownPort);      // Don't use same as shutdown port
        config.monitoring.jmx.port = findAvailablePortAvoidingExisting(8999, 8000, 8999, portsToAvoid);
        
        return config;
    }
    
    /**
     * Assign a shutdown port to config if not already set.
     * Tries the default (HTTP port + 1000) first, but if that conflicts with
     * existing servers, finds an available port in the 9000-9999 range.
     */
    private static void assignShutdownPortIfNeeded(ServerConfig config) {
        Path lucliHome = getLucliHome();
        Path serversDir = lucliHome.resolve("servers");
        Set<Integer> existingPorts = getExistingServerPorts(serversDir);
        
        // Try default shutdown port (HTTP + 1000)
        int preferredShutdownPort = getShutdownPort(config.port);
        if (!existingPorts.contains(preferredShutdownPort) && isPortAvailable(preferredShutdownPort)) {
            config.shutdownPort = preferredShutdownPort;
            return;
        }
        
        // If default is taken, find an available port in the 9000-9999 range
        // Avoid ports being used by existing servers
        for (int port = 9000; port <= 9999; port++) {
            if (!existingPorts.contains(port) && isPortAvailable(port)) {
                config.shutdownPort = port;
                return;
            }
        }
        
        // If we exhaust the range, use system-assigned port
        try (ServerSocket socket = new ServerSocket(0)) {
            config.shutdownPort = socket.getLocalPort();
        } catch (IOException e) {
            // Fallback to HTTP + 1000 even if it might conflict
            config.shutdownPort = preferredShutdownPort;
        }
    }
    
    /**
     * Get LuCLI home directory
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
     * Get all ports currently defined in existing server configurations
     */
    private static Set<Integer> getExistingServerPorts(Path serversDir) {
        Set<Integer> ports = new HashSet<>();
        
        if (!Files.exists(serversDir)) {
            return ports;
        }
        
        try (var stream = Files.list(serversDir)) {
            for (Path serverDir : stream.filter(Files::isDirectory).toList()) {
                try {
                    // Look for lucee.json in each server directory
                    Path configFile = serverDir.resolve("lucee.json");
                    if (Files.exists(configFile)) {
                        ServerConfig existingConfig = objectMapper.readValue(configFile.toFile(), ServerConfig.class);
                        
                        // Add HTTP port
                        ports.add(existingConfig.port);
                        
                        // Add shutdown port (either explicit or derived)
                        ports.add(getEffectiveShutdownPort(existingConfig));
                        
                        // Add JMX port if configured
                        if (existingConfig.monitoring != null && existingConfig.monitoring.jmx != null) {
                            ports.add(existingConfig.monitoring.jmx.port);
                        }

                        // Add HTTPS port if enabled
                        if (isHttpsEnabled(existingConfig)) {
                            ports.add(getEffectiveHttpsPort(existingConfig));
                        }
                    }
                } catch (Exception e) {
                    // Skip servers with invalid configurations
                }
            }
        } catch (IOException e) {
            // If we can't read the servers directory, just return empty set
        }
        
        return ports;
    }
    
    /**
     * Find an available port starting from the preferred port, avoiding specific ports
     */
    private static int findAvailablePortAvoidingExisting(int preferredPort, int rangeStart, int rangeEnd, Set<Integer> portsToAvoid) {
        // First try the preferred port if it's not in the avoid list
        if (!portsToAvoid.contains(preferredPort) && isPortAvailable(preferredPort)) {
            return preferredPort;
        }
        
        // Then search in the range
        for (int port = rangeStart; port <= rangeEnd; port++) {
            if (!portsToAvoid.contains(port) && isPortAvailable(port)) {
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
        int shutdownPort = getEffectiveShutdownPort(config);
        int httpsPort = isHttpsEnabled(config) ? getEffectiveHttpsPort(config) : -1;

        if (config.monitoring != null && config.monitoring.jmx != null) {
            if (config.port == config.monitoring.jmx.port) {
                hasConflicts = true;
                conflictMessages.append("HTTP port (").append(config.port)
                        .append(") and JMX port (").append(config.monitoring.jmx.port)
                        .append(") cannot be the same. Please use different ports in your lucee.json file.\n");
            }

            if (shutdownPort == config.monitoring.jmx.port) {
                hasConflicts = true;
                conflictMessages.append("Shutdown port (").append(shutdownPort)
                        .append(") and JMX port (").append(config.monitoring.jmx.port)
                        .append(") cannot be the same. The shutdown port is calculated as HTTP port + 1000. ");
                conflictMessages.append("Please choose a different HTTP port or JMX port in your lucee.json file.\n");
            }
        }

        if (httpsPort > 0) {
            if (httpsPort == config.port) {
                hasConflicts = true;
                conflictMessages.append("HTTPS port (").append(httpsPort)
                        .append(") and HTTP port (").append(config.port)
                        .append(") cannot be the same. Please use different ports in your lucee.json file.\n");
            }
            if (httpsPort == shutdownPort) {
                hasConflicts = true;
                conflictMessages.append("HTTPS port (").append(httpsPort)
                        .append(") and Shutdown port (").append(shutdownPort)
                        .append(") cannot be the same.\n");
            }
            if (config.monitoring != null && config.monitoring.jmx != null && httpsPort == config.monitoring.jmx.port) {
                hasConflicts = true;
                conflictMessages.append("HTTPS port (").append(httpsPort)
                        .append(") and JMX port (").append(config.monitoring.jmx.port)
                        .append(") cannot be the same.\n");
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
        
        // Check shutdown port (either explicit or derived from HTTP port)
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
        if (config.monitoring != null && config.monitoring.jmx != null && config.monitoring.enabled) {
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

        // Check HTTPS port
        if (httpsPort > 0) {
            if (!isPortAvailable(httpsPort)) {
                hasConflicts = true;
                conflictMessages.append("HTTPS port ").append(httpsPort).append(" is already in use");

                if (allowPortReassignment) {
                    int newHttpsPort = findAvailablePort(httpsPort, 8000, 8999);
                    conflictMessages.append(", reassigning to port ").append(newHttpsPort);
                    if (config.https != null) {
                        config.https.port = newHttpsPort;
                    }
                } else {
                    conflictMessages.append(". Please stop the service using this port or choose a different HTTPS port.");
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
     * Get the shutdown port for a given HTTP port.
     *
     * This helper preserves the legacy convention used throughout the codebase
     * and in existing server directories where only the HTTP port is recorded.
     */
    public static int getShutdownPort(int httpPort) {
        return httpPort + 1000;
    }

    /**
     * Get the effective shutdown port for a given server configuration.
     *
     * Precedence:
     *  1. Explicit shutdownPort value from lucee.json when present.
     *  2. Derived value using getShutdownPort(config.port) for backward
     *     compatibility when shutdownPort is absent.
     */
    public static int getEffectiveShutdownPort(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig must not be null");
        }
        if (config.shutdownPort != null) {
            return config.shutdownPort.intValue();
        }
        return getShutdownPort(config.port);
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

    /**
     * Resolve the effective CFConfig JSON for a server configuration.
     *
     * Merges configurations with the following precedence (lowest to highest):
     *  1. External JSON file referenced by {@code configurationFile}, if it exists (base config).
     *  2. Inline {@code configuration} object in lucee.json (overrides).
     *
     * Returns null when no configuration is defined anywhere.
     */
    public static JsonNode resolveConfigurationNode(ServerConfig config, Path projectDir) throws IOException {
        if (config == null) {
            return null;
        }

        JsonNode result = null;

        // Start with external configuration file as base (if specified and exists)
        if (config.configurationFile != null && !config.configurationFile.trim().isEmpty()) {
            Path cfConfigPath = Paths.get(config.configurationFile);
            if (!cfConfigPath.isAbsolute()) {
                cfConfigPath = projectDir.resolve(cfConfigPath);
            }

            if (Files.exists(cfConfigPath)) {
                result = objectMapper.readTree(cfConfigPath.toFile());
            }
        }

        // Merge inline configuration (if present) as overrides
        if (config.configuration != null && !config.configuration.isNull()) {
            if (result == null) {
                // No base config file; use inline config directly
                result = config.configuration;
            } else {
                // Merge inline config into the base; inline values override file values
                result = mergeJsonNodes(result, config.configuration);
            }
        }

        return result;
    }

    /**
     * Deep merge two JSON nodes, with {@code overrides} taking precedence over {@code base}.
     * Modifies {@code base} in place and returns it.
     */
    private static JsonNode mergeJsonNodes(JsonNode base, JsonNode overrides) {
        if (!(base instanceof com.fasterxml.jackson.databind.node.ObjectNode)) {
            // If base is not an object, overrides replaces it entirely
            return overrides;
        }

        com.fasterxml.jackson.databind.node.ObjectNode baseObj = (com.fasterxml.jackson.databind.node.ObjectNode) base;

        if (overrides.isObject()) {
            overrides.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode overrideValue = entry.getValue();

                if (baseObj.has(key) && baseObj.get(key).isObject() && overrideValue.isObject()) {
                    // Recursively merge nested objects
                    mergeJsonNodes(baseObj.get(key), overrideValue);
                } else {
                    // Override or add the value
                    baseObj.set(key, overrideValue);
                }
            });
        }

        return baseObj;
    }

    /**
     * When a CFConfig definition is present in the server configuration, write it to
     * the Lucee context directory as .CFConfig.json. This is a pure side-effect method
     * used during server startup and does nothing when no configuration is defined.
     */
    public static void writeCfConfigIfPresent(ServerConfig config, Path projectDir, Path serverInstanceDir) throws IOException {
        JsonNode cfConfig = resolveConfigurationNode(config, projectDir);
        if (cfConfig == null || cfConfig.isNull()) {
            return; // Nothing to write
        }

        // Ensure lucee-server/context directory exists under this server instance
        Path contextDir = serverInstanceDir.resolve("lucee-server").resolve("context");
        Files.createDirectories(contextDir);

        Path cfConfigPath = contextDir.resolve(".CFConfig.json");
        objectMapper.writeValue(cfConfigPath.toFile(), cfConfig);
    }
}
