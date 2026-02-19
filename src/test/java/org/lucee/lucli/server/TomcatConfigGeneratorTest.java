package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for TomcatConfigGenerator
 * Tests placeholder map creation and template processing logic
 */
public class TomcatConfigGeneratorTest {

    @TempDir
    Path tempDir;
    
    private TomcatConfigGenerator generator;
    private Path projectDir;
    private Path serverInstanceDir;

    @BeforeEach
    void setUp() throws IOException {
        generator = new TomcatConfigGenerator();
        projectDir = tempDir.resolve("project");
        serverInstanceDir = tempDir.resolve("server");
        Files.createDirectories(projectDir);
        Files.createDirectories(serverInstanceDir);
    }

    // ===================
    // Placeholder Map Tests (using reflection to access private method)
    // ===================

    @Test
    void createPlaceholderMap_containsHttpPort() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 9090;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("9090", placeholders.get("${httpPort}"));
    }

    @Test
    void createPlaceholderMap_containsShutdownPort() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        // Shutdown port should be HTTP port + 1000
        assertEquals("9080", placeholders.get("${shutdownPort}"));
    }

    @Test
    void createPlaceholderMap_usesExplicitShutdownPort() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        config.shutdownPort = 5555;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("5555", placeholders.get("${shutdownPort}"));
    }

    @Test
    void createPlaceholderMap_containsJmxPort() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.monitoring = new LuceeServerConfig.MonitoringConfig();
        config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
        config.monitoring.jmx.port = 7777;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("7777", placeholders.get("${jmxPort}"));
    }

    @Test
    void createPlaceholderMap_containsWebroot() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.webroot = "./";
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertNotNull(placeholders.get("${webroot}"));
        assertTrue(placeholders.get("${webroot}").contains(projectDir.toString()));
    }

    @Test
    void createPlaceholderMap_containsServerPaths() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertNotNull(placeholders.get("${luceeServerPath}"));
        assertNotNull(placeholders.get("${luceeWebPath}"));
        assertNotNull(placeholders.get("${luceePatches}"));
        
        assertTrue(placeholders.get("${luceeServerPath}").contains("lucee-server"));
        assertTrue(placeholders.get("${luceeWebPath}").contains("lucee-web"));
    }

    @Test
    void createPlaceholderMap_containsJvmRoute() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.name = "my-server-name";
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("my-server-name", placeholders.get("${jvmRoute}"));
    }

    @Test
    void createPlaceholderMap_containsRouterFile() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        config.urlRewrite.routerFile = "app.cfm";
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("app.cfm", placeholders.get("${routerFile}"));
    }

    @Test
    void createPlaceholderMap_defaultsRouterFileToIndexCfm() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        config.urlRewrite.routerFile = null;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("index.cfm", placeholders.get("${routerFile}"));
    }

    @Test
    void createPlaceholderMap_containsLogLevel() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        assertEquals("INFO", placeholders.get("${logLevel}"));
    }

    // ===================
    // Configuration Validity Tests
    // ===================

    @Test
    void createPlaceholderMap_allRequiredPlaceholdersPresent() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        // These are required for Tomcat templates
        String[] requiredPlaceholders = {
            "${httpPort}",
            "${shutdownPort}",
            "${jmxPort}",
            "${webroot}",
            "${luceeServerPath}",
            "${luceeWebPath}",
            "${luceePatches}",
            "${jvmRoute}",
            "${logLevel}",
            "${routerFile}"
        };
        
        for (String placeholder : requiredPlaceholders) {
            assertTrue(placeholders.containsKey(placeholder), 
                "Missing required placeholder: " + placeholder);
            assertNotNull(placeholders.get(placeholder),
                "Null value for placeholder: " + placeholder);
        }
    }

    @Test
    void createPlaceholderMap_pathsAreAbsolute() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        // All paths should be absolute
        String webroot = placeholders.get("${webroot}");
        String luceeServerPath = placeholders.get("${luceeServerPath}");
        String luceeWebPath = placeholders.get("${luceeWebPath}");
        
        assertTrue(Path.of(webroot).isAbsolute(), "Webroot should be absolute");
        assertTrue(Path.of(luceeServerPath).isAbsolute(), "Lucee server path should be absolute");
        assertTrue(Path.of(luceeWebPath).isAbsolute(), "Lucee web path should be absolute");
    }

    // ===================
    // URL Rewrite Configuration Tests
    // ===================

    @Test
    void createPlaceholderMap_handlesNullUrlRewrite() throws Exception {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = null;
        
        Map<String, String> placeholders = invokeCreatePlaceholderMap(config);
        
        // Should default to index.cfm
        assertEquals("index.cfm", placeholders.get("${routerFile}"));
    }

    // ===================
    // Helper Methods
    // ===================

    private LuceeServerConfig.ServerConfig createTestConfig() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "test-server";
        config.port = 8080;
        config.webroot = "./";
        config.monitoring = new LuceeServerConfig.MonitoringConfig();
        config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
        config.monitoring.jmx.port = 8999;
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        return config;
    }

    /**
     * Use reflection to access private createPlaceholderMap method
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> invokeCreatePlaceholderMap(LuceeServerConfig.ServerConfig config) throws Exception {
        Method method = TomcatConfigGenerator.class.getDeclaredMethod(
            "createPlaceholderMap", 
            Path.class, 
            LuceeServerConfig.ServerConfig.class, 
            Path.class
        );
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(generator, serverInstanceDir, config, projectDir);
    }
}
