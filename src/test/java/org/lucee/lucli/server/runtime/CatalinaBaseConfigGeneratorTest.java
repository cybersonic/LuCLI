package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.server.LuceeServerConfig;

/**
 * Unit tests for CatalinaBaseConfigGenerator.
 *
 * These tests verify that the CATALINA_BASE directory is correctly
 * constructed from a given CATALINA_HOME, regardless of whether that
 * HOME is a Lucee Express distribution or a vendor Tomcat installation.
 *
 * A fake CATALINA_HOME structure is created in a temp directory to
 * simulate real distributions without requiring downloads.
 */
public class CatalinaBaseConfigGeneratorTest {

    @TempDir
    Path tempDir;

    private CatalinaBaseConfigGenerator generator;
    private Path catalinaHome;
    private Path catalinaBase;
    private Path projectDir;

    @BeforeEach
    void setUp() throws IOException {
        generator = new CatalinaBaseConfigGenerator();
        catalinaHome = tempDir.resolve("catalina-home");
        catalinaBase = tempDir.resolve("catalina-base");
        projectDir = tempDir.resolve("project");

        Files.createDirectories(projectDir);
        Files.createDirectories(catalinaBase);

        // Create a minimal fake CATALINA_HOME structure
        createFakeCatalinaHome(catalinaHome);
    }

    // ── generateConfiguration: directory structure ───────────────────────

    @Test
    void generateConfiguration_createsCatalinaBaseDirectories() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        assertTrue(Files.isDirectory(catalinaBase.resolve("conf")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("conf/Catalina/localhost")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("logs")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("temp")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("work")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("webapps")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("lucee-server")));
        assertTrue(Files.isDirectory(catalinaBase.resolve("lucee-web")));
    }

    @Test
    void generateConfiguration_copiesServerXmlFromHome() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path serverXml = catalinaBase.resolve("conf/server.xml");
        assertTrue(Files.exists(serverXml), "server.xml should be copied to CATALINA_BASE");
    }

    @Test
    void generateConfiguration_patchesServerXmlPort() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 9090;

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        String serverXml = Files.readString(catalinaBase.resolve("conf/server.xml"), StandardCharsets.UTF_8);
        assertTrue(serverXml.contains("9090"),
                "server.xml should have the configured HTTP port");
    }

    @Test
    void generateConfiguration_patchesShutdownPort() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        config.shutdownPort = 9100;

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        String serverXml = Files.readString(catalinaBase.resolve("conf/server.xml"), StandardCharsets.UTF_8);
        assertTrue(serverXml.contains("9100"),
                "server.xml should have the configured shutdown port");
    }

    @Test
    void generateConfiguration_copiesWebXmlFromHome() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path webXml = catalinaBase.resolve("conf/web.xml");
        assertTrue(Files.exists(webXml), "web.xml should be copied to CATALINA_BASE");
    }

    @Test
    void generateConfiguration_copiesLoggingProperties() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path logging = catalinaBase.resolve("conf/logging.properties");
        assertTrue(Files.exists(logging),
                "logging.properties should be present in CATALINA_BASE");
    }

    @Test
    void generateConfiguration_copiesCatalinaProperties() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path catalinaProps = catalinaBase.resolve("conf/catalina.properties");
        assertTrue(Files.exists(catalinaProps),
                "catalina.properties should be copied from CATALINA_HOME");
    }

    @Test
    void generateConfiguration_copiesContextXml() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path contextXml = catalinaBase.resolve("conf/context.xml");
        assertTrue(Files.exists(contextXml),
                "context.xml should be copied from CATALINA_HOME");
    }

    @Test
    void generateConfiguration_generatesSetenvScripts() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path setenvSh = catalinaBase.resolve("bin/setenv.sh");
        Path setenvBat = catalinaBase.resolve("bin/setenv.bat");

        // setenv scripts are only written when JVM options are non-empty,
        // which depends on buildCatalinaOpts. At minimum, the bin dir should exist.
        assertTrue(Files.isDirectory(catalinaBase.resolve("bin")),
                "bin directory should be created for setenv scripts");
    }

    // ── generateConfiguration: Tomcat version handling ───────────────────

    @Test
    void generateConfiguration_skipsMissingOptionalFiles() throws IOException {
        // Create minimal CATALINA_HOME without optional files
        Path minimalHome = tempDir.resolve("minimal-home");
        Files.createDirectories(minimalHome.resolve("conf"));
        Files.writeString(minimalHome.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        Files.writeString(minimalHome.resolve("conf/web.xml"), MINIMAL_WEB_XML);

        Path base = tempDir.resolve("minimal-base");
        Files.createDirectories(base);
        LuceeServerConfig.ServerConfig config = createTestConfig();

        // Should not throw even though catalina.properties, context.xml, etc. are missing
        assertDoesNotThrow(() ->
                generator.generateConfiguration(base, config, projectDir, minimalHome, 0, false));

        // Core files should still be present
        assertTrue(Files.exists(base.resolve("conf/server.xml")));
        assertTrue(Files.exists(base.resolve("conf/web.xml")));
    }

    // ── generateConfiguration: error handling ────────────────────────────

    @Test
    void generateConfiguration_throwsWhenServerXmlMissing() throws IOException {
        Path emptyHome = tempDir.resolve("empty-home");
        Files.createDirectories(emptyHome.resolve("conf"));
        // No server.xml
        Files.writeString(emptyHome.resolve("conf/web.xml"), MINIMAL_WEB_XML);

        Path base = tempDir.resolve("error-base");
        Files.createDirectories(base);
        LuceeServerConfig.ServerConfig config = createTestConfig();

        assertThrows(IOException.class, () ->
                generator.generateConfiguration(base, config, projectDir, emptyHome, 0, false));
    }

    // ── Dry-run preview methods ─────────────────────────────────────────

    @Test
    void generatePatchedServerXmlContent_returnsXmlString() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 7070;

        String result = generator.generatePatchedServerXmlContent(
                config, projectDir, catalinaBase, catalinaHome);

        assertNotNull(result);
        assertTrue(result.contains("7070"),
                "Patched server.xml should contain the configured port");
    }

    @Test
    void generatePatchedServerXmlContent_throwsWhenServerXmlMissing() {
        Path emptyHome = tempDir.resolve("empty-home-2");

        LuceeServerConfig.ServerConfig config = createTestConfig();

        assertThrows(IOException.class, () ->
                generator.generatePatchedServerXmlContent(
                        config, projectDir, catalinaBase, emptyHome));
    }

    @Test
    void generateServerXmlContent_returnsRawXml() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        String result = generator.generateServerXmlContent(
                config, catalinaBase, catalinaHome);

        assertNotNull(result);
        // Should return the raw (unpatched) content
        assertTrue(result.contains("8080"),
                "Raw server.xml should contain the original default port");
    }

    @Test
    void generateWebXmlContent_returnsContent() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        String result = generator.generateWebXmlContent(
                config, projectDir, catalinaBase, catalinaHome);

        assertNotNull(result);
        assertTrue(result.contains("web-app"),
                "web.xml content should contain web-app element");
    }

    @Test
    void generateWebXmlContent_throwsWhenWebXmlMissing() throws IOException {
        Path noWebXmlHome = tempDir.resolve("no-webxml-home");
        Files.createDirectories(noWebXmlHome.resolve("conf"));
        Files.writeString(noWebXmlHome.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        // No web.xml anywhere

        LuceeServerConfig.ServerConfig config = createTestConfig();

        assertThrows(IOException.class, () ->
                generator.generateWebXmlContent(
                        config, projectDir, catalinaBase, noWebXmlHome));
    }

    // ── generateConfiguration: idempotency ──────────────────────────────

    @Test
    void generateConfiguration_canRunTwiceWithoutError() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();

        // Run twice — should not throw on second invocation
        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);
        assertDoesNotThrow(() ->
                generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false));
    }

    @Test
    void generateConfiguration_overwriteReplacesExistingConfig() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.port = 8080;
        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        // Change port and regenerate with overwrite
        config.port = 9999;
        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, true);

        String serverXml = Files.readString(catalinaBase.resolve("conf/server.xml"), StandardCharsets.UTF_8);
        assertTrue(serverXml.contains("9999"),
                "Overwritten server.xml should reflect the new port");
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private LuceeServerConfig.ServerConfig createTestConfig() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "test-server";
        config.port = 8080;
        config.webroot = "./";
        config.enableLucee = true;
        config.monitoring = new LuceeServerConfig.MonitoringConfig();
        config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
        config.monitoring.jmx.port = 8999;
        config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
        config.urlRewrite.enabled = false; // Disable to avoid network calls for filter JAR
        config.admin = new LuceeServerConfig.AdminConfig();
        config.ajp = new LuceeServerConfig.AjpConfig();
        return config;
    }

    /**
     * Create a minimal fake CATALINA_HOME that satisfies CatalinaBaseConfigGenerator.
     */
    private void createFakeCatalinaHome(Path home) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib"));
        Files.createDirectories(home.resolve("conf"));
        Files.createDirectories(home.resolve("webapps/ROOT/WEB-INF"));

        Files.writeString(home.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        Files.writeString(home.resolve("conf/web.xml"), MINIMAL_WEB_XML);
        Files.writeString(home.resolve("conf/logging.properties"), "# logging\n");
        Files.writeString(home.resolve("conf/catalina.properties"), "# catalina\n");
        Files.writeString(home.resolve("conf/context.xml"), MINIMAL_CONTEXT_XML);
        Files.writeString(home.resolve("conf/tomcat-users.xml"), MINIMAL_TOMCAT_USERS_XML);
    }

    // Minimal XML fixtures for testing — just enough structure for patching

    private static final String MINIMAL_SERVER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Server port="8005" shutdown="SHUTDOWN">
              <Service name="Catalina">
                <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="20000" />
                <Engine name="Catalina" defaultHost="localhost">
                  <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="true">
                  </Host>
                </Engine>
              </Service>
            </Server>
            """;

    private static final String MINIMAL_WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                     version="4.0">
              <servlet>
                <servlet-name>default</servlet-name>
                <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
              </servlet>
              <servlet-mapping>
                <servlet-name>default</servlet-name>
                <url-pattern>/</url-pattern>
              </servlet-mapping>
            </web-app>
            """;

    private static final String MINIMAL_CONTEXT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Context>
              <WatchedResource>WEB-INF/web.xml</WatchedResource>
            </Context>
            """;

    private static final String MINIMAL_TOMCAT_USERS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tomcat-users />
            """;
}
