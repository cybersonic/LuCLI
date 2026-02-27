package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for LuceeServerConfig
 * Tests JSON parsing, environment merging, port handling, and configuration defaults
 */
public class LuceeServerConfigTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clean environment for each test
    }

    // ===================
    // Default Configuration Tests
    // ===================

    @Test
    void createDefaultConfig_setsReasonableDefaults() {
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.createDefaultConfig(tempDir);
        
        assertEquals(tempDir.getFileName().toString(), config.name);
        // Note: Default version may change - just verify it's set
        assertNotNull(LuceeServerConfig.getLuceeVersion(config));
        // Default port should be in the expected range (8000-8999)
        assertTrue(config.port >= 8000 && config.port <= 8999,
            "Default port should be in range 8000-8999, was: " + config.port);
        assertEquals("./", config.webroot);
        assertTrue(config.enableLucee);
        assertFalse(config.enableREST);
        assertTrue(config.openBrowser);
    }

    @Test
    void createDefaultConfig_initializesNestedConfigs() {
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.createDefaultConfig(tempDir);
        
        assertNotNull(config.monitoring);
        assertNotNull(config.jvm);
        assertNotNull(config.urlRewrite);
        assertNotNull(config.admin);
        assertNotNull(config.agents);
        assertNotNull(config.environments);
        // New lucee block should be populated by default
        assertNotNull(config.lucee);
        assertEquals("6.2.2.91", config.lucee.version);
        assertEquals("standard", config.lucee.variant);
    }

    // ===================
    // JSON Parsing Tests
    // ===================

    @Test
    void loadConfig_parsesMinimalJson() throws IOException {
        String json = """
            {
                "name": "test-server",
                "port": 9000
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        
        assertEquals("test-server", config.name);
        assertEquals(9000, config.port);
    }

    @Test
    void loadConfig_parsesFullJson() throws IOException {
        String json = """
            {
                "name": "full-server",
                "version": "7.0.0.100",
                "port": 8888,
                "host": "myapp.localhost",
                "webroot": "./public",
                "enableLucee": true,
                "enableREST": true,
                "openBrowser": false,
                "jvm": {
                    "maxMemory": "1024m",
                    "minMemory": "256m"
                },
                "monitoring": {
                    "enabled": true,
                    "jmx": {
                        "port": 9999
                    }
                },
                "urlRewrite": {
                    "enabled": true,
                    "routerFile": "app.cfm"
                },
                "admin": {
                    "enabled": true,
                    "password": "secret"
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        
        assertEquals("full-server", config.name);
        // Legacy top-level version should be migrated to lucee block
        assertEquals("7.0.0.100", LuceeServerConfig.getLuceeVersion(config));
        assertNotNull(config.lucee);
        assertEquals("7.0.0.100", config.lucee.version);
        assertEquals(8888, config.port);
        assertEquals("myapp.localhost", config.host);
        assertEquals("./public", config.webroot);
        assertTrue(config.enableLucee);
        assertTrue(config.enableREST);
        assertFalse(config.openBrowser);
        assertEquals("1024m", config.jvm.maxMemory);
        assertEquals("256m", config.jvm.minMemory);
        assertTrue(config.monitoring.enabled);
        assertEquals(9999, config.monitoring.jmx.port);
        assertTrue(config.urlRewrite.enabled);
        assertEquals("app.cfm", config.urlRewrite.routerFile);
        assertTrue(config.admin.enabled);
        assertEquals("secret", config.admin.password);
    }

    @Test
    void loadConfig_createsDefaultWhenMissing() throws IOException {
        // No lucee.json file exists
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        
        assertNotNull(config);
        assertEquals(tempDir.getFileName().toString(), config.name);
        
        // Should have created the file
        assertTrue(Files.exists(tempDir.resolve("lucee.json")));
    }

    @Test
    void loadConfig_handlesAlternateConfigFile() throws IOException {
        String json = """
            {
                "name": "alternate-config",
                "port": 7777
            }
            """;
        Path configFile = tempDir.resolve("lucee-prod.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir, "lucee-prod.json");
        
        assertEquals("alternate-config", config.name);
        assertEquals(7777, config.port);
    }

    // ===================
    // Lucee Version Migration Tests
    // ===================

    @Test
    void loadConfig_migratesLegacyVersionToLuceeBlock() throws IOException {
        String json = """
            {
                "name": "legacy-server",
                "version": "6.2.2.91",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // Legacy version should be migrated into the lucee block
        assertNotNull(config.lucee, "lucee block should be created during migration");
        assertEquals("6.2.2.91", config.lucee.version);
        assertEquals("6.2.2.91", LuceeServerConfig.getLuceeVersion(config));
    }

    @Test
    void loadConfig_prefersNewFormatOverLegacy() throws IOException {
        String json = """
            {
                "name": "new-format-server",
                "version": "1.0.0",
                "lucee": {
                    "version": "7.0.0.346",
                    "variant": "light"
                },
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // New format takes precedence
        assertEquals("7.0.0.346", LuceeServerConfig.getLuceeVersion(config));
        assertEquals("light", LuceeServerConfig.getLuceeVariant(config));
    }

    @Test
    void getLuceeVersion_fallsBackToDefault() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.lucee = null;
        config.version = null;

        assertEquals("6.2.2.91", LuceeServerConfig.getLuceeVersion(config));
    }

    @Test
    void getLuceeVariant_prefersLuceeBlockOverRuntime() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.lucee = new LuceeServerConfig.LuceeEngineConfig();
        config.lucee.variant = "zero";
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.variant = "light";

        assertEquals("zero", LuceeServerConfig.getLuceeVariant(config));
    }

    @Test
    void getLuceeVariant_fallsBackToRuntimeVariant() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.lucee = null;
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.variant = "light";

        assertEquals("light", LuceeServerConfig.getLuceeVariant(config));
    }

    @Test
    void setLuceeVersion_updatesBothFields() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        LuceeServerConfig.setLuceeVersion(config, "7.0.0.100-RC");

        assertNotNull(config.lucee);
        assertEquals("7.0.0.100-RC", config.lucee.version);
        assertEquals("7.0.0.100-RC", LuceeServerConfig.getLuceeVersion(config));
    }

    // ===================
    // Port Handling Tests
    // ===================

    @Test
    void getShutdownPort_calculatesFromHttpPort() {
        // Default shutdown port is HTTP port + 1000
        assertEquals(9080, LuceeServerConfig.getShutdownPort(8080));
        assertEquals(10000, LuceeServerConfig.getShutdownPort(9000));
        assertEquals(1080, LuceeServerConfig.getShutdownPort(80));
    }

    @Test
    void getEffectiveShutdownPort_usesExplicitWhenSet() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.port = 8080;
        config.shutdownPort = 5555;
        
        assertEquals(5555, LuceeServerConfig.getEffectiveShutdownPort(config));
    }

    @Test
    void getEffectiveShutdownPort_calculatesWhenNotSet() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.port = 8080;
        config.shutdownPort = null;
        
        assertEquals(9080, LuceeServerConfig.getEffectiveShutdownPort(config));
    }

    // ===================
    // HTTPS Configuration Tests
    // ===================

    @Test
    void isHttpsEnabled_falseByDefault() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        assertFalse(LuceeServerConfig.isHttpsEnabled(config));
    }

    @Test
    void isHttpsEnabled_trueWhenConfigured() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.enabled = true;
        
        assertTrue(LuceeServerConfig.isHttpsEnabled(config));
    }

    @Test
    void getEffectiveHttpsPort_defaultsTo8443() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        assertEquals(8443, LuceeServerConfig.getEffectiveHttpsPort(config));
    }

    @Test
    void getEffectiveHttpsPort_usesConfiguredPort() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.port = 9443;
        
        assertEquals(9443, LuceeServerConfig.getEffectiveHttpsPort(config));
    }

    @Test
    void isHttpsRedirectEnabled_defaultsTrueWhenHttpsEnabled() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.enabled = true;
        config.https.redirect = null;
        
        assertTrue(LuceeServerConfig.isHttpsRedirectEnabled(config));
    }

    @Test
    void isHttpsRedirectEnabled_respectsExplicitFalse() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.enabled = true;
        config.https.redirect = false;
        
        assertFalse(LuceeServerConfig.isHttpsRedirectEnabled(config));
    }

    // ===================
    // Host Configuration Tests
    // ===================

    @Test
    void getEffectiveHost_defaultsToLocalhost() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        assertEquals("localhost", LuceeServerConfig.getEffectiveHost(config));
    }

    @Test
    void getEffectiveHost_usesConfiguredHost() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.host = "myapp.local";
        
        assertEquals("myapp.local", LuceeServerConfig.getEffectiveHost(config));
    }

    @Test
    void getEffectiveHost_treatsEmptyAsDefault() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.host = "   ";
        
        assertEquals("localhost", LuceeServerConfig.getEffectiveHost(config));
    }

    // ===================
    // Webroot Resolution Tests
    // ===================

    @Test
    void resolveWebroot_resolvesRelativePath() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.webroot = "./public";
        
        Path resolved = LuceeServerConfig.resolveWebroot(config, tempDir);
        
        assertEquals(tempDir.resolve("public").toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void resolveWebroot_handlesAbsolutePath() throws IOException {
        Path absoluteWebroot = tempDir.resolve("absolute-webroot");
        Files.createDirectories(absoluteWebroot);
        
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.webroot = absoluteWebroot.toAbsolutePath().toString();
        
        Path resolved = LuceeServerConfig.resolveWebroot(config, tempDir);
        
        assertEquals(absoluteWebroot.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void resolveWebroot_defaultsToProjectDir() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.webroot = "./";
        
        Path resolved = LuceeServerConfig.resolveWebroot(config, tempDir);
        
        assertEquals(tempDir.toAbsolutePath(), resolved.toAbsolutePath());
    }

    // ===================
    // Environment Configuration Tests
    // ===================

    @Test
    void loadConfig_parsesEnvironments() throws IOException {
        String json = """
            {
                "name": "env-test",
                "port": 8080,
                "environments": {
                    "prod": {
                        "port": 80,
                        "openBrowser": false
                    },
                    "dev": {
                        "port": 3000
                    }
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        
        assertNotNull(config.environments);
        assertEquals(2, config.environments.size());
        assertTrue(config.environments.containsKey("prod"));
        assertTrue(config.environments.containsKey("dev"));
        assertEquals(80, config.environments.get("prod").port);
        assertEquals(3000, config.environments.get("dev").port);
    }

    // ===================
    // Agent Configuration Tests
    // ===================

    @Test
    void loadConfig_parsesAgents() throws IOException {
        String json = """
            {
                "name": "agent-test",
                "agents": {
                    "luceedebug": {
                        "enabled": true,
                        "jvmArgs": ["-agentlib:jdwp=transport=dt_socket"],
                        "description": "Debug agent"
                    }
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        
        assertNotNull(config.agents);
        assertTrue(config.agents.containsKey("luceedebug"));
        assertTrue(config.agents.get("luceedebug").enabled);
        assertEquals("Debug agent", config.agents.get("luceedebug").description);
        assertEquals(1, config.agents.get("luceedebug").jvmArgs.length);
    }

    // ===================
    // Variable Substitution Tests (#env:VAR# syntax)
    // ===================

    @Test
    void loadConfig_envPrefixVarSubstitutesInName() throws IOException {
        // Set an env var via .env file
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MY_SERVER_NAME=hash-test-server\n");

        String json = """
            {
                "name": "#env:MY_SERVER_NAME#",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertEquals("hash-test-server", config.name);
    }

    @Test
    void loadConfig_envPrefixVarWithDefault() throws IOException {
        // No .env file, no system env for UNSET_VAR
        String json = """
            {
                "name": "#env:UNSET_LUCLI_TEST_VAR:-fallback-name#",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertEquals("fallback-name", config.name);
    }

    @Test
    void loadConfig_envPrefixVarSubstitutesInAdminPassword() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "ADMIN_PW=s3cret\n");

        String json = """
            {
                "name": "pw-test",
                "port": 8080,
                "admin": {
                    "password": "#env:ADMIN_PW#"
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertEquals("s3cret", config.admin.password);
    }

    @Test
    void loadConfig_bareHashVarStillWorksBackwardCompat() throws IOException {
        // Bare #VAR# (no env: prefix) should still resolve for backward compat
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "LEGACY_BARE_VAR=legacy-value\n");

        String json = """
            {
                "name": "#LEGACY_BARE_VAR#",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // Deprecated bare #VAR# should still resolve
        assertEquals("legacy-value", config.name);
    }

    @Test
    void loadConfig_dollarVarStillWorksOutsideConfigBlock() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "LEGACY_HOST=legacy.localhost\n");

        String json = """
            {
                "name": "legacy-var-test",
                "host": "${LEGACY_HOST}",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // Deprecated ${VAR} should still resolve outside protected zones
        assertEquals("legacy.localhost", config.host);
    }

    @Test
    void loadConfig_dollarVarPreservedInConfigurationBlock() throws IOException {
        String json = """
            {
                "name": "config-block-test",
                "port": 8080,
                "configuration": {
                    "inspectTemplate": "once",
                    "password": "${LUCEE_RUNTIME_PASSWORD}"
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // ${VAR} in configuration block must be preserved for Lucee runtime
        assertNotNull(config.configuration);
        String password = config.configuration.get("password").asText();
        assertEquals("${LUCEE_RUNTIME_PASSWORD}", password);
    }

    @Test
    void loadConfig_envPrefixVarResolvedInConfigurationBlock() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "LUCLI_INSPECT=always\n");

        String json = """
            {
                "name": "config-hash-test",
                "port": 8080,
                "configuration": {
                    "inspectTemplate": "#env:LUCLI_INSPECT#",
                    "runtimePassword": "${LUCEE_RUNTIME_PW}"
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertNotNull(config.configuration);
        // #env:VAR# should be resolved
        assertEquals("always", config.configuration.get("inspectTemplate").asText());
        // ${VAR} should be preserved for Lucee
        assertEquals("${LUCEE_RUNTIME_PW}", config.configuration.get("runtimePassword").asText());
    }

    @Test
    void loadConfig_dollarVarPreservedInJvmAdditionalArgs() throws IOException {
        String json = """
            {
                "name": "jvm-args-test",
                "port": 8080,
                "jvm": {
                    "additionalArgs": [
                        "-Dfile.encoding=UTF-8",
                        "-Djava.io.tmpdir=${java.io.tmpdir}/lucli"
                    ]
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // ${java.io.tmpdir} should be preserved for JVM runtime resolution
        assertEquals("-Djava.io.tmpdir=${java.io.tmpdir}/lucli", config.jvm.additionalArgs[1]);
    }

    @Test
    void loadConfig_envPrefixVarResolvedInJvmAdditionalArgs() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MY_ENCODING=UTF-16\n");

        String json = """
            {
                "name": "jvm-hash-test",
                "port": 8080,
                "jvm": {
                    "additionalArgs": [
                        "-Dfile.encoding=#env:MY_ENCODING#"
                    ]
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertEquals("-Dfile.encoding=UTF-16", config.jvm.additionalArgs[0]);
    }

    @Test
    void loadConfig_unsetEnvPrefixVarPreserved() throws IOException {
        String json = """
            {
                "name": "#env:NONEXISTENT_LUCLI_VAR#",
                "port": 8080
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        // Unresolvable #env:VAR# should be preserved as-is
        assertEquals("#env:NONEXISTENT_LUCLI_VAR#", config.name);
    }

    // ===================
    // Unique Server Name Tests
    // ===================

    @Test
    void getUniqueServerName_returnsBaseNameWhenAvailable() throws IOException {
        Path serversDir = tempDir.resolve("servers");
        Files.createDirectories(serversDir);
        
        String unique = LuceeServerConfig.getUniqueServerName("myserver", serversDir);
        
        assertEquals("myserver", unique);
    }

    @Test
    void getUniqueServerName_appendsSuffixWhenTaken() throws IOException {
        Path serversDir = tempDir.resolve("servers");
        Files.createDirectories(serversDir);
        Files.createDirectories(serversDir.resolve("myserver"));
        
        String unique = LuceeServerConfig.getUniqueServerName("myserver", serversDir);
        
        assertEquals("myserver-1", unique);
    }

    @Test
    void getUniqueServerName_incrementsSuffixUntilAvailable() throws IOException {
        Path serversDir = tempDir.resolve("servers");
        Files.createDirectories(serversDir);
        Files.createDirectories(serversDir.resolve("myserver"));
        Files.createDirectories(serversDir.resolve("myserver-1"));
        Files.createDirectories(serversDir.resolve("myserver-2"));
        
        String unique = LuceeServerConfig.getUniqueServerName("myserver", serversDir);
        
        assertEquals("myserver-3", unique);
    }

    // ===================
    // Example Config Validation Tests
    // ===================

    /**
     * Files to skip during example validation. Add entries here for example
     * configs that are intentionally invalid or require environment variables
     * that won't be available during test runs.
     * Entries are matched against the relative path from the examples/ root,
     * e.g. "docker/lucee-broken.json".
     */
    private static final Set<String> EXAMPLE_PARSE_EXCEPTIONS = Set.of(
        // No exceptions currently — all examples use literal values
    );

    /**
     * Filename patterns that look like lucee*.json but are NOT server configs.
     * Lock files and CFConfig files have different schemas and must be skipped.
     */
    private static boolean isServerConfigFile(String fileName) {
        if (!fileName.startsWith("lucee") || !fileName.endsWith(".json")) return false;
        // Exclude lock files (lucee-lock.json) and CFConfig files (lucee-config.json)
        if (fileName.contains("-lock")) return false;
        if (fileName.contains("-config")) return false;
        return true;
    }

    @TestFactory
    Collection<DynamicTest> exampleConfigs_parseWithoutErrors() throws IOException {
        Path examplesDir = Path.of(System.getProperty("user.dir"), "examples");

        if (!Files.isDirectory(examplesDir)) {
            return List.of(DynamicTest.dynamicTest(
                "examples/ directory not found — skipping",
                () -> { /* nothing to validate */ }
            ));
        }

        List<Path> configFiles;
        try (Stream<Path> walk = Files.walk(examplesDir)) {
            configFiles = walk
                .filter(Files::isRegularFile)
                .filter(p -> isServerConfigFile(p.getFileName().toString()))
                .toList();
        }

        return configFiles.stream().map(configPath -> {
            String relativePath = examplesDir.relativize(configPath).toString();
            String displayName = "parse examples/" + relativePath;

            return DynamicTest.dynamicTest(displayName, () -> {
                if (EXAMPLE_PARSE_EXCEPTIONS.contains(relativePath)) {
                    return; // intentionally skipped
                }

                Path parentDir = configPath.getParent();
                String fileName = configPath.getFileName().toString();

                LuceeServerConfig.ServerConfig config =
                        LuceeServerConfig.loadConfig(parentDir, fileName);

                assertNotNull(config, "Config should not be null for " + relativePath);
                assertNotNull(config.name,
                        "Config name should not be null for " + relativePath);
                assertTrue(config.port > 0,
                        "Config port should be > 0 for " + relativePath);
            });
        }).toList();
    }
}
