package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Test
    void reloadConfiguredEnvFile_withEnvironmentLayersBaseAndEnvironmentEnvFiles() throws IOException {
        Files.writeString(tempDir.resolve("default.env"), "DEFAULT=from-default\nBASE_ONLY=base-only\n");
        Files.writeString(tempDir.resolve("prod.env"), "DEFAULT=from-prod\nPROD_ONLY=prod-only\n");

        String json = """
            {
              "name": "env-file-layering-test",
              "envFile": "./default.env",
              "environments": {
                "prod": {
                  "envFile": "./prod.env"
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir);
        LuceeServerConfig.ServerConfig merged = LuceeServerConfig.applyEnvironment(base, "prod", tempDir);

        LuceeServerConfig.reloadConfiguredEnvFile(merged, tempDir, "lucee.json", "prod");

        java.util.Map<String, String> envPreview = new java.util.HashMap<>();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(envPreview);

        assertEquals("from-prod", envPreview.get("DEFAULT"));
        assertEquals("base-only", envPreview.get("BASE_ONLY"));
        assertEquals("prod-only", envPreview.get("PROD_ONLY"));
    }

    @Test
    void applyLoadedEnvToProcessEnvironment_overridesExistingShellValues() throws IOException {
        Files.writeString(tempDir.resolve("app.env"), "CONFIG_PATH=from-envfile\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.envFile = "./app.env";
        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        java.util.Map<String, String> effectiveEnv = new java.util.HashMap<>();
        effectiveEnv.put("CONFIG_PATH", "from-shell");

        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(effectiveEnv);

        assertEquals("from-envfile", effectiveEnv.get("CONFIG_PATH"));
    }

    @Test
    void applyLoadedEnvToProcessEnvironment_overridesEmptyShellValues() throws IOException {
        Files.writeString(tempDir.resolve("app.env"), "CONFIG_PATH=from-envfile\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.envFile = "./app.env";
        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        java.util.Map<String, String> effectiveEnv = new java.util.HashMap<>();
        effectiveEnv.put("CONFIG_PATH", "");

        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(effectiveEnv);

        assertEquals("from-envfile", effectiveEnv.get("CONFIG_PATH"));
    }

    @Test
    void applyLoadedEnvToProcessEnvironment_envVarsStillOverrideEnvFileAfterShellMerge() throws IOException {
        Files.writeString(tempDir.resolve("app.env"), "CONFIG_PATH=from-envfile\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.envFile = "./app.env";
        config.envVars.put("CONFIG_PATH", "from-envvars");
        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        java.util.Map<String, String> effectiveEnv = new java.util.HashMap<>();
        effectiveEnv.put("CONFIG_PATH", "from-shell");

        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(effectiveEnv);
        LuceeServerConfig.applyConfigEnvVarsToProcessEnvironment(effectiveEnv, config.envVars);

        assertEquals("from-envvars", effectiveEnv.get("CONFIG_PATH"));
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
        assertEquals("7.0.4.34", config.lucee.version);
        assertEquals("standard", config.lucee.variant);
        assertFalse(config.urlRewrite.enabled,
            "URL rewrite should be disabled by default unless explicitly enabled");
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
        assertNotNull(config.urlRewrite);
        assertFalse(config.urlRewrite.enabled,
            "URL rewrite should be disabled when not specified in lucee.json");
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
        config.version = "1.0.0";
        config.lucee = null;
        config.version = null;

        assertEquals("7.0.4.34", LuceeServerConfig.getLuceeVersion(config));
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
    void setLuceeVersion_updatesLuceeBlockWithoutOverwritingAppVersion() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        LuceeServerConfig.setLuceeVersion(config, "7.0.0.100-RC");

        assertNotNull(config.lucee);
        assertEquals("7.0.0.100-RC", config.lucee.version);
        assertEquals("7.0.0.100-RC", LuceeServerConfig.getLuceeVersion(config));
        assertEquals("1.0.0", config.version);
    }

    // ===================
    // Lucee Version Cutoff Tests
    // ===================

    @Test
    void isLuceeVersionAtLeast_handlesQualifiedVersions() {
        assertTrue(LuceeServerConfig.isLuceeVersionAtLeast("6.1.0.0", 6, 1));
        assertTrue(LuceeServerConfig.isLuceeVersionAtLeast("6.1.8.29", 6, 1));
        assertTrue(LuceeServerConfig.isLuceeVersionAtLeast("7.0.1.100-RC", 6, 1));

        assertFalse(LuceeServerConfig.isLuceeVersionAtLeast("6.0.4.10", 6, 1));
        assertFalse(LuceeServerConfig.isLuceeVersionAtLeast("5.4.6.9", 6, 1));
    }

    @Test
    void validateLuceeVersionSupportForRuntime_rejectsSub61() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                LuceeServerConfig.validateLuceeVersionSupportForRuntime("6.0.4.10", "lucee-express"));

        assertTrue(ex.getMessage().contains("6.0.4.10"));
        assertTrue(ex.getMessage().contains(LuceeServerConfig.MIN_SUPPORTED_LUCEE_VERSION));
    }

    @Test
    void validateLuceeVersionSupportForRuntime_accepts61AndAbove() {
        assertDoesNotThrow(() ->
                LuceeServerConfig.validateLuceeVersionSupportForRuntime("6.1.0.0", "lucee-express"));
        assertDoesNotThrow(() ->
                LuceeServerConfig.validateLuceeVersionSupportForRuntime("7.0.1.100-RC", "tomcat"));
    }

    @Test
    void validateCfConfigSupport_rejectsSub61WhenCfConfigDefined() throws IOException {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        LuceeServerConfig.setLuceeVersion(config, "6.0.4.10");
        config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"once\"}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                LuceeServerConfig.validateCfConfigSupport(config));

        assertTrue(ex.getMessage().contains(".CFConfig"));
        assertTrue(ex.getMessage().contains(LuceeServerConfig.MIN_SUPPORTED_LUCEE_VERSION));
    }

    @Test
    void resolveConfigurationNode_rejectsSub61WhenCfConfigDefined() throws IOException {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        LuceeServerConfig.setLuceeVersion(config, "6.0.4.10");
        config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"once\"}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                LuceeServerConfig.resolveConfigurationNode(config, tempDir));

        assertTrue(ex.getMessage().contains(".CFConfig"));
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
    @Test
    void applyEnvironment_missingEnvironmentFallsBackToBaseConfig() throws IOException {
        String json = """
            {
              "name": "env-fallback-test",
              "port": 8080,
              "webroot": "./public",
              "environments": {
                "prod": {
                  "port": 80
                }
              }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir);
        LuceeServerConfig.ServerConfig merged = LuceeServerConfig.applyEnvironment(base, "nonexistent", tempDir);

        assertSame(base, merged, "Missing environment should return the base config unchanged");
        assertEquals(8080, merged.port);
        assertEquals("./public", merged.webroot);
    }

    @Test
    void applyEnvironment_usesEnvironmentSpecificEnvFileForRuntimeEnvPreview() throws IOException {
        Files.writeString(tempDir.resolve("default.env"), "DEFAULT=from-default\nBASE_ONLY=base-only\n");
        Files.writeString(tempDir.resolve("prod.env"), "DEFAULT=from-prod\nPROD_ONLY=prod-only\n");

        String json = """
            {
              "name": "env-file-override-test",
              "envFile": "./default.env",
              "envVars": {
                "SETTING1": "from-base"
              },
              "environments": {
                "prod": {
                  "envFile": "./prod.env",
                  "envVars": {
                    "SETTING1": "overridden-in-prod"
                  }
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir);
        LuceeServerConfig.ServerConfig merged = LuceeServerConfig.applyEnvironment(base, "prod", tempDir);

        java.util.Map<String, String> envPreview = new java.util.HashMap<>();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(envPreview);

        assertEquals("./prod.env", merged.envFile);
        assertEquals("overridden-in-prod", merged.envVars.get("SETTING1"));
        assertEquals("from-prod", envPreview.get("DEFAULT"));
        assertEquals("base-only", envPreview.get("BASE_ONLY"));
        assertEquals("prod-only", envPreview.get("PROD_ONLY"));
    }

    @Test
    void applyConfigEnvVarsToProcessEnvironment_nullValueUnsetsExistingKey() {
        java.util.Map<String, String> env = new java.util.HashMap<>();
        env.put("VAR", "from-env-file");
        env.put("KEEP", "keep-me");

        java.util.Map<String, String> envVars = new java.util.HashMap<>();
        envVars.put("VAR", null);

        LuceeServerConfig.applyConfigEnvVarsToProcessEnvironment(env, envVars);

        assertFalse(env.containsKey("VAR"));
        assertEquals("keep-me", env.get("KEEP"));
    }

    @Test
    void applyEnvironment_envVarNullUnsetsLayeredEnvFileValue() throws IOException {
        Files.writeString(tempDir.resolve("default.env"), "VAR=2\n");
        Files.writeString(tempDir.resolve("prod.env"), "VAR=4\n");

        String json = """
            {
              "name": "env-var-null-unset-test",
              "envFile": "./default.env",
              "envVars": {
                "VAR": "3"
              },
              "environments": {
                "prod": {
                  "envFile": "./prod.env",
                  "envVars": {
                    "VAR": null
                  }
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir);
        LuceeServerConfig.ServerConfig merged = LuceeServerConfig.applyEnvironment(base, "prod", tempDir);

        LuceeServerConfig.reloadConfiguredEnvFile(merged, tempDir, "lucee.json", "prod");

        java.util.Map<String, String> effectiveEnv = new java.util.HashMap<>(System.getenv());
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(effectiveEnv);
        LuceeServerConfig.applyConfigEnvVarsToProcessEnvironment(effectiveEnv, merged.envVars);

        assertFalse(effectiveEnv.containsKey("VAR"));
    }

    @Test
    void applyEnvironment_usesProvidedConfigFileForRawEnvironmentOverrides() throws IOException {
        String defaultConfigJson = """
            {
              "name": "default-config",
              "port": 8080,
              "configuration": {
                "preserveCase": true
              },
              "environments": {
                "prod": {
                  "port": 81,
                  "configuration": {
                    "errorGeneralTemplate": "/wrong-from-default.cfm"
                  }
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), defaultConfigJson);

        String customConfigJson = """
            {
              "name": "custom-config",
              "port": 9090,
              "configuration": {
                "preserveCase": true
              },
              "environments": {
                "prod": {
                  "port": 80,
                  "configuration": {
                    "errorGeneralTemplate": "/right-from-custom.cfm"
                  }
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee-env.json"), customConfigJson);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir, "lucee-env.json");
        LuceeServerConfig.ServerConfig merged =
            LuceeServerConfig.applyEnvironment(base, "prod", tempDir, "lucee-env.json");

        assertEquals(80, merged.port);
        assertNotNull(merged.configuration);
        assertEquals(
            "/right-from-custom.cfm",
            merged.configuration.path("errorGeneralTemplate").asText()
        );
    }

    @Test
    void reloadConfiguredEnvFile_loadsEnvFileForMaterializedConfig() throws IOException {
        Files.writeString(tempDir.resolve("runtime.env"), "RUNTIME_ONLY=from-env-file\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.envFile = "./runtime.env";

        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        java.util.Map<String, String> envPreview = new java.util.HashMap<>();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(envPreview);

        assertEquals("from-env-file", envPreview.get("RUNTIME_ONLY"));
    }

   
    void reloadConfiguredEnvFile_clearsPreviouslyLoadedValues() throws IOException {
        Files.writeString(tempDir.resolve("first.env"), "ONLY_FIRST=first\n");
        Files.writeString(tempDir.resolve("second.env"), "ONLY_SECOND=second\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.envFile = "./first.env";
        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        config.envFile = "./second.env";
        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        java.util.Map<String, String> envPreview = new java.util.HashMap<>();
        LuceeServerConfig.applyLoadedEnvToProcessEnvironment(envPreview);

        assertFalse(envPreview.containsKey("ONLY_FIRST"));
        assertEquals("second", envPreview.get("ONLY_SECOND"));
    }

    @Test
    void resolveConfigurationNode_mergesExtensionsFromConfigurationFileAndInlineConfiguration() throws IOException {
        Files.writeString(tempDir.resolve("cfconfig-base.json"), """
            {
              "extensions": [
                { "id": "A", "version": "1.0.0" },
                { "id": "B", "version": "1.0.0" }
              ]
            }
            """);

        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "extension-merge-test",
              "configurationFile": "./cfconfig-base.json",
              "configuration": {
                "extensions": [
                  { "id": "B", "version": "2.0.0" },
                  { "id": "C", "version": "1.0.0" }
                ]
              }
            }
            """);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        JsonNode resolved = LuceeServerConfig.resolveConfigurationNode(config, tempDir);

        JsonNode extensions = resolved.path("extensions");
        assertTrue(extensions.isArray(), "Expected extensions array in resolved CFConfig");
        assertEquals(3, extensions.size(), "Expected base + override extension union without dropping unique base entries");
        assertEquals("A", extensions.get(0).path("id").asText());
        assertEquals("B", extensions.get(1).path("id").asText());
        assertEquals("2.0.0", extensions.get(1).path("version").asText(),
                "Expected inline configuration entry to override matching extension from configurationFile");
        assertEquals("C", extensions.get(2).path("id").asText());
    }
    @Test
    void resolveConfigurationNode_environmentConfigurationFileMergesOverBaseConfigurationFile() throws IOException {
        Files.writeString(tempDir.resolve("cfconfig-base.json"), """
            {
              "baseOnly": "from-base-file",
              "shared": {
                "winner": "base",
                "baseValue": true
              }
            }
            """);

        Files.writeString(tempDir.resolve("cfconfig-dev.json"), """
            {
              "envOnly": "from-env-file",
              "shared": {
                "winner": "env",
                "envValue": true
              }
            }
            """);

        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-config-file-merge-test",
              "configurationFile": "./cfconfig-base.json",
              "environments": {
                "dev": {
                  "configurationFile": "./cfconfig-dev.json"
                }
              }
            }
            """);

        LuceeServerConfig.ServerConfig base = LuceeServerConfig.loadConfig(tempDir);
        LuceeServerConfig.ServerConfig merged = LuceeServerConfig.applyEnvironment(base, "dev", tempDir);
        JsonNode resolved = LuceeServerConfig.resolveConfigurationNode(merged, tempDir);

        assertEquals("from-base-file", resolved.path("baseOnly").asText(),
            "Environment configurationFile should layer on top of base configurationFile");
        assertEquals("from-env-file", resolved.path("envOnly").asText());
        assertEquals("env", resolved.path("shared").path("winner").asText());
        assertTrue(resolved.path("shared").path("baseValue").asBoolean());
        assertTrue(resolved.path("shared").path("envValue").asBoolean());
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
    void loadConfig_envPrefixVarSubstitutesInPort() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "HTTP_PORT=9090\n");

        String json = """
            {
                "name": "port-env-test",
                "port": "#env:HTTP_PORT#"
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        assertEquals(9090, config.port);
    }

    @Test
    void loadConfig_envPrefixVarInvalidPortValueShowsClearError() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "HTTP_PORT=ELVIS IS THE GREATEST\n");

        String json = """
            {
                "name": "port-env-invalid-test",
                "port": "#env:HTTP_PORT#"
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, json);

        IOException exception = assertThrows(IOException.class, () -> LuceeServerConfig.loadConfig(tempDir));
        String message = exception.getMessage();

        assertNotNull(message);
        String normalized = message.toLowerCase();
        assertTrue(normalized.contains("port"), "Error message should identify the port field");
        assertTrue(normalized.contains("int"), "Error message should identify integer parsing");
        assertTrue(normalized.contains("not a valid"), "Error message should explain why parsing failed");
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
    // Dependency Mapping Tests
    // ===================
    
    @Test
    void resolveConfigurationNode_generatesForgeboxMappingToProjectDependenciesDirectory() throws IOException {
        String json = """
            {
              "name": "dep-map-test",
              "dependencies": {
                "testbox": {
                  "version": "5.0.0",
                  "source": "forgebox",
                  "mapping": "/testbox"
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);
        
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        com.fasterxml.jackson.databind.JsonNode resolved =
                LuceeServerConfig.resolveConfigurationNode(config, tempDir);
        
        assertNotNull(resolved);
        com.fasterxml.jackson.databind.JsonNode mapping = resolved.path("mappings").path("/testbox/");
        assertTrue(mapping.isObject(), "Expected /testbox/ mapping to be generated");
        
        String expectedPhysicalPath = tempDir.resolve("dependencies")
                .resolve("testbox")
                .toAbsolutePath()
                .normalize()
                .toString();
        assertEquals(expectedPhysicalPath, mapping.path("physical").asText());
    }
    
    @Test
    void resolveConfigurationNode_prefersExistingForgeboxDependenciesDirectoryWhenLockPathIsStale() throws IOException {
        String json = """
            {
              "name": "dep-map-lock-test",
              "dependencies": {
                "testbox": {
                  "version": "5.0.0",
                  "source": "forgebox",
                  "mapping": "/testbox"
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);
        
        // Simulate actual installed ForgeBox package location
        Files.createDirectories(tempDir.resolve("dependencies").resolve("testbox"));
        
        // Simulate stale lock metadata pointing at an incorrect old installPath
        String staleLockJson = """
            {
              "dependencies": {
                "testbox": {
                  "version": "5.0.0",
                  "resolved": "forgebox:testbox@5.0.0",
                  "integrity": "sha512-dummy",
                  "source": "forgebox",
                  "type": "cfml",
                  "installPath": "testbox",
                  "mapping": "/testbox"
                }
              },
              "devDependencies": {}
            }
            """;
        Files.writeString(tempDir.resolve("lucee-lock.json"), staleLockJson);
        
        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        com.fasterxml.jackson.databind.JsonNode resolved =
                LuceeServerConfig.resolveConfigurationNode(config, tempDir);
        
        assertNotNull(resolved);
        com.fasterxml.jackson.databind.JsonNode mapping = resolved.path("mappings").path("/testbox/");
        assertTrue(mapping.isObject(), "Expected /testbox/ mapping to be generated");
        
        String expectedPhysicalPath = tempDir.resolve("dependencies")
                .resolve("testbox")
                .toAbsolutePath()
                .normalize()
                .toString();
        assertEquals(expectedPhysicalPath, mapping.path("physical").asText(),
                "Stale lock installPath should not override the real dependencies/testbox location");
    }

    @Test
    void resolveConfigurationNode_generatesDeclaredDependencyMappingFromExplicitMappingAndInstallPath() throws IOException {
        String json = """
            {
              "name": "dep-map-explicit-test",
              "dependencies": {
                "my-framework": {
                  "type": "cfml",
                  "version": "4.3.0",
                  "source": "git",
                  "url": "https://github.com/example/my-framework.git",
                  "installPath": "vendor/my-framework",
                  "mapping": "/framework"
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("lucee.json"), json);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        com.fasterxml.jackson.databind.JsonNode resolved =
                LuceeServerConfig.resolveConfigurationNode(config, tempDir);

        assertNotNull(resolved);
        com.fasterxml.jackson.databind.JsonNode mapping = resolved.path("mappings").path("/framework/");
        assertTrue(mapping.isObject(), "Expected declared dependency mapping /framework/ to be generated");

        String expectedPhysicalPath = tempDir.resolve("vendor")
                .resolve("my-framework")
                .toAbsolutePath()
                .normalize()
                .toString();
        assertEquals(expectedPhysicalPath, mapping.path("physical").asText());
    }

    // ===================
    // CFConfig Path Tests
    // ===================

    @Test
    void writeCfConfigIfPresent_writesToLuceeServerContextForTomcatBasedRuntimes() throws IOException {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"once\"}");

        Path serverInstanceDir = tempDir.resolve("server-instance");
        Files.createDirectories(serverInstanceDir);

        LuceeServerConfig.writeCfConfigIfPresent(config, tempDir, serverInstanceDir);

        Path expectedPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        Path legacyPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        assertTrue(Files.exists(expectedPath), "Expected .CFConfig.json at canonical Lucee server context path");
        assertFalse(Files.exists(legacyPath), "Legacy nested .CFConfig.json path should not be written");
    }

    @Test
    void writeCfConfigIfPresent_readsLegacyNestedPathAndWritesCanonicalPath() throws IOException {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"always\"}");

        Path serverInstanceDir = tempDir.resolve("server-instance-legacy");
        Path legacyPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        Files.createDirectories(legacyPath.getParent());
        Files.writeString(legacyPath, "{\"existing\":\"value\"}");

        LuceeServerConfig.writeCfConfigIfPresent(config, tempDir, serverInstanceDir);

        Path canonicalPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");

        assertTrue(Files.exists(canonicalPath), "Expected canonical .CFConfig.json to be written");
        com.fasterxml.jackson.databind.JsonNode written =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(canonicalPath.toFile());
        assertEquals("value", written.path("existing").asText(), "Expected legacy existing config to be merged");
        assertEquals("always", written.path("inspectTemplate").asText(), "Expected new config to override/add values");
        assertFalse(Files.exists(legacyPath), "Legacy non-nested .CFConfig.json path should not be written");
    }

    @Test
    void resolveConfigurationNode_injectsFallbackLocalSecretProviderWhenMissing() throws IOException {
        String originalLucliHome = System.getProperty("lucli.home");
        try {
            Path lucliHome = tempDir.resolve(".lucli-home");
            Path localStore = lucliHome.resolve("secrets").resolve("local.json");
            Files.createDirectories(localStore.getParent());
            Files.writeString(localStore, "{}");
            System.setProperty("lucli.home", lucliHome.toString());

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"once\"}");

            com.fasterxml.jackson.databind.JsonNode resolved =
                LuceeServerConfig.resolveConfigurationNode(config, tempDir);

            assertNotNull(resolved);
            assertTrue(resolved.has("secretProvider"), "Expected fallback secretProvider block");
            assertTrue(
                resolved.path("secretProvider").has("lucli-local"),
                "Expected fallback lucli-local provider entry"
            );
            assertEquals(
                "org.lucee.lucli.secrets.LucliLocalSecretProvider",
                resolved.path("secretProvider").path("lucli-local").path("class").asText()
            );
        } finally {
            if (originalLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", originalLucliHome);
            }
        }
    }

    @Test
    void resolveConfigurationNode_doesNotOverrideExistingSecretProviderConfig() throws IOException {
        String originalLucliHome = System.getProperty("lucli.home");
        try {
            Path lucliHome = tempDir.resolve(".lucli-home-existing");
            Path localStore = lucliHome.resolve("secrets").resolve("local.json");
            Files.createDirectories(localStore.getParent());
            Files.writeString(localStore, "{}");
            System.setProperty("lucli.home", lucliHome.toString());

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.configuration = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {
                  "secretProvider": {
                    "custom": {
                      "class": "example.CustomSecretProvider"
                    }
                  }
                }
                """);

            com.fasterxml.jackson.databind.JsonNode resolved =
                LuceeServerConfig.resolveConfigurationNode(config, tempDir);

            assertNotNull(resolved);
            assertEquals(
                "example.CustomSecretProvider",
                resolved.path("secretProvider").path("custom").path("class").asText()
            );
            assertFalse(
                resolved.path("secretProvider").has("lucli-local"),
                "Fallback provider should not be injected when providers already exist"
            );
        } finally {
            if (originalLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", originalLucliHome);
            }
        }
    }

    @Test
    void writeCfConfigIfPresent_writesToJettyContextForJettyRuntime() throws IOException {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "jetty";
        config.configuration = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"inspectTemplate\":\"once\"}");

        Path serverInstanceDir = tempDir.resolve("jetty-instance");
        Files.createDirectories(serverInstanceDir);

        LuceeServerConfig.writeCfConfigIfPresent(config, tempDir, serverInstanceDir);

        Path expectedPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        Path tomcatNestedPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");

        assertTrue(Files.exists(expectedPath), "Expected .CFConfig.json at Jetty Lucee context path");
        assertFalse(Files.exists(tomcatNestedPath), "Tomcat nested context path should not be used for Jetty");
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

    // ===================
    // #project:path# Placeholder Resolution Tests
    // ===================

    @Test
    void resolveProjectPlaceholders_replacesInTextNode() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.getNodeFactory().textNode("jdbc:sqlite:#project:path#/db/development.db");
        Path projectDir = Path.of("/home/user/myapp");

        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(input, projectDir);

        String expected = "jdbc:sqlite:" + projectDir.toAbsolutePath().normalize() + "/db/development.db";
        assertEquals(expected, result.asText());
    }

    @Test
    void resolveProjectPlaceholders_leavesStringsWithoutPlaceholder() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.getNodeFactory().textNode("jdbc:mysql://localhost/mydb");
        Path projectDir = Path.of("/home/user/myapp");

        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(input, projectDir);

        assertEquals("jdbc:mysql://localhost/mydb", result.asText());
    }

    @Test
    void resolveProjectPlaceholders_handlesNestedObjects() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
            {
                "datasources": {
                    "myds": {
                        "connectionString": "jdbc:sqlite:#project:path#/db/dev.db",
                        "class": "org.sqlite.JDBC"
                    }
                },
                "inspectTemplate": "once"
            }
            """;
        JsonNode input = mapper.readTree(json);
        Path projectDir = Path.of("/app/wheels");
        String resolvedPath = projectDir.toAbsolutePath().normalize().toString();

        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(input, projectDir);

        assertEquals("jdbc:sqlite:" + resolvedPath + "/db/dev.db",
                result.get("datasources").get("myds").get("connectionString").asText());
        assertEquals("org.sqlite.JDBC",
                result.get("datasources").get("myds").get("class").asText());
        assertEquals("once", result.get("inspectTemplate").asText());
    }

    @Test
    void resolveProjectPlaceholders_handlesArrays() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
            ["#project:path#/lib", "no-change", "#project:path#/ext"]
            """;
        JsonNode input = mapper.readTree(json);
        Path projectDir = Path.of("/opt/project");
        String resolvedPath = projectDir.toAbsolutePath().normalize().toString();

        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(input, projectDir);

        assertTrue(result.isArray());
        assertEquals(resolvedPath + "/lib", result.get(0).asText());
        assertEquals("no-change", result.get(1).asText());
        assertEquals(resolvedPath + "/ext", result.get(2).asText());
    }

    @Test
    void resolveProjectPlaceholders_handlesNullNode() {
        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(null, Path.of("/any"));
        assertNull(result);
    }

    @Test
    void resolveProjectPlaceholders_handlesNumericNodes() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.getNodeFactory().numberNode(42);
        Path projectDir = Path.of("/any");

        JsonNode result = LuceeServerConfig.resolveProjectPlaceholders(input, projectDir);

        assertEquals(42, result.asInt());
    }

    @Test
    void resolveConfigurationNode_replacesProjectPlaceholder() throws IOException {
        // Integration test: verify that resolveConfigurationNode resolves #project:path#
        String luceeJson = """
            {
                "name": "placeholder-test",
                "lucee": { "version": "6.2.2.91" },
                "configuration": {
                    "datasources": {
                        "devdb": {
                            "connectionString": "jdbc:sqlite:#project:path#/db/development.db"
                        }
                    }
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, luceeJson);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);
        JsonNode resolved = LuceeServerConfig.resolveConfigurationNode(config, tempDir);

        assertNotNull(resolved);
        String dsn = resolved.get("datasources").get("devdb").get("connectionString").asText();
        String expected = "jdbc:sqlite:" + tempDir.toAbsolutePath() + "/db/development.db";
        assertEquals(expected, dsn);
    }

    @Test
    void writeCfConfigIfPresent_writesResolvedProjectPaths() throws IOException {
        // Integration test: verify the full write path resolves #project:path#
        String luceeJson = """
            {
                "name": "write-test",
                "lucee": { "version": "6.2.2.91" },
                "configuration": {
                    "datasources": {
                        "testds": {
                            "connectionString": "jdbc:sqlite:#project:path#/db/test.db"
                        }
                    }
                }
            }
            """;
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, luceeJson);

        LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(tempDir);

        Path serverInstanceDir = tempDir.resolve("server-instance");
        Files.createDirectories(serverInstanceDir);

        LuceeServerConfig.writeCfConfigIfPresent(config, tempDir, serverInstanceDir);

        // Read back the written .CFConfig.json
        Path cfConfigPath = serverInstanceDir
                .resolve("lucee-server")
                .resolve("context")
                .resolve(".CFConfig.json");
        assertTrue(Files.exists(cfConfigPath), ".CFConfig.json should have been written");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode written = mapper.readTree(cfConfigPath.toFile());
        String dsn = written.get("datasources").get("testds").get("connectionString").asText();
        String expected = "jdbc:sqlite:" + tempDir.toAbsolutePath() + "/db/test.db";
        assertEquals(expected, dsn);
        assertFalse(dsn.contains("#project:path#"), "DSN should not contain unresolved #project:path#");
    }

    // ===================
    // Port Availability Tests
    // ===================

    /**
     * Regression test for the IPv4-blind port check.
     *
     * On a dual-stack JVM (no -Djava.net.preferIPv4Stack), `new ServerSocket(port)`
     * binds the IPv6 wildcard, which does NOT conflict with a listener bound to the
     * IPv4 address family. Before the fix, isPortAvailable() therefore reported a
     * port held by an IPv4-only process (e.g. python http.server, Django runserver,
     * Postgres/Redis on 127.0.0.1) as "available", so `wheels start` would collide
     * with it instead of picking the next free port.
     */
    @Test
    void isPortAvailableReturnsFalseForPortHeldByIPv4OnlyListener() throws IOException {
        try (ServerSocket ipv4Only = new ServerSocket()) {
            ipv4Only.setReuseAddress(false);
            ipv4Only.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            int port = ipv4Only.getLocalPort();

            assertFalse(
                LuceeServerConfig.isPortAvailable(port),
                "A port held by an IPv4-only listener (127.0.0.1) must be reported unavailable");
        }
    }

    /**
     * Guards against an over-correction that reports every port as unavailable:
     * a port with nothing listening must still be reported available.
     */
    @Test
    void isPortAvailableReturnsTrueForFreePort() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            freePort = probe.getLocalPort();
        } // closed here -> port is now free

        assertTrue(
            LuceeServerConfig.isPortAvailable(freePort),
            "A port with no listener must be reported available");
    }
}
