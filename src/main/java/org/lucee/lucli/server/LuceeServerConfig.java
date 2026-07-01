    package org.lucee.lucli.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.lucee.lucli.LuceeScriptEngine;
import org.lucee.lucli.secrets.LucliSecretProviderSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonParser;

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

        /**
         * @deprecated Top-level "version" now refers to the app/project version.
         * Lucee engine version should be specified under the "lucee" block:
         * {@code "lucee": { "version": "7.0.4.34" }}.
         * This field is kept for backward-compatible deserialization of legacy
         * lucee.json files. Use {@link LuceeServerConfig#getLuceeVersion(ServerConfig)}
         * to obtain the effective Lucee engine version.
         */
        @Deprecated
        public String version = "1.0.0";

        /**
         * Lucee engine configuration (version and variant).
         * New-format lucee.json files should use this block:
         * {@code "lucee": { "version": "7.0.4.34", "variant": "standard" }}
         */
        public LuceeEngineConfig lucee;
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
         * Optional Lucee server configuration to be written to the runtime's
         * effective Lucee context .CFConfig.json location.
         * When present, this JSON is treated as the source of truth for CFConfig.
         */
        public JsonNode configuration;

        /**
         * Optional path to an external CFConfig JSON file. Relative paths are resolved
         * against the project directory when starting the server.
         */
        public String configurationFile;

        /**
         * Internal-only path to the base configurationFile when environment overrides
         * specify their own configurationFile. Used to preserve base->env layering.
         */
        @JsonIgnore
        public String baseConfigurationFile;

        /**
         * Optional path to a project-specific environment file. When set, this
         * file is loaded before performing variable substitution in lucee.json.
         * Relative paths are resolved against the project directory. When not
         * specified, LuCLI defaults to `.env` in the project directory.
         *
         * Supports #env:VAR# syntax (preferred). Bare #VAR# and ${VAR} syntax are deprecated.
         */
        public String envFile;

        /**
         * Additional environment variables to expose to the Tomcat process when
         * starting the server. Values support #env:VAR# and #env:VAR:-default#
         * syntax (preferred) and are resolved against the combined .env + system
         * environment. Bare #VAR# and the deprecated ${VAR} syntax still work
         * but will be removed in a future release.
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
        public boolean enabled = false;
        public String routerFile = "index.cfm";
        /**
         * Path to the rewrite.config file in the project.
         * Relative paths are resolved against the project directory.
         * Defaults to "rewrite.config" in project root.
         * Uses Tomcat RewriteValve mod_rewrite syntax.
         */
        public String configFile = "rewrite.config";
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
        public String type;         // "lucee-express" | "tomcat" | "docker" | "jetty"
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

        // Jetty-specific
        public String jettyHome;    // Path to Jetty distribution; falls back to JETTY_HOME env var

        // Docker-specific (configuration only for now)
        public String image;
        public String dockerfile;
        public String context;
        public String tag;
        public String containerName;
        public String runMode;      // "mount" | "copy"
    }
    
    /**
     * Lucee engine configuration — version and JAR variant.
     * This is the new canonical location for Lucee-specific settings
     * that were previously at the top level of lucee.json.
     */
    public static class LuceeEngineConfig {
        /**
         * Lucee engine version (e.g. "7.0.4.34", "7.0.0.346-RC").
         * Classifiers like -RC or -SNAPSHOT are part of this string.
         */
        public String version = "7.0.4.34";

        /**
         * Lucee JAR variant: "standard" (default), "light", or "zero".
         */
        public String variant = "standard";
    }

    public static class AgentConfig {
        public boolean enabled = false;
        public String[] jvmArgs = new String[0];
        public String description;
    }
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
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
        clearLoadedEnvFileVariables();
        clearRealizedEnvVariables();

        Path configFile = projectDir.resolve(configFileName);
        
        if (!Files.exists(configFile)) {
            // Create default configuration
            ServerConfig defaultConfig = createDefaultConfig(projectDir);
            saveConfig(defaultConfig, configFile);
            return defaultConfig;
        }
        
        // Parse as JsonNode first so we can preprocess placeholder-backed numeric fields
        // (e.g. "port": "#env:HTTP_PORT#") before strict int deserialization.
        JsonNode rawConfigNode = objectMapper.readTree(configFile.toFile());

        // Load env vars early so numeric placeholder preprocessing can resolve #env values.
        String initialEnvFileName = resolveConfiguredEnvFileName(rawConfigNode);
        loadEnvFile(projectDir, initialEnvFileName);

        JsonNode preprocessedConfigNode = preprocessNumericFieldsForDeserialization(rawConfigNode);
        ServerConfig config = objectMapper.treeToValue(preprocessedConfigNode, ServerConfig.class);
        
        // Migrate legacy top-level "version" to the new "lucee" block
        migrateLegacyLuceeVersion(config);
        
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
        clearLoadedEnvFileVariables();
        String envFileName = resolveConfiguredEnvFileName(config);
        loadEnvFile(projectDir, envFileName);

        // Perform variable substitution on string fields
        // Supports #env:VAR_NAME# and #env:VAR_NAME:-default# (preferred)
        // Bare #VAR_NAME# and ${VAR_NAME} are deprecated but still work
        substituteEnvironmentVariables(config);
        
        // Resolve relative paths in envVars (e.g. "./distrocore") against the project directory
        // so that the server process receives absolute paths regardless of its working directory.
        resolveRelativeEnvVarPaths(config, projectDir);
        
        // NOTE: Secret placeholders (#secret:NAME# or deprecated ${secret:NAME}) are
        // NOT resolved automatically here. This prevents read-only commands (status,
        // stop, list, config get, etc.) from prompting for the secrets passphrase.
        // Callers that actually need the resolved secrets (e.g. when starting a
        // server or locking config) must explicitly call
        // resolveSecretPlaceholders(config, projectDir).
        
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

    // Pattern matching a Lucee version string: N.N.N.N with optional classifier
    private static final java.util.regex.Pattern LUCEE_VERSION_PATTERN =
            java.util.regex.Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+.*$");
    private static final java.util.regex.Pattern LEADING_NUMERIC_VERSION_PART =
            java.util.regex.Pattern.compile("^(\\d+)");
    public static final String MIN_SUPPORTED_LUCEE_VERSION = "6.1.0.0";
    private static final int MIN_SUPPORTED_LUCEE_MAJOR = 6;
    private static final int MIN_SUPPORTED_LUCEE_MINOR = 1;
    private static final java.util.regex.Pattern INTEGER_LITERAL_PATTERN =
            java.util.regex.Pattern.compile("^[+-]?\\d+$");

    // Track whether we've already shown the deprecation warning for this session
    private static boolean hasShownDeprecationWarning = false;

    // Track whether we've shown the ${VAR} -> #VAR# syntax deprecation warning
    private static boolean hasShownVarSyntaxDeprecation = false;

    // Track whether we've shown the bare #VAR# -> #env:VAR# prefix deprecation warning
    private static boolean hasShownEnvPrefixDeprecation = false;

    /**
     * Migrate legacy top-level "version" (Lucee engine version) into the new
     * {@code "lucee": { "version": ... }} block. This is called during
     * {@link #loadConfig} so that all downstream code can rely on
     * {@link #getLuceeVersion(ServerConfig)}.
     *
     * Rules:
     * 1. If {@code config.lucee} already has a version → nothing to do (new format).
     * 2. If top-level {@code version} looks like a Lucee version (N.N.N.N) and
     *    no {@code lucee} block exists → migrate it and emit a deprecation warning.
     * 3. If both exist, the {@code lucee.version} takes precedence.
     */
    private static void migrateLegacyLuceeVersion(ServerConfig config) {
        if (config.lucee != null && config.lucee.version != null
                && !config.lucee.version.trim().isEmpty()) {
            // New format already present — nothing to migrate.
            return;
        }

        @SuppressWarnings("deprecation")
        String legacyVersion = config.version;
        if (legacyVersion != null && LUCEE_VERSION_PATTERN.matcher(legacyVersion).matches()) {
            // Legacy format detected — migrate
            if (config.lucee == null) {
                config.lucee = new LuceeEngineConfig();
            }
            config.lucee.version = legacyVersion;

            // Migrate runtime.variant into lucee.variant if present
            if (config.runtime != null && config.runtime.variant != null
                    && !config.runtime.variant.trim().isEmpty()
                    && (config.lucee.variant == null || "standard".equals(config.lucee.variant))) {
                config.lucee.variant = config.runtime.variant;
            }

            // Only show deprecation warning once per session to avoid spam
            if (!hasShownDeprecationWarning) {
                System.err.println(
                    "⚠️  Deprecation: top-level \"version\" in lucee.json is deprecated for specifying the Lucee engine version.\n" +
                    "   Please move it to the \"lucee\" block:\n" +
                    "     \"lucee\": { \"version\": \"" + legacyVersion + "\" }\n" +
                    "   The top-level \"version\" key is now reserved for your app/project version.");
                hasShownDeprecationWarning = true;
            }
        }
    }

    /**
     * Get the effective Lucee engine version for a server configuration.
     * Prefers {@code config.lucee.version}, falls back to the deprecated
     * top-level {@code config.version}.
     */
    public static String getLuceeVersion(ServerConfig config) {
        if (config == null) {
            return "7.0.4.34";
        }
        if (config.lucee != null && config.lucee.version != null
                && !config.lucee.version.trim().isEmpty()) {
            return config.lucee.version;
        }
        @SuppressWarnings("deprecation")
        String legacy = config.version;
        if (legacy != null && !legacy.trim().isEmpty() && LUCEE_VERSION_PATTERN.matcher(legacy.trim()).matches()) {
            return legacy.trim();
        }
        return "7.0.4.34";
    }

    /**
     * Get the effective Lucee JAR variant for a server configuration.
     * Prefers {@code config.lucee.variant}, falls back to
     * {@code config.runtime.variant}, then defaults to "standard".
     */
    public static String getLuceeVariant(ServerConfig config) {
        if (config != null && config.lucee != null
                && config.lucee.variant != null && !config.lucee.variant.trim().isEmpty()) {
            return config.lucee.variant;
        }
        if (config != null && config.runtime != null
                && config.runtime.variant != null && !config.runtime.variant.trim().isEmpty()) {
            return config.runtime.variant;
        }
        return "standard";
    }

    /**
     * Set the Lucee engine version on a configuration, writing to the new
     * {@code lucee} block.
     *
     * IMPORTANT: this must not overwrite the top-level {@code version} field.
     * The top-level field is now reserved for app/project versioning.
     */
    public static void setLuceeVersion(ServerConfig config, String version) {
        if (config.lucee == null) {
            config.lucee = new LuceeEngineConfig();
        }
        config.lucee.version = version;
    }

    /**
     * Parse a Lucee version string into numeric parts.
     *
     * Supports classifiers such as "-RC" or "-SNAPSHOT" on any segment by
     * extracting the leading numeric portion of each dot-delimited part.
     *
     * Examples:
     *   - "6.2.4.24"      -> [6,2,4,24]
     *   - "7.0.1.100-RC"  -> [7,0,1,100]
     *   - "6.1"           -> [6,1,0,0]
     */
    private static int[] parseLuceeVersionParts(String version) {
        int[] parts = new int[] {0, 0, 0, 0};
        if (version == null || version.trim().isEmpty()) {
            return parts;
        }

        String[] tokens = version.trim().split("\\.");
        int max = Math.min(tokens.length, 4);
        for (int i = 0; i < max; i++) {
            parts[i] = parseLeadingNumericPart(tokens[i]);
        }
        return parts;
    }

    private static int parseLeadingNumericPart(String token) {
        if (token == null || token.trim().isEmpty()) {
            return 0;
        }
        java.util.regex.Matcher matcher = LEADING_NUMERIC_VERSION_PART.matcher(token.trim());
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Compare only major/minor version segments against a minimum required pair.
     */
    public static boolean isLuceeVersionAtLeast(String version, int requiredMajor, int requiredMinor) {
        int[] parts = parseLuceeVersionParts(version);
        int major = parts[0];
        int minor = parts[1];

        if (major > requiredMajor) {
            return true;
        }
        if (major < requiredMajor) {
            return false;
        }
        return minor >= requiredMinor;
    }

    /**
     * Whether this Lucee version is within LuCLI's supported runtime floor.
     */
    public static boolean isLuceeVersionSupportedForLucli(String version) {
        return isLuceeVersionAtLeast(version, MIN_SUPPORTED_LUCEE_MAJOR, MIN_SUPPORTED_LUCEE_MINOR);
    }

    /**
     * Enforce LuCLI's runtime support floor for Lucee versions.
     */
    public static void validateLuceeVersionSupportForRuntime(String version, String runtimeType) {
        String effectiveVersion = (version == null || version.trim().isEmpty()) ? "unknown" : version.trim();
        String normalizedRuntime = (runtimeType == null || runtimeType.trim().isEmpty())
                ? "lucee-express"
                : runtimeType.trim();

        if (isLuceeVersionSupportedForLucli(effectiveVersion)) {
            return;
        }

        throw new IllegalStateException(
            "❌ Lucee " + effectiveVersion + " is below LuCLI's supported cutoff.\n" +
            "   Runtime: " + normalizedRuntime + "\n" +
            "   Minimum supported Lucee version: " + MIN_SUPPORTED_LUCEE_VERSION + "\n\n" +
            "Versions below 6.1 are not supported because Lucee Express availability\n" +
            "and LuCLI feature/config parity are not guaranteed."
        );
    }

    /**
     * Enforce LuCLI's runtime support floor for the effective version in config.
     */
    public static void validateLuceeVersionSupportForRuntime(ServerConfig config, String runtimeType) {
        validateLuceeVersionSupportForRuntime(getLuceeVersion(config), runtimeType);
    }

    private static boolean hasCfConfigDefinition(ServerConfig config) {
        if (config == null) {
            return false;
        }
        boolean hasInlineConfig = config.configuration != null && !config.configuration.isNull();
        boolean hasConfigFile = config.configurationFile != null && !config.configurationFile.trim().isEmpty();
        return hasInlineConfig || hasConfigFile;
    }

    /**
     * Enforce .CFConfig support cutoff for older Lucee versions.
     */
    public static void validateCfConfigSupport(ServerConfig config) {
        if (!hasCfConfigDefinition(config)) {
            return;
        }

        String effectiveVersion = getLuceeVersion(config);
        if (isLuceeVersionSupportedForLucli(effectiveVersion)) {
            return;
        }

        throw new IllegalStateException(
            "❌ .CFConfig integration is not supported for Lucee " + effectiveVersion + ".\n" +
            "   Minimum supported Lucee version for .CFConfig features: " + MIN_SUPPORTED_LUCEE_VERSION + "\n" +
            "   Remove 'configuration'/'configurationFile' or upgrade to Lucee 6.1+."
        );
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
    // Map tracking variables that were actually realized while resolving #env:...#
    // (or deprecated ${...}) placeholders during configuration substitution.
    // Keys are variable names; values are the resolved values (including defaults when used).
    private static final Map<String, String> realizedEnvVariables = new java.util.LinkedHashMap<>();

    private static void clearLoadedEnvFileVariables() {
        envVariables.clear();
    }

    private static String resolveConfiguredEnvFileName(ServerConfig config) {
        String envFileName = (config == null || config.envFile == null || config.envFile.trim().isEmpty())
                ? ".env"
                : config.envFile.trim();
        return substituteField(envFileName);
    }

    private static String resolveConfiguredEnvFileName(JsonNode configNode) {
        String envFileName = ".env";
        if (configNode != null && configNode.isObject()) {
            JsonNode envFileNode = configNode.get("envFile");
            if (envFileNode != null && envFileNode.isTextual()) {
                String rawValue = envFileNode.asText();
                if (rawValue != null && !rawValue.trim().isEmpty()) {
                    envFileName = rawValue.trim();
                }
            }
        }
        return substituteField(envFileName);
    }

    private static String resolveOptionalConfiguredEnvFileName(JsonNode configNode) {
        if (configNode == null || !configNode.isObject()) {
            return null;
        }
        JsonNode envFileNode = configNode.get("envFile");
        if (envFileNode == null || !envFileNode.isTextual()) {
            return null;
        }
        String rawValue = envFileNode.asText();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        return substituteField(rawValue.trim());
    }

    private static void clearRealizedEnvVariables() {
        synchronized (realizedEnvVariables) {
            realizedEnvVariables.clear();
        }
    }

    private static void recordRealizedEnvVariable(String name, String value) {
        if (name == null || name.trim().isEmpty() || value == null) {
            return;
        }
        synchronized (realizedEnvVariables) {
            realizedEnvVariables.put(name, value);
        }
    }

    /**
     * Returns a snapshot of environment variables that were realized while substituting
     * placeholders in the most recent configuration load/apply flow.
     */
    public static Map<String, String> getRealizedEnvVariables() {
        synchronized (realizedEnvVariables) {
            return new java.util.LinkedHashMap<>(realizedEnvVariables);
        }
    }
    
    /**
     * Apply the currently loaded .env / envFile variables into a target process
     * environment map (e.g. ProcessBuilder.environment()).
     *
     * Values from envFile override any existing entries in {@code targetEnv}
     * (including empty strings set by the parent shell). Explicit
     * {@code envVars} from lucee.json are applied afterward and take final
     * precedence.
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
            targetEnv.put(key, value);
        }
    }

    /**
     * Apply {@code envVars} from config into a target environment map.
     *
     * Semantics:
     * - non-null value: set/override target key
     * - null value: explicitly unset/remove target key
     */
    public static void applyConfigEnvVarsToProcessEnvironment(
            Map<String, String> targetEnv,
            Map<String, String> configuredEnvVars
    ) {
        if (targetEnv == null || configuredEnvVars == null || configuredEnvVars.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : configuredEnvVars.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null) {
                targetEnv.remove(key);
            } else {
                targetEnv.put(key, value);
            }
        }
    }

    /**
     * Reload env variables from the currently effective {@code envFile} for an
     * already materialized config object.
     *
     * This is used for startup flows that may bypass {@link #loadConfig(Path, String)}
     * (for example locked snapshots or in-memory sandbox configs), ensuring runtime
     * process environment is always sourced from the effective envFile/default .env.
     */
    public static void reloadConfiguredEnvFile(ServerConfig config, Path projectDir) {
        clearLoadedEnvFileVariables();
        if (projectDir == null) {
            return;
        }
        String envFileName = resolveConfiguredEnvFileName(config);
        loadEnvFile(projectDir, envFileName);
    }

    /**
     * Reload env variables for server startup while supporting layered env files
     * when an environment-specific override is active.
     *
     * Layering behavior:
     * - Load base envFile first (or default .env)
     * - Then load environments.{env}.envFile (if explicitly configured), allowing
     *   keys in the environment-specific file to override matching base-file keys.
     *
     * If the config file cannot be read, this gracefully falls back to the
     * effective config's envFile/default .env behavior.
     */
    public static void reloadConfiguredEnvFile(
            ServerConfig config,
            Path projectDir,
            String configFileName,
            String environmentName
    ) {
        clearLoadedEnvFileVariables();
        if (projectDir == null) {
            return;
        }

        String resolvedConfigFileName = (configFileName == null || configFileName.trim().isEmpty())
                ? "lucee.json"
                : configFileName.trim();

        try {
            Path configFilePath = projectDir.resolve(resolvedConfigFileName);
            if (!Files.exists(configFilePath)) {
                String envFileName = resolveConfiguredEnvFileName(config);
                loadEnvFile(projectDir, envFileName);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(configFilePath.toFile());
            String baseEnvFileName = resolveConfiguredEnvFileName(rootNode);
            loadEnvFile(projectDir, baseEnvFileName);

            if (environmentName != null && !environmentName.trim().isEmpty()) {
                JsonNode environmentsNode = rootNode.get("environments");
                if (environmentsNode != null && environmentsNode.isObject()) {
                    JsonNode envNode = environmentsNode.get(environmentName.trim());
                    String envOverrideEnvFileName = resolveOptionalConfiguredEnvFileName(envNode);
                    if (envOverrideEnvFileName != null) {
                        loadEnvFile(projectDir, envOverrideEnvFileName);
                    }
                }
            }
        } catch (IOException e) {
            String envFileName = resolveConfiguredEnvFileName(config);
            loadEnvFile(projectDir, envFileName);
        }
    }
    
    /**
     * Get an environment variable, checking .env loaded vars first, then system env vars
     */
    private static String getEnvVar(String name) {
        // Check .env file variables first
        if (envVariables.containsKey(name)) {
            String value = envVariables.get(name);
            if (value != null) {
                recordRealizedEnvVariable(name, value);
            }
            return value;
        }
        // Fall back to system environment variables
        String value = System.getenv(name);
        if (value != null) {
            recordRealizedEnvVariable(name, value);
        }
        return value;
    }

    /**
     * Preprocess numeric fields that are strongly typed as integers in ServerConfig
     * so placeholders like "#env:HTTP_PORT#" can be resolved before Jackson's strict
     * int deserialization.
     */
    private static JsonNode preprocessNumericFieldsForDeserialization(JsonNode rootNode) {
        if (rootNode == null || !rootNode.isObject()) {
            return rootNode;
        }
        JsonNode copy = rootNode.deepCopy();
        preprocessServerConfigNumericFields((com.fasterxml.jackson.databind.node.ObjectNode) copy);
        return copy;
    }

    private static void preprocessServerConfigNumericFields(
            com.fasterxml.jackson.databind.node.ObjectNode configNode) {
        if (configNode == null) {
            return;
        }

        preprocessIntegerField(configNode, "port");
        preprocessIntegerField(configNode, "shutdownPort");

        JsonNode httpsNode = configNode.get("https");
        if (httpsNode != null && httpsNode.isObject()) {
            preprocessIntegerField((com.fasterxml.jackson.databind.node.ObjectNode) httpsNode, "port");
        }

        JsonNode ajpNode = configNode.get("ajp");
        if (ajpNode != null && ajpNode.isObject()) {
            preprocessIntegerField((com.fasterxml.jackson.databind.node.ObjectNode) ajpNode, "port");
        }

        JsonNode monitoringNode = configNode.get("monitoring");
        if (monitoringNode != null && monitoringNode.isObject()) {
            JsonNode jmxNode = monitoringNode.get("jmx");
            if (jmxNode != null && jmxNode.isObject()) {
                preprocessIntegerField((com.fasterxml.jackson.databind.node.ObjectNode) jmxNode, "port");
            }
        }

        JsonNode environmentsNode = configNode.get("environments");
        if (environmentsNode != null && environmentsNode.isObject()) {
            environmentsNode.fields().forEachRemaining(entry -> {
                JsonNode envNode = entry.getValue();
                if (envNode != null && envNode.isObject()) {
                    preprocessServerConfigNumericFields((com.fasterxml.jackson.databind.node.ObjectNode) envNode);
                }
            });
        }
    }

    private static void preprocessIntegerField(
            com.fasterxml.jackson.databind.node.ObjectNode objectNode, String fieldName) {
        if (objectNode == null || fieldName == null) {
            return;
        }
        JsonNode valueNode = objectNode.get(fieldName);
        if (valueNode == null || !valueNode.isTextual()) {
            return;
        }

        String substituted = substituteField(valueNode.asText());
        if (substituted == null) {
            return;
        }

        String trimmed = substituted.trim();
        if (INTEGER_LITERAL_PATTERN.matcher(trimmed).matches()) {
            try {
                objectNode.put(fieldName, Integer.parseInt(trimmed));
                return;
            } catch (NumberFormatException ignored) {
                // Leave as string; normal Jackson validation will report invalid range.
            }
        }

        objectNode.put(fieldName, substituted);
    }
    
    /**
     * Substitute variables in all string fields of the configuration.
     * Supports:
     *   #env:VAR_NAME# - replaced with environment variable value (preferred)
     *   #env:VAR_NAME:-default# - replaced with env var or default if not set (preferred)
     *   #VAR_NAME# - deprecated, still works with a warning (use #env:VAR_NAME# instead)
     *   ${VAR_NAME} - deprecated, still works outside protected zones with a warning
     * 
     * Protected zones where ${VAR} is NOT substituted (left for Lucee/JVM runtime):
     *   - "configuration" block (passed to .CFConfig.json)
     *   - "jvm.additionalArgs" (JVM resolves ${...} at runtime)
     * In these zones, only #env:VAR# / #secret:NAME# syntax is processed.
     */
    private static void substituteEnvironmentVariables(ServerConfig config) {
        // Lucee engine config block
        if (config.lucee != null) {
            if (config.lucee.version != null) {
                config.lucee.version = substituteField(config.lucee.version);
            }
            if (config.lucee.variant != null) {
                config.lucee.variant = substituteField(config.lucee.variant);
            }
        }
        // Legacy top-level version (kept in sync for backward compat)
        if (config.version != null) {
            config.version = substituteField(config.version);
        }
        if (config.name != null) {
            config.name = substituteField(config.name);
        }
        if (config.host != null) {
            config.host = substituteField(config.host);
        }
        if (config.webroot != null) {
            config.webroot = substituteField(config.webroot);
        }
        if (config.openBrowserURL != null) {
            config.openBrowserURL = substituteField(config.openBrowserURL);
        }
        if (config.configurationFile != null) {
            config.configurationFile = substituteField(config.configurationFile);
        }
        if (config.envFile != null) {
            config.envFile = substituteField(config.envFile);
        }
        
        // Substitute in JVM config
        if (config.jvm != null) {
            if (config.jvm.maxMemory != null) {
                config.jvm.maxMemory = substituteField(config.jvm.maxMemory);
            }
            if (config.jvm.minMemory != null) {
                config.jvm.minMemory = substituteField(config.jvm.minMemory);
            }
            // PROTECTED ZONE: jvm.additionalArgs — only #env:VAR# / #secret:NAME# syntax is processed.
            // ${VAR} is left intact for JVM runtime resolution.
            if (config.jvm.additionalArgs != null) {
                for (int i = 0; i < config.jvm.additionalArgs.length; i++) {
                    config.jvm.additionalArgs[i] = replaceLucliVars(config.jvm.additionalArgs[i]);
                }
            }
        }
        
        // Substitute in URL Rewrite config
        if (config.urlRewrite != null) {
            if (config.urlRewrite.routerFile != null) {
                config.urlRewrite.routerFile = substituteField(config.urlRewrite.routerFile);
            }
        }
        
        // Substitute in Admin config
        if (config.admin != null) {
            if (config.admin.password != null) {
                config.admin.password = substituteField(config.admin.password);
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
                resolved.put(key, value != null ? substituteField(value) : null);
            }
            config.envVars = resolved;
        }
        
        // Substitute in Runtime config
        if (config.runtime != null) {
            if (config.runtime.type != null) {
                config.runtime.type = substituteField(config.runtime.type);
            }
            if (config.runtime.installPath != null) {
                config.runtime.installPath = substituteField(config.runtime.installPath);
            }
            if (config.runtime.variant != null) {
                config.runtime.variant = substituteField(config.runtime.variant);
            }
            if (config.runtime.catalinaHome != null) {
                config.runtime.catalinaHome = substituteField(config.runtime.catalinaHome);
            }
            if (config.runtime.catalinaBase != null) {
                config.runtime.catalinaBase = substituteField(config.runtime.catalinaBase);
            }
            if (config.runtime.jettyHome != null) {
                config.runtime.jettyHome = substituteField(config.runtime.jettyHome);
            }
            if (config.runtime.image != null) {
                config.runtime.image = substituteField(config.runtime.image);
            }
            if (config.runtime.dockerfile != null) {
                config.runtime.dockerfile = substituteField(config.runtime.dockerfile);
            }
            if (config.runtime.context != null) {
                config.runtime.context = substituteField(config.runtime.context);
            }
            if (config.runtime.tag != null) {
                config.runtime.tag = substituteField(config.runtime.tag);
            }
            if (config.runtime.containerName != null) {
                config.runtime.containerName = substituteField(config.runtime.containerName);
            }
            if (config.runtime.runMode != null) {
                config.runtime.runMode = substituteField(config.runtime.runMode);
            }
        }

        // PROTECTED ZONE: configuration block — only #env:VAR# / #secret:NAME# syntax is processed.
        // ${VAR} is left intact for Lucee runtime resolution in .CFConfig.json.
        if (config.configuration != null) {
            config.configuration = replaceLucliVarsInJsonNode(config.configuration);
        }
    }
    
    /**
     * Resolve relative paths (starting with "./" or "../") in envVars values
     * to absolute paths against the project directory. This ensures the server
     * process receives correct paths regardless of its working directory.
     */
    private static void resolveRelativeEnvVarPaths(ServerConfig config, Path projectDir) {
        if (config.envVars == null || config.envVars.isEmpty() || projectDir == null) {
            return;
        }
        Path absProjectDir = projectDir.toAbsolutePath().normalize();
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && (value.startsWith("./") || value.startsWith("../"))) {
                value = absProjectDir.resolve(value).normalize().toString();
            }
            resolved.put(key, value);
        }
        config.envVars = resolved;
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
     * Replace secret placeholders in a string.
     * Supports both new #secret:NAME# syntax (preferred) and deprecated ${secret:NAME} syntax.
     */
    private static String replaceSecretPlaceholders(
        String value,
        String providerName,
        char[] localPassphrase
    ) throws Exception {
        if (value == null) {
            return null;
        }
        // Process new #secret:NAME# syntax first
        java.util.regex.Pattern hashPattern = java.util.regex.Pattern.compile("#secret:([^#]+)#");
        java.util.regex.Matcher hashMatcher = hashPattern.matcher(value);
        StringBuffer sb1 = new StringBuffer();
        while (hashMatcher.find()) {
            String name = hashMatcher.group(1).trim();
            String replacement = readSecretForPlaceholder(name, providerName, localPassphrase, true);
            hashMatcher.appendReplacement(sb1, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        hashMatcher.appendTail(sb1);
        value = sb1.toString();
        
        // Process deprecated ${secret:NAME} syntax
        java.util.regex.Pattern dollarPattern = java.util.regex.Pattern.compile("\\$\\{secret:([^}]+)\\}");
        java.util.regex.Matcher dollarMatcher = dollarPattern.matcher(value);
        StringBuffer sb2 = new StringBuffer();
        boolean foundDeprecated = false;
        while (dollarMatcher.find()) {
            foundDeprecated = true;
            String name = dollarMatcher.group(1).trim();
            String replacement = readSecretForPlaceholder(name, providerName, localPassphrase, true);
            dollarMatcher.appendReplacement(sb2, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        dollarMatcher.appendTail(sb2);
        if (foundDeprecated && !hasShownVarSyntaxDeprecation) {
            System.err.println(
                "\u26a0\ufe0f  Deprecation: ${secret:NAME} syntax is deprecated.\n" +
                "   Please use #secret:NAME# instead. ${secret:NAME} will be removed in a future release.");
            hasShownVarSyntaxDeprecation = true;
        }
        return sb2.toString();
    }
    
    /**
     * Replace secret placeholders in a string using only #secret:NAME# syntax.
     * Used for protected zones (configuration blocks) where ${...} must be preserved.
     * (In protected zones, #env:VAR# is the only env var syntax processed.)
     */
    private static String replaceLucliSecretPlaceholders(
        String value,
        String providerName,
        char[] localPassphrase
    ) throws Exception {
        if (value == null) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("#secret:([^#]+)#");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = readSecretForPlaceholder(name, providerName, localPassphrase, true);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String readSecretForPlaceholder(
        String secretName,
        String providerName,
        char[] localPassphrase,
        boolean required
    ) throws Exception {
        if (LucliSecretProviderSupport.isLocalProviderName(providerName)) {
            return LucliSecretProviderSupport.readSecretFromLocalStore(
                secretName,
                localPassphrase,
                required,
                "Enter secrets passphrase to unlock config secrets: "
            );
        }
        return LucliSecretProviderSupport.readSecretViaLuceeProvider(providerName, secretName);
    }

    /**
     * Quick scan to see if any secret placeholders are present in fields we support.
     * Checks for both new #secret:NAME# and deprecated ${secret:NAME} syntax.
     */
    private static boolean hasSecretPlaceholders(ServerConfig config) {
        java.util.regex.Pattern hashPattern = java.util.regex.Pattern.compile("#secret:([^#]+)#");
        java.util.regex.Pattern dollarPattern = java.util.regex.Pattern.compile("\\$\\{secret:([^}]+)\\}");

        if (config.admin != null && config.admin.password != null) {
            if (hashPattern.matcher(config.admin.password).find() ||
                dollarPattern.matcher(config.admin.password).find()) {
                return true;
            }
        }
        if (config.configuration != null) {
            return jsonNodeHasSecretPlaceholders(config.configuration, hashPattern) ||
                   jsonNodeHasSecretPlaceholders(config.configuration, dollarPattern);
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
     * Resolve secret placeholders across relevant config fields.
     * Supports both #secret:NAME# (preferred) and deprecated ${secret:NAME} syntax.
     *
     * Behaviour:
     * - If there are no secret placeholders, this is a no-op.
     * - If placeholders exist but no passphrase can be obtained (env var or console),
     *   an IOException is thrown so the caller sees a clear failure.
     *
     * For the "configuration" block (protected zone), only #secret:NAME# is resolved;
     * ${secret:NAME} is left intact so Lucee can handle ${...} at runtime.
     *
     * This method is intentionally public so that only commands that actually
     * need secrets (e.g. server start, server lock) pay the cost of prompting
     * for the secret store passphrase.
     */
    public static void resolveSecretPlaceholders(ServerConfig config, Path projectDir) throws IOException {
        if (!hasSecretPlaceholders(config)) {
            return; // nothing to do
        }
        String providerName = LucliSecretProviderSupport.getSelectedProviderName();
        char[] localPassphrase = null;

        if (LucliSecretProviderSupport.isLocalProviderName(providerName)) {
            if (!LucliSecretProviderSupport.hasLocalStoreFile()) {
                throw new IOException(
                    "Configuration references secret placeholders but local secret store does not exist. " +
                    "Run 'lucli secrets init' and define the required secrets."
                );
            }
            localPassphrase = LucliSecretProviderSupport.resolvePassphrase(
                null,
                "Enter secrets passphrase to unlock config secrets: "
            );
            if (localPassphrase == null || localPassphrase.length == 0) {
                throw new IOException(
                    "Configuration requires secrets but no passphrase is available. Set " +
                    LucliSecretProviderSupport.DEFAULT_PASSPHRASE_ENV +
                    " or run with an interactive console."
                );
            }
        }

        try {
            // admin.password: supports both syntaxes (not a protected zone)
            if (config.admin != null && config.admin.password != null) {
                config.admin.password = replaceSecretPlaceholders(
                    config.admin.password,
                    providerName,
                    localPassphrase
                );
            }
            // PROTECTED ZONE: configuration block — only #secret:NAME# is resolved.
            // ${secret:NAME} is left intact for Lucee runtime.
            if (config.configuration != null) {
                config.configuration = substituteLucliSecretsInJsonNode(
                    config.configuration,
                    providerName,
                    localPassphrase
                );
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Recursively substitute secret placeholders in a JSON node.
     * Supports both #secret:NAME# and deprecated ${secret:NAME} syntax.
     * Used for non-protected zones.
     */
    private static com.fasterxml.jackson.databind.JsonNode substituteSecretsInJsonNode(
        com.fasterxml.jackson.databind.JsonNode node,
        String providerName,
        char[] localPassphrase
    ) throws Exception {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String replaced = replaceSecretPlaceholders(node.asText(), providerName, localPassphrase);
            return objectMapper.getNodeFactory().textNode(replaced);
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.getNodeFactory().arrayNode();
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                arrayNode.add(substituteSecretsInJsonNode(element, providerName, localPassphrase));
            }
            return arrayNode;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objNode = objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                try {
                    objNode.set(
                        entry.getKey(),
                        substituteSecretsInJsonNode(entry.getValue(), providerName, localPassphrase)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return objNode;
        } else {
            return node;
        }
    }
    
    /**
     * Recursively substitute only #secret:NAME# placeholders in a JSON node.
     * Used for protected zones (configuration blocks) where ${...} must be preserved.
     */
    private static com.fasterxml.jackson.databind.JsonNode substituteLucliSecretsInJsonNode(
        com.fasterxml.jackson.databind.JsonNode node,
        String providerName,
        char[] localPassphrase
    ) throws Exception {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            String replaced = replaceLucliSecretPlaceholders(node.asText(), providerName, localPassphrase);
            return objectMapper.getNodeFactory().textNode(replaced);
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode = objectMapper.getNodeFactory().arrayNode();
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                arrayNode.add(substituteLucliSecretsInJsonNode(element, providerName, localPassphrase));
            }
            return arrayNode;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objNode = objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                try {
                    objNode.set(
                        entry.getKey(),
                        substituteLucliSecretsInJsonNode(entry.getValue(), providerName, localPassphrase)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return objNode;
        } else {
            return node;
        }
    }

    /**
     * Replace LuCLI variables in a string.
     * Preferred syntax: #env:VAR# or #env:VAR:-default#
     * Deprecated syntax: bare #VAR# or #VAR:-default# (still works with a warning)
     * Checks .env file variables first, then system environment variables.
     */
    private static String replaceLucliVars(String value) {
        if (value == null) {
            return null;
        }
        
        // Pattern: #...# (matches all hash-delimited placeholders)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("#([^#]+)#");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = null;
            
            // Skip secret and project placeholders — handled separately
            if (placeholder.startsWith("secret:") || placeholder.startsWith("project:")) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            
            // Warn about CFML-like expressions — LuCLI currently only supports
            // simple variable names. Full CFML expression evaluation is planned.
            if (placeholder.contains("(") || placeholder.contains(")")) {
                System.err.println(
                    "\u26a0\ufe0f  '#" + placeholder + "#' looks like a CFML expression. " +
                    "LuCLI currently only supports simple variable names in #env:VAR# placeholders " +
                    "(e.g. #env:MY_VAR# or #env:MY_VAR:-default#). CFML expression evaluation is planned for a future release.");
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            
            // Determine the effective variable expression:
            // #env:VAR# or #env:VAR:-default# → strip "env:" prefix
            // bare #VAR# → deprecated, emit warning
            String varExpression;
            if (placeholder.startsWith("env:")) {
                varExpression = placeholder.substring(4); // strip "env:" prefix
            } else {
                // Bare #VAR# — deprecated
                if (!hasShownEnvPrefixDeprecation) {
                    System.err.println(
                        "\u26a0\ufe0f  Deprecation: bare #VAR# syntax is deprecated for LuCLI variable substitution.\n" +
                        "   Please use #env:VAR# instead (e.g. #env:" + placeholder + "#). Bare #VAR# will be removed in a future release.");
                    hasShownEnvPrefixDeprecation = true;
                }
                varExpression = placeholder;
            }
            
            // Check if it has a default value
            if (varExpression.contains(":-")) {
                String[] parts = varExpression.split(":-", 2);
                String varName = parts[0].trim();
                String defaultValue = parts[1].trim();
                replacement = getEnvVar(varName);
                if (replacement == null) {
                    replacement = defaultValue;
                    recordRealizedEnvVariable(varName, defaultValue);
                }
            } else {
                // Just the variable name
                replacement = getEnvVar(varExpression);
                if (replacement == null) {
                    // Keep the placeholder if env var doesn't exist
                    replacement = matcher.group(0);
                }
            }
            
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Recursively substitute LuCLI #env:VAR# variables in a JSON node.
     * Unlike substituteInJsonNode, this does NOT process ${VAR} syntax,
     * leaving those intact for Lucee runtime resolution.
     */
    private static JsonNode replaceLucliVarsInJsonNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(replaceLucliVars(node.asText()));
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode = 
                objectMapper.getNodeFactory().arrayNode();
            for (JsonNode element : node) {
                arrayNode.add(replaceLucliVarsInJsonNode(element));
            }
            return arrayNode;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objNode = 
                objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                objNode.set(entry.getKey(), replaceLucliVarsInJsonNode(entry.getValue()));
            });
            return objNode;
        } else {
            return node;
        }
    }

    /**
     * Recursively replace {@code #project:path#} placeholders in all text values of a JSON node
     * with the absolute path of the project directory.
     *
     * <p>This follows the same {@code #prefix:value#} convention as {@code #env:VAR#} and
     * {@code #secret:NAME#}. The {@code project:} prefix is skipped during the env-var pass
     * and resolved here after all other substitutions.</p>
     *
     * <p>Example: {@code jdbc:sqlite:#project:path#/db/development.db}</p>
     */
    public static JsonNode resolveProjectPlaceholders(JsonNode node, Path projectDir) {
        if (node == null || projectDir == null) {
            return node;
        }
        String projectPath = projectDir.toAbsolutePath().normalize().toString();

        if (node.isTextual()) {
            String text = node.asText();
            if (text.contains("#project:path#")) {
                return objectMapper.getNodeFactory().textNode(text.replace("#project:path#", projectPath));
            }
            return node;
        } else if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arrayNode =
                objectMapper.getNodeFactory().arrayNode();
            for (JsonNode element : node) {
                arrayNode.add(resolveProjectPlaceholders(element, projectDir));
            }
            return arrayNode;
        } else if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objNode =
                objectMapper.getNodeFactory().objectNode();
            node.fields().forEachRemaining(entry -> {
                objNode.set(entry.getKey(), resolveProjectPlaceholders(entry.getValue(), projectDir));
            });
            return objNode;
        } else {
            return node;
        }
    }

    /**
     * Substitute a single config field value: first applies new #env:VAR# syntax
     * (and deprecated bare #VAR#), then falls back to deprecated ${VAR} syntax
     * for backward compatibility.
     */
    private static String substituteField(String value) {
        if (value == null) {
            return null;
        }
        value = replaceLucliVars(value);
        value = replaceEnvVars(value);
        return value;
    }
    
    /**
     * @deprecated Use #VAR# or #VAR:-default# syntax instead.
     * Replace environment variables in a string using ${VAR} or ${VAR:-default} syntax.
     * This method is kept for backward compatibility and emits a deprecation warning.
     * Checks .env file variables first, then system environment variables.
     */
    @Deprecated
    private static String replaceEnvVars(String value) {
        if (value == null) {
            return null;
        }
        
        // Pattern: ${VAR_NAME} or ${VAR_NAME:-default_value}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        
        // Check if there are any ${VAR} placeholders before proceeding
        if (!matcher.find()) {
            return value; // No ${VAR} placeholders, nothing to do
        }
        
        // Emit deprecation warning once per session
        if (!hasShownVarSyntaxDeprecation) {
            System.err.println(
                "⚠️  Deprecation: ${VAR} syntax for LuCLI variable substitution is deprecated.\n" +
                "   Please use #VAR# instead. ${VAR} will be removed in a future release.");
            hasShownVarSyntaxDeprecation = true;
        }
        
        // Reset matcher to start from beginning
        matcher.reset();
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
                    recordRealizedEnvVariable(varName, defaultValue);
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

        // Populate the new lucee block with defaults
        config.lucee = new LuceeEngineConfig();
        
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
     * Check if a port is available.
     *
     * <p>Two probes, because neither alone is sufficient on a dual-stack host:
     * <ol>
     *   <li><b>Connect probe</b> against both loopback families (127.0.0.1 and
     *       ::1). A successful connect means something is actively LISTENing on
     *       the port. This is what catches IPv4-only listeners that the bind
     *       probe misses: on a dual-stack JVM (no -Djava.net.preferIPv4Stack)
     *       {@code new ServerSocket(port)} binds the IPv6 wildcard, which does
     *       not conflict with a socket bound to the IPv4 family — so an
     *       IPv4-only listener (python http.server, Django runserver,
     *       Postgres/Redis on 127.0.0.1, ...) would otherwise be reported as
     *       "available" and the server would start on top of it. Connecting is
     *       not fooled by sockets in TIME_WAIT, so it does not regress quick
     *       restarts.</li>
     *   <li><b>Bind probe</b> on the wildcard, preserving the original behaviour
     *       for ports that cannot be bound even though nothing is accepting
     *       connections there.</li>
     * </ol>
     */
    public static boolean isPortAvailable(int port) {
        // A listener on either loopback family means the port is already in use.
        if (isListening("127.0.0.1", port) || isListening("::1", port)) {
            return false;
        }
        // Fall back to a bind probe for ports that are reserved/bind-blocked
        // without an accepting listener.
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * True if a TCP connection to {@code host:port} succeeds quickly — i.e. some
     * process is actively LISTENing there. Connection refused, timeout, or an
     * unresolvable/unreachable address all mean "nothing listening on this
     * address" and return false.
     */
    private static boolean isListening(String host, int port) {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(InetAddress.getByName(host), port), 200);
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
        validateCfConfigSupport(config);

        JsonNode result = null;

        // Layer external configuration files (if configured):
        //   baseConfigurationFile (set by applyEnvironment when env overrides file)
        //   -> configurationFile (effective file after environment merge)
        Path baseConfigurationFilePath = resolveConfigurationFilePath(projectDir, config.baseConfigurationFile);
        Path effectiveConfigurationFilePath = resolveConfigurationFilePath(projectDir, config.configurationFile);

        if (baseConfigurationFilePath != null && Files.exists(baseConfigurationFilePath)) {
            result = objectMapper.readTree(baseConfigurationFilePath.toFile());
        }
        if (effectiveConfigurationFilePath != null
                && Files.exists(effectiveConfigurationFilePath)
                && (baseConfigurationFilePath == null
                    || !baseConfigurationFilePath.equals(effectiveConfigurationFilePath))) {
            JsonNode effectiveFileConfig = objectMapper.readTree(effectiveConfigurationFilePath.toFile());
            result = mergeCfConfigNodes(result, effectiveFileConfig);
        }

        // Merge inline configuration (if present) as overrides
        if (config.configuration != null && !config.configuration.isNull()) {
            result = mergeCfConfigNodes(result, config.configuration);
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

        // If no provider is configured but we have a local LuCLI store, inject
        // a fallback LuCLI local provider so Lucee can resolve secrets natively.
        result = LucliSecretProviderSupport.ensureFallbackSecretProvider(result, objectMapper);

        // Resolve #project:path# placeholders in all text values
        if (result != null && projectDir != null) {
            result = resolveProjectPlaceholders(result, projectDir);
        }

        return result;
    }

    private static JsonNode mergeCfConfigNodes(JsonNode baseConfig, JsonNode overrideConfig) throws IOException {
        if (overrideConfig == null || overrideConfig.isNull()) {
            return baseConfig;
        }
        if (baseConfig == null || baseConfig.isNull()) {
            return overrideConfig;
        }
        try {
            return mergeLuceeConfigSection(baseConfig, overrideConfig);
        }
        catch (Exception e) {
            throw new IOException("ConfigMerge via Lucee failed: " + e.getMessage(), e);
        }
    }

    private static Path resolveConfigurationFilePath(Path projectDir, String configurationFile) {
        if (configurationFile == null || configurationFile.trim().isEmpty()) {
            return null;
        }
        Path cfConfigPath = Paths.get(configurationFile.trim());
        if (!cfConfigPath.isAbsolute()) {
            if (projectDir == null) {
                return null;
            }
            cfConfigPath = projectDir.resolve(cfConfigPath);
        }
        return cfConfigPath.normalize();
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
     */
    public static ServerConfig applyEnvironment(ServerConfig base, String envName) {
        return applyEnvironment(base, envName, null, "lucee.json");
    }
    
    /**
     * Apply environment-specific overrides to a base ServerConfig.
     * 
     * @param base The base ServerConfig loaded from lucee.json
     * @param envName The environment name to apply (e.g., "prod", "dev", "staging")
     * @param projectDir The project directory (used to read raw environment JSON)
     * @return A new ServerConfig with environment overrides deep-merged into the base
     */
    public static ServerConfig applyEnvironment(ServerConfig base, String envName, Path projectDir) {
        return applyEnvironment(base, envName, projectDir, "lucee.json");
    }

    /**
     * Apply environment-specific overrides to a base ServerConfig.
     * 
     * @param base The base ServerConfig loaded from the provided config file
     * @param envName The environment name to apply (e.g., "prod", "dev", "staging")
     * @param projectDir The project directory (used to read raw environment JSON)
     * @param configFileName The config file name to read environment overrides from
     * @return A new ServerConfig with environment overrides deep-merged into the base
     */
    public static ServerConfig applyEnvironment(
            ServerConfig base,
            String envName,
            Path projectDir,
            String configFileName) {
        if (envName == null || envName.trim().isEmpty()) {
            return base; // No environment specified
        }
        String normalizedEnvName = envName.trim();
        String resolvedConfigFileName = (configFileName == null || configFileName.trim().isEmpty())
                ? "lucee.json"
                : configFileName.trim();
        
        // Missing environments should be a no-op so callers can pass --env values
        // safely and continue with already computed defaults/base config.
        if (base.environments == null || !base.environments.containsKey(normalizedEnvName)) {
            System.err.println(
                "⚠️  Environment '" + normalizedEnvName + "' not found in " + resolvedConfigFileName + "; using base configuration."
            );
            return base;
        }
        
        // Prevent recursive environment definitions
        ServerConfig envOverrides = base.environments.get(normalizedEnvName);
        if (envOverrides.environments != null && !envOverrides.environments.isEmpty()) {
            throw new IllegalArgumentException(
                "Environment '" + normalizedEnvName + "' cannot contain nested 'environments' definitions"
            );
        }
        
        try {
            // Try to read raw environment JSON from file to get only explicitly set fields
            JsonNode rawEnvNode = null;
            if (projectDir != null) {
                Path configFile = projectDir.resolve(resolvedConfigFileName);
                if (Files.exists(configFile)) {
                    JsonNode rootNode = objectMapper.readTree(configFile.toFile());
                    JsonNode envsNode = rootNode.get("environments");
                    if (envsNode != null && envsNode.has(normalizedEnvName)) {
                        rawEnvNode = envsNode.get(normalizedEnvName);
                    }
                }
            }
            
            boolean hasEnvironmentConfigurationFileOverride =
                hasExplicitEnvironmentConfigurationFileOverride(rawEnvNode);
            ServerConfig merged = deepMergeConfigs(base, rawEnvNode);
            if (hasEnvironmentConfigurationFileOverride) {
                String inheritedBaseConfigurationFile =
                    (base.baseConfigurationFile != null && !base.baseConfigurationFile.trim().isEmpty())
                        ? base.baseConfigurationFile
                        : base.configurationFile;
                merged.baseConfigurationFile = inheritedBaseConfigurationFile;
            }
            if (projectDir != null) {
                // Re-evaluate env files after environment overrides are applied.
                // Layering behavior:
                //   base envFile -> environment-specific envFile override
                // so environment-specific keys can override matching base-file keys.
                clearLoadedEnvFileVariables();
                loadEnvFile(projectDir, resolveConfiguredEnvFileName(base));
                String envOverrideEnvFileName = resolveOptionalConfiguredEnvFileName(rawEnvNode);
                if (envOverrideEnvFileName != null) {
                    loadEnvFile(projectDir, envOverrideEnvFileName);
                }
            }
            // Re-run substitution after environment merge so placeholders introduced
            // by environment-specific overrides are realized.
            clearRealizedEnvVariables();
            substituteEnvironmentVariables(merged);
            if (projectDir != null) {
                resolveRelativeEnvVarPaths(merged, projectDir);
            }
            return merged;
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge environment configuration: " + e.getMessage(), e);
        }
    }

    private static boolean hasExplicitEnvironmentConfigurationFileOverride(JsonNode rawEnvNode) {
        if (rawEnvNode == null || !rawEnvNode.isObject()) {
            return false;
        }
        JsonNode configurationFileNode = rawEnvNode.get("configurationFile");
        return configurationFileNode != null
            && !configurationFileNode.isNull()
            && configurationFileNode.isTextual()
            && !configurationFileNode.asText().trim().isEmpty();
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
        // simply return base if there are no overrides to apply
        if (rawOverrideNode == null || rawOverrideNode.isNull() || rawOverrideNode.isEmpty()) {
            return base;
        }
        try {
            // make a copy to avoid modifying the original override node
            JsonNode overrideCopy = rawOverrideNode.deepCopy();
            if (overrideCopy.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) overrideCopy).remove("environments");
            }

            // extract "configuration" from both sides, ConfigMerge needs a proper CFConfig structure
            JsonNode baseNode = objectMapper.valueToTree(base);
            JsonNode baseConfigRaw = baseNode.get("configuration");
            JsonNode baseConfig = (baseConfigRaw == null || baseConfigRaw.isNull()) ? null : baseConfigRaw;

            JsonNode overrideConfigRaw = overrideCopy.get("configuration");
            JsonNode overrideConfig = (overrideConfigRaw == null || overrideConfigRaw.isNull()) ? null : overrideConfigRaw;

            // remove "configuration" from both nodes before the regular merge
            ((com.fasterxml.jackson.databind.node.ObjectNode) baseNode).remove("configuration");
            ((com.fasterxml.jackson.databind.node.ObjectNode) overrideCopy).remove("configuration");

            // regular deep merge for everything except "configuration"
            JsonNode mergedNode = mergeJsonNodes(baseNode.deepCopy(), overrideCopy);

            // merge the "configuration" blocks via Lucee configMerge
            JsonNode mergedConfig = mergeLuceeConfigSection(baseConfig, overrideConfig);

            // put merged "configuration" back into the merged node
            ((com.fasterxml.jackson.databind.node.ObjectNode) mergedNode).set("configuration", mergedConfig);

            // Environment overrides may introduce placeholder-backed numeric strings
            // (e.g. "port": "#env:HTTP_PORT#"), so preprocess before strict binding.
            mergedNode = preprocessNumericFieldsForDeserialization(mergedNode);
            return objectMapper.treeToValue(mergedNode, ServerConfig.class);
        }
        catch (Exception e) {
            throw new IOException("ConfigMerge via Lucee failed: " + e.getMessage(), e);
        }
    }

    private static JsonNode mergeLuceeConfigSection(JsonNode baseConfig, JsonNode overrideConfig) throws ScriptException, IOException {
        // mix both
        if (baseConfig != null && overrideConfig != null) {
            ScriptEngine engine = LuceeScriptEngine.getInstance().getEngine();
            engine.put("__cfgBase", objectMapper.writeValueAsString(baseConfig));
            engine.put("__cfgOverride", objectMapper.writeValueAsString(overrideConfig));
            engine.eval("__cfgMerged = serializeJSON( var: configMerge( deserializeJSON(__cfgBase), deserializeJSON(__cfgOverride) ), compact: false);");
            String mergedConfigJson = (String) engine.get("__cfgMerged");
            return objectMapper.readTree(mergedConfigJson);
        }
        // left or right
        if (baseConfig != null) {
            return baseConfig;
        }
        if (overrideConfig != null) {
            return overrideConfig;
        }
        // create
        return objectMapper.createObjectNode();
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
                if (!dep.isEnabled()) {
                    continue;
                }
                if (dep.getMapping() != null && dep.getInstallPath() != null) {
                    addComputedMapping(mappingsNode, dep, projectDir);
                }
            }
            
            // Process dev dependencies
            for (org.lucee.lucli.config.DependencyConfig dep : config.parseDevDependencies()) {
                if (!dep.isEnabled()) {
                    continue;
                }
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
        JsonNode sourceNode = dep.get("source");
        String source = sourceNode != null && !sourceNode.isNull() ? sourceNode.asText() : null;
        boolean isForgeBox = source != null && "forgebox".equalsIgnoreCase(source);
        
        if (mappingNode == null || mappingNode.isNull()) {
            return; // No mapping defined for this dependency
        }
        
        String virtualPath = mappingNode.asText();
        String installPath = (installPathNode != null && !installPathNode.isNull())
                ? installPathNode.asText()
                : null;
        
        // ForgeBox dependencies are expected to install under dependencies/<name>
        // by default. When a stale lock entry points elsewhere, avoid overriding
        // the computed declared mapping with a non-existent path.
        if ((installPath == null || installPath.isBlank()) && isForgeBox) {
            installPath = "dependencies/" + depName;
        }
        if (installPath == null || installPath.isBlank()) {
            return;
        }
        
        // Resolve to absolute path
        Path physicalPath = Paths.get(installPath);
        if (!physicalPath.isAbsolute()) {
            physicalPath = projectDir.resolve(installPath).toAbsolutePath().normalize();
        }
        
        if (isForgeBox && !Files.exists(physicalPath)) {
            Path defaultForgeboxPath = projectDir.resolve("dependencies")
                                                 .resolve(depName)
                                                 .toAbsolutePath()
                                                 .normalize();
            if (Files.exists(defaultForgeboxPath)) {
                physicalPath = defaultForgeboxPath;
            } else {
                // Keep any existing declared mapping rather than overriding it
                // with a stale/non-existent lock-file path.
                return;
            }
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
        
        Path cfConfigPath = resolveCfConfigPath(config, serverInstanceDir);
        Path existingConfigPath = cfConfigPath;

        if (!Files.exists(existingConfigPath)) {
            Path legacyCfConfigPath = resolveLegacyCfConfigPath(config, serverInstanceDir);
            if (legacyCfConfigPath == null || !Files.exists(legacyCfConfigPath)) {
                // No existing file – nothing to merge.
                return cfConfig;
            }
            existingConfigPath = legacyCfConfigPath;
        }

        JsonNode existing = objectMapper.readTree(existingConfigPath.toFile());
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
        Path cfConfigPath = resolveCfConfigPath(config, serverInstanceDir);
        Path canonicalContextDir = cfConfigPath.getParent();
        Files.createDirectories(canonicalContextDir);

        // Only log array overrides when we actually merged into an existing file.
        if (!arrayPaths.isEmpty() && Files.exists(cfConfigPath)) {
            System.out.println("⚠️  CFConfig merge applied array overrides at: " + String.join(", ", arrayPaths));
            System.out.println("    Existing arrays at these paths were replaced rather than merged.");
        }

        objectMapper.writeValue(cfConfigPath.toFile(), finalConfig);
        System.out.println("Generated lucee config in: " + cfConfigPath);

        // Cleanup legacy nested location when present to avoid diverging CFConfig state.
        Path legacyCfConfigPath = resolveLegacyCfConfigPath(config, serverInstanceDir);
        if (legacyCfConfigPath != null
                && !legacyCfConfigPath.equals(cfConfigPath)
                && Files.exists(legacyCfConfigPath)) {
            try {
                Files.deleteIfExists(legacyCfConfigPath);
                Path legacyContextDir = legacyCfConfigPath.getParent();
                if (legacyContextDir != null && isDirectoryEmpty(legacyContextDir)) {
                    Files.deleteIfExists(legacyContextDir);
                }
                Path legacyNestedLuceeServerDir = legacyContextDir != null ? legacyContextDir.getParent() : null;
                if (legacyNestedLuceeServerDir != null && isDirectoryEmpty(legacyNestedLuceeServerDir)) {
                    Files.deleteIfExists(legacyNestedLuceeServerDir);
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to clean legacy nested .CFConfig.json path: " + e.getMessage());
            }
        }
    }

    /**
     * Resolve the effective .CFConfig.json path for the given runtime.
     *
     * <p>All supported runtimes write the effective CFConfig at
     * lucee-server/context/.CFConfig.json under the server instance directory.</p>
     */
    private static Path resolveCfConfigPath(ServerConfig config, Path serverInstanceDir) {

        return serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
    }

    /**
     * Legacy CFConfig path used by older LuCLI builds for Tomcat-based runtimes.
     * This is read-only fallback to preserve existing config until rewritten.
     */
    private static Path resolveLegacyCfConfigPath(ServerConfig config, Path serverInstanceDir) {
        String runtimeType = "lucee-express";
        if (config != null
                && config.runtime != null
                && config.runtime.type != null
                && !config.runtime.type.trim().isEmpty()) {
            runtimeType = config.runtime.type.trim();
        }

        if ("jetty".equalsIgnoreCase(runtimeType)) {
            return null;
        }

        return serverInstanceDir
                .resolve("lucee-server")
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
    }

    private static boolean isDirectoryEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            return !stream.findFirst().isPresent();
        }
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
