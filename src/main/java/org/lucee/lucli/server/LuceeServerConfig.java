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

import org.lucee.lucli.secrets.LocalSecretStore;
import org.lucee.lucli.secrets.SecretStore;
import org.lucee.lucli.secrets.SecretStoreException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Handles Lucee server configuration from lucee.json files
 */
public class LuceeServerConfig {
    
    @JsonIgnoreProperties({"dependencies", "devDependencies", "packages", "dependencySettings"})
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
        /**
         * Optional AJP (Apache JServ Protocol) connector configuration.
         * When omitted or when enabled=false, LuCLI will not configure an AJP connector.
         */
        public AjpConfig ajp = new AjpConfig();
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

        /**
         * Optional path to a project-specific environment file. When set, this
         * file is loaded before performing ${VAR} substitution in lucee.json.
         * Relative paths are resolved against the project directory. When not
         * specified, LuCLI defaults to `.env` in the project directory.
         */
        public String envFile;

        /**
         * Additional environment variables to expose to the Tomcat process when
         * starting the server. Values support the same ${VAR} and
         * ${VAR:-default} syntax as the rest of lucee.json and are resolved
         * against the combined .env + system environment.
         */
        public Map<String, String> envVars = new HashMap<>();
        
        /**
         * Optional environment-specific configuration overrides.
         * Each key is an environment name (e.g., "prod", "dev", "staging") and
         * the value is a ServerConfig object containing overrides for that environment.
         * Use `lucli server start --env prod` to apply the "prod" environment overrides.
         */
        public Map<String, ServerConfig> environments = new HashMap<>();

        /**
         * Optional runtime configuration describing how/where the server runs
         * (lucee-express, tomcat, docker, etc.). When null or when type is
         * omitted, LuCLI defaults to a Lucee Express runtime.
         */
        public RuntimeConfig runtime;
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
    
    public static class AjpConfig {
        public boolean enabled = false;
        /**
         * Optional AJP port. When null, defaults to 8009.
         */
        public Integer port;
    }

    public static class AdminConfig {
        public boolean enabled = true;
        public String password = "";
    }
    
    public static class UrlRewriteConfig {
        public boolean enabled = true;
        public String routerFile = "index.cfm";
        /**
         * Path to the urlrewrite.xml config file in the project.
         * Relative paths are resolved against the project directory.
         * Defaults to "urlrewrite.xml" in project root.
         */
        public String configFile = "urlrewrite.xml";
    }
    
    public static class MonitoringConfig {
        public boolean enabled = false;
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
    
    /**
     * Runtime configuration describing how and where the server is run.
     * This is a direct mapping of the "runtime" section documented in
     * documentation/todo/RUNTIME.md and is intentionally permissive for
     * future backends.
     */
    public static class RuntimeConfig {
        // Common fields
        public String type;         // "lucee-express" | "tomcat" | "docker" (others later)
        public String installPath;  // Host path for lucee-express/tomcat; in-container path for docker

        // Lucee Express–specific
        public String variant;      // "standard" (default), "light", "zero"
        public Boolean shared;      // false = per-project, true = shared install

        // Tomcat-specific (first-pass wiring only)
        public String catalinaHome;
        public String catalinaBase;
        @Deprecated
        public String webappsDir; 
        @Deprecated
        public String contextPath;

        // Docker-specific (configuration only for now)
        public String image;
        public String dockerfile;
        public String context;
        public String tag;
        public String containerName;
        public String runMode;      // "mount" | "copy"
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
        return loadConfig(projectDir, "lucee.json");
    }
    
    /**
     * Load configuration from a specified file in the directory
     * @param projectDir The project directory
     * @param configFileName The configuration file name (e.g., "lucee.json", "lucee-simple.json")
     */
    public static ServerConfig loadConfig(Path projectDir, String configFileName) throws IOException {
        // Reset any previously loaded .env variables for this new project/config load
        envVariables.clear();

        Path configFile = projectDir.resolve(configFileName);
        
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

        // Load environment variables from the configured envFile (or default .env)
        String envFileName = (config.envFile == null || config.envFile.trim().isEmpty())
                ? ".env"
                : config.envFile.trim();
        loadEnvFile(projectDir, envFileName);

        // Perform environment variable substitution on string fields
        // Supports ${VAR_NAME} and ${VAR_NAME:-default_value}
        substituteEnvironmentVariables(config);
        
        // NOTE: Secret placeholders (${secret:NAME}) are NOT resolved automatically here
        // anymore. This prevents read-only commands (status, stop, list, config get, etc.)
        // from prompting for the secrets passphrase. Callers that actually need the
        // resolved secrets (e.g. when starting a server or locking config) must
        // explicitly call resolveSecretPlaceholders(config, projectDir).
        
        // Ensure monitoring is initialized
        if (config.monitoring == null) {
            config.monitoring = new MonitoringConfig();
        }
        
        // Ensure AJP is initialized for backward compatibility with older configs
        if (config.ajp == null) {
            config.ajp = new AjpConfig();
        }
        
        // Assign default ports if not explicitly set, checking for conflicts with existing servers
        assignDefaultPortsIfNeeded(config);
        
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
     * Resolve the effective runtime configuration for a server.
     *
     * Behaviour:
     * - When {@code config.runtime} is null, a new RuntimeConfig is created.
     * - When {@code runtime.type} is null/blank, it defaults to "lucee-express".
     * - Some fields have lightweight defaults applied (e.g. shared=false,
     *   webappsDir="webapps", runMode="mount").
     *
     * The returned instance is also assigned back to {@code config.runtime}
     * so that downstream callers can rely on non-null runtime configuration.
     */
    public static RuntimeConfig getEffectiveRuntime(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ServerConfig must not be null");
        }

        RuntimeConfig runtime = config.runtime != null ? config.runtime : new RuntimeConfig();

        if (runtime.type == null || runtime.type.trim().isEmpty()) {
            runtime.type = "lucee-express";
        }

        // Default shared flag for lucee-express runtime
        if (runtime.shared == null) {
            runtime.shared = Boolean.FALSE;
        }

        // Default Tomcat webappsDir
        if (runtime.webappsDir == null || runtime.webappsDir.trim().isEmpty()) {
            runtime.webappsDir = "webapps";
        }

        // Default Docker runMode for dev-like usage
        if (runtime.runMode == null || runtime.runMode.trim().isEmpty()) {
            runtime.runMode = "mount";
        }

        config.runtime = runtime;
        return runtime;
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
     * Load environment variables from an env file in the project directory.
     * The env file is typically `.env` and should be in the same directory as lucee.json.
     * Lines starting with # are treated as comments.
     * Supports KEY=VALUE format, including quoted values.
     * Environment variables in the file take precedence over system env vars for
     * configuration substitution. Loaded values are also available to server
     * processes via {@link #applyLoadedEnvToProcessEnvironment(Map)}.
     */
    private static void loadEnvFile(Path projectDir, String envFileName) {
        if (envFileName == null || envFileName.trim().isEmpty()) {
            return; // No env file configured
        }

        Path envFilePath = Paths.get(envFileName);
        if (!envFilePath.isAbsolute()) {
            envFilePath = projectDir.resolve(envFileName);
        }

        if (!Files.exists(envFilePath)) {
            return; // No env file, skip
        }
        
        try {
            java.util.List<String> lines = Files.readAllLines(envFilePath);
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
    
    // Map to store environment variables loaded from .env / envFile for the current config.
    // This map is cleared at the start of each loadConfig call.
    private static final Map<String, String> envVariables = new java.util.HashMap<>();
    
    /**
     * Apply the currently loaded .env / envFile variables into a target process
     * environment map (e.g. ProcessBuilder.environment()).
     *
     * This only sets variables that are not already present in the target
     * environment, so explicit OS environment variables and values provided via
     * envVars in lucee.json take precedence.
     */
    public static void applyLoadedEnvToProcessEnvironment(Map<String, String> targetEnv) {
        if (targetEnv == null) {
            return;
        }
        for (Map.Entry<String, String> entry : envVariables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null) {
                continue;
            }
            targetEnv.putIfAbsent(key, value);
        }
    }
    
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
        if (config.envFile != null) {
            config.envFile = replaceEnvVars(config.envFile);
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
        
        // Substitute in Admin config
        if (config.admin != null) {
            if (config.admin.password != null) {
                config.admin.password = replaceEnvVars(config.admin.password);
            }
        }

        // Substitute in envVars map (values only)
        if (config.envVars != null && !config.envVars.isEmpty()) {
            Map<String, String> resolved = new HashMap<>();
            for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                resolved.put(key, value != null ? replaceEnvVars(value) : null);
            }
            config.envVars = resolved;
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
     * Replace secret placeholders in string using ${secret:NAME} syntax.
     * This is evaluated after environment variables, and only for the local provider
     * in this initial implementation.
     */
    private static String replaceSecretPlaceholders(String value, SecretStore store) throws SecretStoreException {
        if (value == null) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{secret:([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            java.util.Optional<char[]> secret = store.get(name);
            if (secret.isEmpty()) {
                throw new SecretStoreException("Secret '" + name + "' not found for placeholder in configuration");
            }
            String replacement = new String(secret.get());
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Quick scan to see if any ${secret:...} placeholders are present in fields we support.
     */
    private static boolean hasSecretPlaceholders(ServerConfig config) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{secret:([^}]+)\\}");

        if (config.admin != null && config.admin.password != null) {
            if (pattern.matcher(config.admin.password).find()) {
                return true;
            }
        }
        if (config.configuration != null) {
            return jsonNodeHasSecretPlaceholders(config.configuration, pattern);
        }
        return false;
    }

    private static boolean jsonNodeHasSecretPlaceholders(com.fasterxml.jackson.databind.JsonNode node, java.util.regex.Pattern pattern) {
        if (node == null) {
            return false;
        }
        if (node.isTextual()) {
            return pattern.matcher(node.asText()).find();
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                if (jsonNodeHasSecretPlaceholders(element, pattern)) {
                    return true;
                }
            }
            return false;
        } else if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = node.fields();
            while (it.hasNext()) {
                if (jsonNodeHasSecretPlaceholders(it.next().getValue(), pattern)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * Resolve ${secret:...} placeholders across relevant config fields.
     *
     * Behaviour:
     * - If there are no ${secret:...} placeholders, this is a no-op.
     * - If placeholders exist but no passphrase can be obtained (env var or console),
     *   an IOException is thrown so the caller sees a clear failure.
     *
     * This method is intentionally public so that only commands that actually
     * need secrets (e.g. server start, server lock) pay the cost of prompting
     * for the secret store passphrase.
     */
    public static void resolveSecretPlaceholders(ServerConfig config, Path projectDir) throws IOException {
        if (!hasSecretPlaceholders(config)) {
            return; // nothing to do
        }

        Path storePath = getLucliHome().resolve("secrets").resolve("local.json");
        if (!Files.exists(storePath)) {
            throw new IOException("Configuration references ${secret:...} but local secret store does not exist. Run 'lucli secrets init' and define the required secrets.");
        }

        // Prefer non-interactive passphrase from environment when available
        char[] passphrase = null;
        String envPass = System.getenv("LUCLI_SECRETS_PASSPHRASE");
        if (envPass != null && !envPass.isEmpty()) {
            passphrase = envPass.toCharArray();
        } else if (System.console() != null) {
            passphrase = System.console().readPassword("Enter secrets passphrase to unlock config secrets: ");
        }

        if (passphrase == null || passphrase.length == 0) {
            throw new IOException("Configuration requires secrets but no passphrase is available. Set LUCLI_SECRETS_PASSPHRASE or run with an interactive console.");
        }

        SecretStore store;
        try {
            store = new LocalSecretStore(storePath, passphrase);
        } catch (SecretStoreException e) {
            throw new IOException("Failed to open local secret store: " + e.getMessage(), e);
        }

        try {
            if (config.admin != null && config.admin.password != null) {
                config.admin.password = replaceSecretPlaceholders(config.admin.password, store);
            }
            if (config.configuration != null) {
                config.configuration = substituteSecretsInJsonNode(config.configuration, store);
            }
        } catch (SecretStoreException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode substituteSecretsInJsonNode(com.fasterxml.jackson.databind.JsonNode node, SecretStore store) throws SecretStoreException {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String replaced = replaceSecretPlaceholders(node.asText(), store);
            return objectMapper.getNodeFactory().textNode(replaced);
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.getNodeFactory().arrayNode();
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                arrayNode.add(substituteSecretsInJsonNode(element, store));
            }
            return arrayNode;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objNode = objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                try {
                    objNode.set(entry.getKey(), substituteSecretsInJsonNode(entry.getValue(), store));
                } catch (SecretStoreException e) {
                    throw new RuntimeException(e);
                }
            });
            return objNode;
        } else {
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
        
        // Note: JMX port is not assigned here because monitoring.enabled defaults to false.
        // If user explicitly enables monitoring in lucee.json, the JMX port (8999 by default)
        // will be available. We avoid assigning JMX port by default to prevent port conflicts.
        
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
     * Assign default ports to config if not already set.
     *
     * Behaviour:
     *  - If an explicit HTTP port is configured in lucee.json (non-zero), we ALWAYS
     *    honour that value, even if it is currently in use. Port availability
     *    conflicts are detected later by {@link #resolvePortConflicts} so we can
     *    give detailed diagnostics and point at the owning LuCLI server.
     *  - If no HTTP port is configured (port == 0), we assign a sensible default
     *    starting from 8080, using the same "next available" logic as for
     *    freshly created configs.
     *  - The shutdown port is auto-assigned when not explicitly set.
     */
    private static void assignDefaultPortsIfNeeded(ServerConfig config) {
        // If no HTTP port has been configured at all, pick a default using the
        // same strategy as createDefaultConfig (avoid existing server ports and
        // prefer 8080 when possible).
        if (config.port == 0) {
            Path lucliHome = getLucliHome();
            Path serversDir = lucliHome.resolve("servers");
            Set<Integer> existingPorts = getExistingServerPorts(serversDir);
            config.port = findAvailablePortAvoidingExisting(8080, 8000, 8999, existingPorts);
        }
        
        // Assign shutdown port if not explicitly set
        if (config.shutdownPort == null) {
            assignShutdownPortIfNeeded(config);
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
     *  3. Environment-specific configuration (if applied via applyEnvironment).
     *  4. Dependency mappings from lucee-lock.json (highest precedence).
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
        
        // Add dependency mappings as final layer (highest precedence)
        JsonNode dependencyMappings = generateDependencyMappings(projectDir);
        if (dependencyMappings != null) {
            if (result == null) {
                // No other config; create new object with just mappings
                result = objectMapper.createObjectNode();
            }
            result = mergeMappings(result, dependencyMappings);
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
     * Apply environment-specific overrides to a base ServerConfig.
     * 
     * @param base The base ServerConfig loaded from lucee.json
     * @param envName The environment name to apply (e.g., "prod", "dev", "staging")
     * @return A new ServerConfig with environment overrides deep-merged into the base
     * @throws IllegalArgumentException if the environment name is not found
     */
    public static ServerConfig applyEnvironment(ServerConfig base, String envName) {
        return applyEnvironment(base, envName, null);
    }
    
    /**
     * Apply environment-specific overrides to a base ServerConfig.
     * 
     * @param base The base ServerConfig loaded from lucee.json
     * @param envName The environment name to apply (e.g., "prod", "dev", "staging")
     * @param projectDir The project directory (used to read raw environment JSON)
     * @return A new ServerConfig with environment overrides deep-merged into the base
     * @throws IllegalArgumentException if the environment name is not found
     */
    public static ServerConfig applyEnvironment(ServerConfig base, String envName, Path projectDir) {
        if (envName == null || envName.trim().isEmpty()) {
            return base; // No environment specified
        }
        
        if (base.environments == null || !base.environments.containsKey(envName)) {
            // Build a helpful error message listing available environments
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Environment '").append(envName).append("' not found in lucee.json");
            
            if (base.environments != null && !base.environments.isEmpty()) {
                errorMsg.append("\nAvailable environments: ");
                errorMsg.append(String.join(", ", base.environments.keySet()));
            } else {
                errorMsg.append("\nNo environments are defined in lucee.json");
            }
            
            throw new IllegalArgumentException(errorMsg.toString());
        }
        
        // Prevent recursive environment definitions
        ServerConfig envOverrides = base.environments.get(envName);
        if (envOverrides.environments != null && !envOverrides.environments.isEmpty()) {
            throw new IllegalArgumentException(
                "Environment '" + envName + "' cannot contain nested 'environments' definitions"
            );
        }
        
        try {
            // Try to read raw environment JSON from file to get only explicitly set fields
            JsonNode rawEnvNode = null;
            if (projectDir != null) {
                Path configFile = projectDir.resolve("lucee.json");
                if (Files.exists(configFile)) {
                    JsonNode rootNode = objectMapper.readTree(configFile.toFile());
                    JsonNode envsNode = rootNode.get("environments");
                    if (envsNode != null && envsNode.has(envName)) {
                        rawEnvNode = envsNode.get(envName);
                    }
                }
            }
            
            return deepMergeConfigs(base, rawEnvNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge environment configuration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deep merge two ServerConfig objects using Jackson's ObjectMapper.
     * Uses JSON serialization/deserialization to perform the merge, which handles
     * all nested objects automatically.
     * 
     * @param base The base configuration
     * @param overrides The override configuration  
     * @return A new ServerConfig with overrides merged into base
     */
    /**
     * Deep merge using raw JSON node for overrides.
     * This ensures only fields explicitly present in the environment JSON are merged,
     * rather than default values from deserialized objects.
     */
    private static ServerConfig deepMergeConfigs(ServerConfig base, JsonNode rawOverrideNode) throws IOException {
        // Convert base config to JSON (include all values)
        JsonNode baseNode = objectMapper.valueToTree(base);
        
        if (rawOverrideNode == null || rawOverrideNode.isNull() || rawOverrideNode.isEmpty()) {
            // No overrides to apply
            return base;
        }
        
        // Make a copy to avoid modifying the original
        JsonNode overrideNode = rawOverrideNode.deepCopy();
        
        // Remove 'environments' from the override node to prevent copying
        if (overrideNode.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) overrideNode).remove("environments");
        }
        
        // Merge the JSON nodes
        JsonNode merged = mergeJsonNodes(baseNode.deepCopy(), overrideNode);
        
        // Convert back to ServerConfig
        return objectMapper.treeToValue(merged, ServerConfig.class);
    }
    
    /**
     * Generate CFConfig mappings from both declared (lucee.json) and installed (lucee-lock.json) dependencies.
     * Returns a JsonNode with a "mappings" object containing dependency mappings.
     * 
     * Mapping precedence (lowest to highest):
     * 1. Declared dependencies in lucee.json (computed mappings)
     * 2. Installed dependencies in lucee-lock.json (actual mappings, overrides declared)
     */
    private static JsonNode generateDependencyMappings(Path projectDir) throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode mappingsNode = objectMapper.createObjectNode();
        
        // First, compute mappings from declared dependencies in lucee.json
        addDeclaredDependencyMappings(projectDir, mappingsNode);
        
        // Then, add/override with installed dependencies from lucee-lock.json
        addInstalledDependencyMappings(projectDir, mappingsNode);
        
        if (mappingsNode.size() == 0) {
            return null; // No mappings to add
        }
        
        // Wrap in a configuration object
        com.fasterxml.jackson.databind.node.ObjectNode configNode = objectMapper.createObjectNode();
        configNode.set("mappings", mappingsNode);
        return configNode;
    }
    
    /**
     * Add computed mappings from declared dependencies in lucee.json.
     * These are the expected mappings based on the dependency configuration,
     * even if the dependencies haven't been installed yet.
     */
    private static void addDeclaredDependencyMappings(
            Path projectDir, 
            com.fasterxml.jackson.databind.node.ObjectNode mappingsNode) throws IOException {
        
        Path luceeJsonPath = projectDir.resolve("lucee.json");
        if (!Files.exists(luceeJsonPath)) {
            return; // No lucee.json to read from
        }
        
        try {
            // Parse lucee.json and extract dependencies
            org.lucee.lucli.config.LuceeJsonConfig config = 
                org.lucee.lucli.config.LuceeJsonConfig.load(projectDir);
            
            // Process production dependencies
            for (org.lucee.lucli.config.DependencyConfig dep : config.parseDependencies()) {
                if (dep.getMapping() != null && dep.getInstallPath() != null) {
                    addComputedMapping(mappingsNode, dep, projectDir);
                }
            }
            
            // Process dev dependencies
            for (org.lucee.lucli.config.DependencyConfig dep : config.parseDevDependencies()) {
                if (dep.getMapping() != null && dep.getInstallPath() != null) {
                    addComputedMapping(mappingsNode, dep, projectDir);
                }
            }
        } catch (Exception e) {
            // If we can't parse lucee.json, just skip declared mappings
            // This is not a fatal error - we can still work with lock file mappings
        }
    }
    
    /**
     * Add mappings from installed dependencies in lucee-lock.json.
     * These override any declared mappings from lucee.json.
     */
    private static void addInstalledDependencyMappings(
            Path projectDir,
            com.fasterxml.jackson.databind.node.ObjectNode mappingsNode) throws IOException {
        
        Path lockFilePath = projectDir.resolve("lucee-lock.json");
        if (!Files.exists(lockFilePath)) {
            return; // No lock file, skip installed mappings
        }
        
        // Read lock file
        JsonNode lockFile = objectMapper.readTree(lockFilePath.toFile());
        JsonNode dependencies = lockFile.get("dependencies");
        JsonNode devDependencies = lockFile.get("devDependencies");
        
        // Process production dependencies
        if (dependencies != null && dependencies.isObject()) {
            dependencies.fields().forEachRemaining(entry -> {
                String depName = entry.getKey();
                JsonNode dep = entry.getValue();
                addDependencyMapping(mappingsNode, depName, dep, projectDir);
            });
        }
        
        // Process dev dependencies
        if (devDependencies != null && devDependencies.isObject()) {
            devDependencies.fields().forEachRemaining(entry -> {
                String depName = entry.getKey();
                JsonNode dep = entry.getValue();
                addDependencyMapping(mappingsNode, depName, dep, projectDir);
            });
        }
    }
    
    /**
     * Add a computed mapping from a DependencyConfig object.
     * Used for declared dependencies in lucee.json.
     */
    private static void addComputedMapping(
            com.fasterxml.jackson.databind.node.ObjectNode mappingsNode,
            org.lucee.lucli.config.DependencyConfig dep,
            Path projectDir) {
        
        String virtualPath = dep.getMapping();
        String installPath = dep.getInstallPath();
        
        if (virtualPath == null || installPath == null) {
            return;
        }
        
        // Resolve to absolute path
        Path physicalPath = Paths.get(installPath);
        if (!physicalPath.isAbsolute()) {
            physicalPath = projectDir.resolve(installPath).toAbsolutePath().normalize();
        }
        
        // Create CFConfig mapping object
        com.fasterxml.jackson.databind.node.ObjectNode mappingObj = objectMapper.createObjectNode();
        mappingObj.put("physical", physicalPath.toString());
        mappingObj.put("archive", "");
        mappingObj.put("primary", "physical");
        mappingObj.put("inspectTemplate", "once");
        mappingObj.put("readonly", "no");
        mappingObj.put("listenerMode", "modern");
        mappingObj.put("listenerType", "curr2root");
        
        // Add to mappings with virtual path as key
        // Ensure virtual path ends with / for consistency
        String mappingKey = virtualPath.endsWith("/") ? virtualPath : virtualPath + "/";
        mappingsNode.set(mappingKey, mappingObj);
    }
    
    /**
     * Add a single dependency mapping to the mappings node
     */
    private static void addDependencyMapping(
            com.fasterxml.jackson.databind.node.ObjectNode mappingsNode, 
            String depName, 
            JsonNode dep,
            Path projectDir) {
        
        JsonNode mappingNode = dep.get("mapping");
        JsonNode installPathNode = dep.get("installPath");
        
        if (mappingNode == null || mappingNode.isNull() || 
            installPathNode == null || installPathNode.isNull()) {
            return; // No mapping defined for this dependency
        }
        
        String virtualPath = mappingNode.asText();
        String installPath = installPathNode.asText();
        
        // Resolve to absolute path
        Path physicalPath = Paths.get(installPath);
        if (!physicalPath.isAbsolute()) {
            physicalPath = projectDir.resolve(installPath).toAbsolutePath().normalize();
        }
        
        // Create CFConfig mapping object
        com.fasterxml.jackson.databind.node.ObjectNode mappingObj = objectMapper.createObjectNode();
        mappingObj.put("physical", physicalPath.toString());
        mappingObj.put("archive", "");
        mappingObj.put("primary", "physical");
        mappingObj.put("inspectTemplate", "once");
        mappingObj.put("readonly", "no");
        mappingObj.put("listenerMode", "modern");
        mappingObj.put("listenerType", "curr2root");
        
        // Add to mappings with virtual path as key
        // Ensure virtual path ends with / for consistency
        String mappingKey = virtualPath.endsWith("/") ? virtualPath : virtualPath + "/";
        mappingsNode.set(mappingKey, mappingObj);
    }
    
    /**
     * Merge dependency mappings into existing configuration.
     * Dependency mappings override any existing mappings with the same virtual path.
     */
    private static JsonNode mergeMappings(JsonNode base, JsonNode dependencyConfig) {
        if (!(base instanceof com.fasterxml.jackson.databind.node.ObjectNode)) {
            return dependencyConfig; // Can't merge into non-object
        }
        
        com.fasterxml.jackson.databind.node.ObjectNode baseObj = 
            (com.fasterxml.jackson.databind.node.ObjectNode) base;
        
        JsonNode depMappings = dependencyConfig.get("mappings");
        if (depMappings == null || !depMappings.isObject()) {
            return base; // Nothing to merge
        }
        
        // Get or create mappings node in base
        JsonNode baseMappings = baseObj.get("mappings");
        com.fasterxml.jackson.databind.node.ObjectNode mappingsObj;
        
        if (baseMappings == null || !baseMappings.isObject()) {
            // Create new mappings node
            mappingsObj = objectMapper.createObjectNode();
            baseObj.set("mappings", mappingsObj);
        } else {
            mappingsObj = (com.fasterxml.jackson.databind.node.ObjectNode) baseMappings;
        }
        
        // Add/override with dependency mappings
        depMappings.fields().forEachRemaining(entry -> {
            mappingsObj.set(entry.getKey(), entry.getValue());
        });
        
        return baseObj;
    }
    
    /**
     * Resolve the effective CFConfig JSON for a specific server instance directory,
     * taking into account any existing .CFConfig.json file.
     *
     * This mirrors the behaviour of {@link #writeCfConfigIfPresent} but returns the
     * merged JsonNode instead of writing it. The merge rules are:
     *   - Objects (structs) are deep-merged, with override values winning.
     *   - Scalars (strings, numbers, booleans, null) from the override replace existing values.
     *   - Arrays from the override replace any existing arrays at the same path.
     *
     * If {@code arrayOverridePaths} is non-null and an existing .CFConfig.json file is
     * present, any JSON paths where the override contains arrays that will replace
     * existing arrays are recorded into that list.
     */
    public static JsonNode resolveEffectiveCfConfigForContext(
            ServerConfig config,
            Path projectDir,
            Path serverInstanceDir,
            java.util.List<String> arrayOverridePaths) throws IOException {
        JsonNode cfConfig = resolveConfigurationNode(config, projectDir);
        if (cfConfig == null || cfConfig.isNull()) {
            return null;
        }
        
        Path cfConfigPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        
        if (!Files.exists(cfConfigPath)) {
            // No existing file – nothing to merge.
            return cfConfig;
        }
        
        JsonNode existing = objectMapper.readTree(cfConfigPath.toFile());
        if (existing == null || existing.isNull()) {
            return cfConfig;
        }
        
        if (arrayOverridePaths != null) {
            trackArrayOverridePaths(cfConfig, "", arrayOverridePaths);
        }
        
        // Deep merge existing with overrides from lucee.json, following mergeJsonNodes rules.
        return mergeJsonNodes(existing.deepCopy(), cfConfig);
    }
    
    /**
     * When a CFConfig definition is present in the server configuration, write it to
     * the Lucee context directory as .CFConfig.json. This is a pure side-effect method
     * used during server startup and does nothing when no configuration is defined.
     *
     * Behaviour when .CFConfig.json already exists:
     *   - The existing file is loaded as the base configuration.
     *   - The resolved CFConfig from lucee.json is deep-merged on top, with
     *     structures and simple values overriding existing values while
     *     preserving any keys not mentioned in lucee.json.
     *   - Array values from lucee.json replace any existing arrays entirely
     *     rather than being merged. Paths where array overrides occur are
     *     printed to stdout so users can review them.
     */
    public static void writeCfConfigIfPresent(ServerConfig config, Path projectDir, Path serverInstanceDir) throws IOException {
        java.util.List<String> arrayPaths = new java.util.ArrayList<>();
        JsonNode finalConfig = resolveEffectiveCfConfigForContext(config, projectDir, serverInstanceDir, arrayPaths);
        if (finalConfig == null || finalConfig.isNull()) {
            return; // Nothing to write
        }

        // Ensure lucee-server/context directory exists under this server instance
        Path contextDir = serverInstanceDir.resolve("lucee-server").resolve("context");
        Files.createDirectories(contextDir);

        Path cfConfigPath = contextDir.resolve(".CFConfig.json");

        // Only log array overrides when we actually merged into an existing file.
        if (!arrayPaths.isEmpty() && Files.exists(cfConfigPath)) {
            System.out.println("⚠️  CFConfig merge applied array overrides at: " + String.join(", ", arrayPaths));
            System.out.println("    Existing arrays at these paths were replaced rather than merged.");
        }

        objectMapper.writeValue(cfConfigPath.toFile(), finalConfig);
    }

    /**
     * Recursively record JSON paths where the override configuration contains
     * array values. These are the locations where merges will replace existing
     * arrays entirely rather than merging element-by-element.
     */
    private static void trackArrayOverridePaths(JsonNode node, String currentPath, java.util.List<String> paths) {
        if (node == null) {
            return;
        }

        if (node.isArray()) {
            // Record the path to this array; root arrays are recorded as "$".
            String path = currentPath == null || currentPath.isEmpty() ? "$" : currentPath;
            paths.add(path);
            // Still walk children in case of nested arrays/objects
            int index = 0;
            for (JsonNode element : node) {
                String childPath = path + "[" + index + "]";
                trackArrayOverridePaths(element, childPath, paths);
                index++;
            }
        } else if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                JsonNode child = entry.getValue();
                String childPath;
                if (currentPath == null || currentPath.isEmpty()) {
                    childPath = key;
                } else {
                    childPath = currentPath + "." + key;
                }
                trackArrayOverridePaths(child, childPath, paths);
            }
        }
    }
}
