package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive unit tests for TomcatConfigSupport.
 *
 * These tests cover the shared utilities that both runtime providers
 * (Express and vendor Tomcat) depend on. Regressions here could break
 * server configuration for all runtime types.
 */
public class TomcatConfigSupportTest {

    @TempDir
    Path tempDir;

    private Path projectDir;
    private Path serverInstanceDir;

    @BeforeEach
    void setUp() throws IOException {
        projectDir = tempDir.resolve("project");
        serverInstanceDir = tempDir.resolve("server");
        Files.createDirectories(projectDir);
        Files.createDirectories(serverInstanceDir);
    }

    // ── getLucliHome ─────────────────────────────────────────────────────

    @Test
    void getLucliHome_returnsPathFromSystemProperty() {
        String original = System.getProperty("lucli.home");
        try {
            System.setProperty("lucli.home", "/custom/lucli/home");
            Path home = TomcatConfigSupport.getLucliHome();
            assertEquals(Path.of("/custom/lucli/home"), home);
        } finally {
            if (original != null) {
                System.setProperty("lucli.home", original);
            } else {
                System.clearProperty("lucli.home");
            }
        }
    }

    @Test
    void getLucliHome_fallsBackToUserHome() {
        String original = System.getProperty("lucli.home");
        try {
            System.clearProperty("lucli.home");
            Path home = TomcatConfigSupport.getLucliHome();
            // When no system prop and no env var, should end with .lucli under user home
            assertTrue(home.toString().endsWith(".lucli"),
                    "Default lucli home should end with .lucli, got: " + home);
        } finally {
            if (original != null) {
                System.setProperty("lucli.home", original);
            }
        }
    }

    // ── createPlaceholderMap ─────────────────────────────────────────────

    @Test
    void createPlaceholderMap_containsAllRequiredKeys() {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        String[] requiredKeys = {
                "${httpPort}", "${shutdownPort}", "${jmxPort}",
                "${webroot}", "${luceeServerPath}", "${luceeWebPath}",
                "${luceePatches}", "${jvmRoute}", "${logLevel}", "${routerFile}"
        };

        for (String key : requiredKeys) {
            assertTrue(map.containsKey(key), "Missing placeholder: " + key);
            assertNotNull(map.get(key), "Null value for placeholder: " + key);
        }
    }

    @Test
    void createPlaceholderMap_httpPortMatchesConfig() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 9090;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("9090", map.get("${httpPort}"));
    }

    @Test
    void createPlaceholderMap_shutdownPortDerivedFromHttpPort() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        config.shutdownPort = null;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("9080", map.get("${shutdownPort}"));
    }

    @Test
    void createPlaceholderMap_shutdownPortUsesExplicitValue() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        config.shutdownPort = 5555;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("5555", map.get("${shutdownPort}"));
    }

    @Test
    void createPlaceholderMap_jmxPortFromConfig() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.monitoring.jmx.port = 7777;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("7777", map.get("${jmxPort}"));
    }

    @Test
    void createPlaceholderMap_webrootIsAbsolute() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.webroot = "./";

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        Path webroot = Path.of(map.get("${webroot}"));
        assertTrue(webroot.isAbsolute(), "Webroot should be absolute: " + webroot);
    }

    @Test
    void createPlaceholderMap_luceePathsAreUnderServerInstance() {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        String serverPath = map.get("${luceeServerPath}");
        String webPath = map.get("${luceeWebPath}");

        assertTrue(serverPath.startsWith(serverInstanceDir.toAbsolutePath().toString()),
                "Lucee server path should be under server instance dir");
        assertTrue(serverPath.contains("lucee-server"),
                "Lucee server path should contain lucee-server");
        assertTrue(webPath.startsWith(serverInstanceDir.toAbsolutePath().toString()),
                "Lucee web path should be under server instance dir");
        assertTrue(webPath.contains("lucee-web"),
                "Lucee web path should contain lucee-web");
    }

    @Test
    void createPlaceholderMap_jvmRouteMatchesServerName() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.name = "my-cool-server";

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("my-cool-server", map.get("${jvmRoute}"));
    }

    @Test
    void createPlaceholderMap_routerFileFromConfig() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        config.urlRewrite.routerFile = "app.cfm";

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("app.cfm", map.get("${routerFile}"));
    }

    @Test
    void createPlaceholderMap_routerFileDefaultsToIndexCfm() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = null;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("index.cfm", map.get("${routerFile}"));
    }

    @Test
    void createPlaceholderMap_routerFileDefaultsWhenRouterFileNull() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        config.urlRewrite.routerFile = null;

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("index.cfm", map.get("${routerFile}"));
    }

    @Test
    void createPlaceholderMap_logLevelIsINFO() {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        Map<String, String> map = TomcatConfigSupport.createPlaceholderMap(
                serverInstanceDir, config, projectDir);

        assertEquals("INFO", map.get("${logLevel}"));
    }

    // ── deleteDirectoryRecursively ───────────────────────────────────────

    @Test
    void deleteDirectoryRecursively_removesNestedStructure() throws IOException {
        Path dir = tempDir.resolve("to-delete");
        Files.createDirectories(dir.resolve("sub1/sub2"));
        Files.writeString(dir.resolve("file1.txt"), "hello");
        Files.writeString(dir.resolve("sub1/file2.txt"), "world");
        Files.writeString(dir.resolve("sub1/sub2/file3.txt"), "deep");

        TomcatConfigSupport.deleteDirectoryRecursively(dir);

        assertFalse(Files.exists(dir), "Directory should be deleted");
    }

    @Test
    void deleteDirectoryRecursively_handlesNonExistentDir() throws IOException {
        Path dir = tempDir.resolve("does-not-exist");

        // Should not throw
        assertDoesNotThrow(() -> TomcatConfigSupport.deleteDirectoryRecursively(dir));
    }

    @Test
    void deleteDirectoryRecursively_removesEmptyDir() throws IOException {
        Path dir = tempDir.resolve("empty-dir");
        Files.createDirectories(dir);

        TomcatConfigSupport.deleteDirectoryRecursively(dir);

        assertFalse(Files.exists(dir), "Empty directory should be deleted");
    }

    // ── applyTemplate ───────────────────────────────────────────────────

    @Test
    void applyTemplate_replacesPlaceholders() throws IOException {
        // Use an actual template from the classpath
        Path output = tempDir.resolve("output/logging.properties");
        Map<String, String> placeholders = Map.of(
                "${logLevel}", "WARNING",
                "${catalina.base}", "/tmp/test-base"
        );

        TomcatConfigSupport.applyTemplate(
                "tomcat_template/conf/logging.properties", output, placeholders);

        assertTrue(Files.exists(output), "Output file should be created");
        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("WARNING"),
                "Template should have replaced ${logLevel} with WARNING");
    }

    @Test
    void applyTemplate_createsParentDirectories() throws IOException {
        Path output = tempDir.resolve("deep/nested/dir/file.txt");
        Map<String, String> placeholders = Map.of("${logLevel}", "FINE");

        TomcatConfigSupport.applyTemplate(
                "tomcat_template/conf/logging.properties", output, placeholders);

        assertTrue(Files.exists(output), "Output file should be created with parent dirs");
    }

    @Test
    void applyTemplate_throwsForMissingTemplate() {
        Path output = tempDir.resolve("output.txt");

        assertThrows(IOException.class, () ->
                TomcatConfigSupport.applyTemplate(
                        "nonexistent/template.txt", output, Map.of()));
    }

    // ── displayPortDetails ──────────────────────────────────────────────

    @Test
    void displayPortDetails_includesHttpPort() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 9999;
        config.name = "test-srv";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, null));

        assertTrue(output.contains("9999"), "Should display HTTP port");
        assertTrue(output.contains("test-srv"), "Should display server name");
    }

    @Test
    void displayPortDetails_includesRuntimeLabel() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.name = "my-server";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, "external Tomcat"));

        assertTrue(output.contains("external Tomcat"), "Should display runtime label");
    }

    @Test
    void displayPortDetails_foregroundModeShowsCtrlCMessage() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.name = "fg-server";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, true, null));

        assertTrue(output.contains("foreground"), "Should mention foreground mode");
        assertTrue(output.contains("Ctrl+C"), "Should show Ctrl+C hint");
    }

    @Test
    void displayPortDetails_includesShutdownPort() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        config.shutdownPort = null;
        config.name = "test";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, null));

        assertTrue(output.contains("9080"), "Should display shutdown port (8080 + 1000)");
    }

    @Test
    void displayPortDetails_includesJmxPortWhenMonitoringEnabled() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.monitoring = new LuceeServerConfig.MonitoringConfig();
        config.monitoring.enabled = true;
        config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
        config.monitoring.jmx.port = 7777;
        config.name = "test";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, null));

        assertTrue(output.contains("7777"), "Should display JMX port when monitoring enabled");
    }

    @Test
    void displayPortDetails_excludesJmxPortWhenMonitoringDisabled() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.monitoring = new LuceeServerConfig.MonitoringConfig();
        config.monitoring.enabled = false;
        config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
        config.monitoring.jmx.port = 7777;
        config.name = "test";

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, null));

        assertFalse(output.contains("7777"), "Should not display JMX port when monitoring disabled");
    }

    @Test
    void displayPortDetails_includesHttpsWhenEnabled() {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.name = "test";
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.enabled = true;
        config.https.port = 8443;

        String output = captureStdout(() ->
                TomcatConfigSupport.displayPortDetails(config, false, null));

        assertTrue(output.contains("8443"), "Should display HTTPS port when enabled");
        assertTrue(output.contains("HTTPS"), "Should mention HTTPS");
    }

    // ── Helper methods ──────────────────────────────────────────────────

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

    private String captureStdout(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
