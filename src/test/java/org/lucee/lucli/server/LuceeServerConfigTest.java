package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        assertNotNull(config.version);
        // Default port is 8000 (not 8080)
        assertEquals(8000, config.port);
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
        assertEquals("7.0.0.100", config.version);
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
}
