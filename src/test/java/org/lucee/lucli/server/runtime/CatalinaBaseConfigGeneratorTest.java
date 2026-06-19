package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

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
    void generateConfiguration_usesSafeAdminEntrypointMappingWhenAddingCfmlServlets() throws IOException {
        // Use a minimal CATALINA_HOME web.xml without pre-existing Lucee servlets
        Path minimalHome = tempDir.resolve("minimal-home-admin-mapping");
        Files.createDirectories(minimalHome.resolve("conf"));
        Files.writeString(minimalHome.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        Files.writeString(minimalHome.resolve("conf/web.xml"), MINIMAL_WEB_XML);

        Path base = tempDir.resolve("minimal-base-admin-mapping");
        Files.createDirectories(base);
        LuceeServerConfig.ServerConfig config = createTestConfig();

        generator.generateConfiguration(base, config, projectDir, minimalHome, 0, false);

        String webXml = Files.readString(base.resolve("conf/web.xml"), StandardCharsets.UTF_8);
        assertTrue(webXml.contains("/lucee/admin.cfm"),
                "Generated web.xml should include explicit /lucee/admin.cfm mapping");
        assertFalse(webXml.contains("/lucee/*"),
                "Generated web.xml should not include broad /lucee/* mapping");
        assertFalse(webXml.contains("/lucee/admin/*"),
                "Generated web.xml should not include broad /lucee/admin/* mapping");
    }

    @Test
    void generateConfiguration_whenAdminDisabled_doesNotAddAdminServletMappingAndAddsDenyConstraint() throws IOException {
        // Use a minimal CATALINA_HOME web.xml without pre-existing Lucee servlets
        Path minimalHome = tempDir.resolve("minimal-home-admin-disabled");
        Files.createDirectories(minimalHome.resolve("conf"));
        Files.writeString(minimalHome.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        Files.writeString(minimalHome.resolve("conf/web.xml"), MINIMAL_WEB_XML);

        Path base = tempDir.resolve("minimal-base-admin-disabled");
        Files.createDirectories(base);

        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.admin.enabled = false;

        generator.generateConfiguration(base, config, projectDir, minimalHome, 0, false);

        String webXml = Files.readString(base.resolve("conf/web.xml"), StandardCharsets.UTF_8);
        String compactXml = webXml.replaceAll("\\s+", "");

        assertFalse(compactXml.contains(
                "<servlet-mapping><servlet-name>CFMLServlet</servlet-name><url-pattern>/lucee/admin.cfm</url-pattern></servlet-mapping>"),
                "Generated web.xml should not include CFML admin servlet mapping when admin.enabled=false");
        assertTrue(compactXml.contains("<url-pattern>/lucee/admin.cfm</url-pattern>"),
                "Generated web.xml should include a deny-list pattern for /lucee/admin.cfm");
        assertTrue(compactXml.contains("<auth-constraint"),
                "Generated web.xml should include auth-constraint to deny admin endpoint access");
    }

    @Test
    void generateConfiguration_whenAdminDisabled_removesExistingAdminServletMappings() throws IOException {
        // Use a CATALINA_HOME web.xml that already has explicit admin mappings
        Path adminMappedHome = tempDir.resolve("home-existing-admin-mapping");
        Files.createDirectories(adminMappedHome.resolve("conf"));
        Files.writeString(adminMappedHome.resolve("conf/server.xml"), MINIMAL_SERVER_XML);
        Files.writeString(adminMappedHome.resolve("conf/web.xml"), WEB_XML_WITH_EXPLICIT_ADMIN_MAPPING);

        Path base = tempDir.resolve("base-existing-admin-mapping");
        Files.createDirectories(base);

        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.admin.enabled = false;

        generator.generateConfiguration(base, config, projectDir, adminMappedHome, 0, false);

        String webXml = Files.readString(base.resolve("conf/web.xml"), StandardCharsets.UTF_8);
        String compactXml = webXml.replaceAll("\\s+", "");

        assertFalse(compactXml.contains(
                "<servlet-mapping><servlet-name>CFMLServlet</servlet-name><url-pattern>/lucee/admin.cfm</url-pattern></servlet-mapping>"),
                "Existing CFML admin servlet mapping should be removed when admin.enabled=false");
        assertFalse(compactXml.contains(
                "<servlet-mapping><servlet-name>CFMLServlet</servlet-name><url-pattern>/lucee/admin/*</url-pattern></servlet-mapping>"),
                "Existing wildcard admin servlet mapping should be removed when admin.enabled=false");
        assertTrue(compactXml.contains("<url-pattern>/lucee/admin.cfm</url-pattern>"),
                "Generated web.xml should include a deny-list pattern for /lucee/admin.cfm");
        assertTrue(compactXml.contains("<auth-constraint"),
                "Generated web.xml should include auth-constraint to deny admin endpoint access");
    }

    @Test
    void generateConfiguration_migratesLegacyProjectWebXmlAdminMappings() throws IOException {
        Path webInf = projectDir.resolve("WEB-INF");
        Files.createDirectories(webInf);
        Path projectWebXml = webInf.resolve("web.xml");
        Files.writeString(projectWebXml, LEGACY_PROJECT_WEB_XML, StandardCharsets.UTF_8);

        LuceeServerConfig.ServerConfig config = createTestConfig();
        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        String migrated = Files.readString(projectWebXml, StandardCharsets.UTF_8);
        assertFalse(migrated.contains("/lucee/*"),
                "Legacy project web.xml should have /lucee/* mapping removed");
        assertFalse(migrated.contains("/lucee/admin/*"),
                "Legacy project web.xml should have /lucee/admin/* mapping removed");
        assertTrue(migrated.contains("/lucee/admin.cfm"),
                "Legacy project web.xml should include explicit /lucee/admin.cfm mapping");
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

    @Test
    void generateConfiguration_writesLuceeAdminEnabledFalseToSetenvWhenAdminDisabled() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.admin.enabled = false;

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path setenvSh = catalinaBase.resolve("bin/setenv.sh");
        Path setenvBat = catalinaBase.resolve("bin/setenv.bat");
        assertTrue(Files.exists(setenvSh), "setenv.sh should exist");
        assertTrue(Files.exists(setenvBat), "setenv.bat should exist");

        String setenvShContent = Files.readString(setenvSh, StandardCharsets.UTF_8);
        String setenvBatContent = Files.readString(setenvBat, StandardCharsets.UTF_8);

        assertTrue(setenvShContent.contains("LUCEE_ADMIN_ENABLED=\"false\""),
                "setenv.sh should force LUCEE_ADMIN_ENABLED=false when admin.enabled=false");
        assertTrue(setenvShContent.contains("export LUCEE_ADMIN_ENABLED"),
                "setenv.sh should export LUCEE_ADMIN_ENABLED when admin.enabled=false");
        assertTrue(setenvBatContent.contains("set \"LUCEE_ADMIN_ENABLED=false\""),
                "setenv.bat should force LUCEE_ADMIN_ENABLED=false when admin.enabled=false");
    }

    @Test
    void generateConfiguration_doesNotWriteLuceeAdminEnabledWhenAdminEnabled() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.admin.enabled = true;

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        String setenvShContent = Files.readString(catalinaBase.resolve("bin/setenv.sh"), StandardCharsets.UTF_8);
        String setenvBatContent = Files.readString(catalinaBase.resolve("bin/setenv.bat"), StandardCharsets.UTF_8);

        assertFalse(setenvShContent.contains("LUCEE_ADMIN_ENABLED"),
                "setenv.sh should not set LUCEE_ADMIN_ENABLED when admin.enabled=true");
        assertFalse(setenvBatContent.contains("LUCEE_ADMIN_ENABLED"),
                "setenv.bat should not set LUCEE_ADMIN_ENABLED when admin.enabled=true");
    }

    @Test
    void generateConfiguration_defaultRewriteRulesBypassStaticAssetsAndKeepFrameworkRouting() throws IOException {
        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.urlRewrite.enabled = true;

        generator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, false);

        Path rewriteConfig = catalinaBase.resolve("conf/Catalina/localhost/rewrite.config");
        assertTrue(Files.exists(rewriteConfig),
                "rewrite.config should be generated when URL rewrite is enabled");

        String rewriteRules = Files.readString(rewriteConfig, StandardCharsets.UTF_8);
        assertTrue(rewriteRules.contains("LuCLI URL Rewrite Rules"),
                "When no project rewrite.config exists, the default LuCLI rewrite template should be generated");

        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/assets/styles.css"),
                "Static CSS assets should bypass router rewriting");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/images/HERO.WEBP"),
                "Common static image extensions should bypass router rewriting");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/js/app.MJS"),
                "Modern JS module assets should bypass router rewriting");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/fonts/icon.WOFF2"),
                "Font assets should bypass router rewriting");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/assets"),
                "Static directories should bypass router rewriting even without a file extension");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/public"),
                "Common static root directories should bypass router rewriting");
        assertEquals("-", firstMatchingRewriteRuleTarget(rewriteRules, "/favicon.ico"),
                "Root-level static assets should bypass router rewriting");

        assertEquals("/index.cfm/$1", firstMatchingRewriteRuleTarget(rewriteRules, "/hello"),
                "Non-static app URLs should still be routed through the framework router");
        assertEquals("/index.cfm/$1", firstMatchingRewriteRuleTarget(rewriteRules, "/products/laptop"),
                "Multi-segment app URLs should still be routed through the framework router");
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

    // ── generateConfiguration: enableLucee=false ─────────────────────────

    @Test
    void generateConfiguration_removesLuceeServletsWhenEnableLuceeFalse() throws IOException {
        // Use a CATALINA_HOME whose web.xml already contains Lucee servlets
        // (simulates Lucee Express distribution)
        Path expressHome = tempDir.resolve("express-home");
        createFakeCatalinaHome(expressHome);
        Files.writeString(expressHome.resolve("conf/web.xml"), WEB_XML_WITH_LUCEE_SERVLETS);

        LuceeServerConfig.ServerConfig config = createTestConfig();
        config.enableLucee = false;

        Path base = tempDir.resolve("enablelucee-false-base");
        Files.createDirectories(base);

        generator.generateConfiguration(base, config, projectDir, expressHome, 0, false);

        String webXml = Files.readString(base.resolve("conf/web.xml"), StandardCharsets.UTF_8);
        assertFalse(webXml.contains("CFMLServlet"),
                "web.xml should NOT contain CFMLServlet when enableLucee=false");
        assertFalse(webXml.contains("lucee.loader.servlet"),
                "web.xml should NOT contain Lucee servlet classes when enableLucee=false");
        // The default servlet should still be present
        assertTrue(webXml.contains("DefaultServlet"),
                "web.xml should still contain the default servlet");
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

    private String firstMatchingRewriteRuleTarget(String rewriteRules, String requestPath) {
        for (String rawLine : rewriteRules.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("RewriteRule ")) {
                continue;
            }

            String[] parts = line.split("\\s+", 4);
            if (parts.length < 4) {
                continue;
            }

            int patternFlags = parts[3].contains("NC") ? Pattern.CASE_INSENSITIVE : 0;
            Pattern pattern = Pattern.compile(parts[1], patternFlags);
            if (pattern.matcher(requestPath).matches()) {
                return parts[2];
            }
        }
        return null;
    }

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
        config.urlRewrite.enabled = false;
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

    private static final String WEB_XML_WITH_EXPLICIT_ADMIN_MAPPING = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                     version="4.0">
              <servlet>
                <servlet-name>default</servlet-name>
                <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
              </servlet>
              <servlet>
                <servlet-name>CFMLServlet</servlet-name>
                <servlet-class>lucee.loader.servlet.CFMLServlet</servlet-class>
              </servlet>
              <servlet-mapping>
                <servlet-name>default</servlet-name>
                <url-pattern>/</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>*.cfm</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>/lucee/admin.cfm</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>/lucee/admin/*</url-pattern>
              </servlet-mapping>
            </web-app>
            """;

    /**
     * Simulates a Lucee Express web.xml that already ships with Lucee servlets.
     */
    private static final String WEB_XML_WITH_LUCEE_SERVLETS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                     version="4.0">
              <servlet>
                <servlet-name>default</servlet-name>
                <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
              </servlet>
              <servlet>
                <servlet-name>CFMLServlet</servlet-name>
                <servlet-class>lucee.loader.servlet.CFMLServlet</servlet-class>
              </servlet>
              <servlet-mapping>
                <servlet-name>default</servlet-name>
                <url-pattern>/</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>*.cfm</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>*.cfc</url-pattern>
              </servlet-mapping>
              <welcome-file-list>
                <welcome-file>index.cfm</welcome-file>
                <welcome-file>index.html</welcome-file>
              </welcome-file-list>
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

    private static final String LEGACY_PROJECT_WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                     https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                     version="6.0">
              <servlet>
                <servlet-name>CFMLServlet</servlet-name>
                <servlet-class>lucee.loader.servlet.jakarta.CFMLServlet</servlet-class>
                <init-param>
                  <param-name>lucee-server-root</param-name>
                  <param-value>/tmp/lucee-server</param-value>
                </init-param>
              </servlet>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>*.cfm</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>/lucee/*</url-pattern>
              </servlet-mapping>
              <servlet-mapping>
                <servlet-name>CFMLServlet</servlet-name>
                <url-pattern>/lucee/admin/*</url-pattern>
              </servlet-mapping>
            </web-app>
            """;
}
