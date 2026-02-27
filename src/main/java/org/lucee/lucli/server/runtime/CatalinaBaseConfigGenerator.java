package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.TomcatConfigSupport;
import org.lucee.lucli.server.TomcatServerXmlPatcher;
import org.lucee.lucli.server.TomcatWebXmlPatcher;
import org.w3c.dom.Document;

/**
 * Generates a minimal CATALINA_BASE directory structure suitable for use with
 * any CATALINA_HOME — whether that is a Lucee Express distribution or an
 * external vendor Tomcat installation.
 *
 * The generated CATALINA_BASE contains:
 * - conf/server.xml — patched from CATALINA_HOME with ports and contexts
 * - conf/web.xml   — patched with Lucee servlets
 * - conf/logging.properties
 * - bin/setenv.sh / setenv.bat — JVM options from lucee.json
 * - lib/ — Lucee JAR (vendor-tomcat only; Express already has it in HOME/lib)
 * - logs/, temp/, work/
 * - lucee-server/, lucee-web/ — Lucee contexts
 *
 * The project webroot stays clean — all configuration lives at the server level.
 */
public class CatalinaBaseConfigGenerator {

    private final TomcatServerXmlPatcher serverXmlPatcher = new TomcatServerXmlPatcher();
    private final TomcatWebXmlPatcher webXmlPatcher = new TomcatWebXmlPatcher();

    /**
     * Generate CATALINA_BASE configuration from a CATALINA_HOME.
     *
     * @param serverInstanceDir    The CATALINA_BASE directory (~/.lucli/servers/&lt;name&gt;)
     * @param config               Server configuration from lucee.json
     * @param projectDir           Project directory
     * @param catalinaHome         Tomcat installation directory (Express or vendor)
     * @param tomcatMajorVersion   Major version of Tomcat (e.g. 9, 10, 11). Use 0 when unknown
     *                             (e.g. Lucee Express where the bundled version is opaque).
     * @param overwriteProjectConfig Whether to overwrite existing project WEB-INF files
     */
    public void generateConfiguration(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                      Path projectDir, Path catalinaHome,
                                      int tomcatMajorVersion,
                                      boolean overwriteProjectConfig) throws IOException {

        // Create CATALINA_BASE directory structure
        createCatalinaBaseStructure(serverInstanceDir);

        // Create placeholder map for template processing
        Map<String, String> placeholders = TomcatConfigSupport.createPlaceholderMap(serverInstanceDir, config, projectDir);

        // Copy server.xml from CATALINA_HOME and patch it
        copyAndPatchServerXml(catalinaHome, serverInstanceDir, config, projectDir);

        // Copy logging.properties from CATALINA_HOME, or use our template as fallback
        Path vendorLogging = catalinaHome.resolve("conf/logging.properties");
        Path targetLogging = serverInstanceDir.resolve("conf/logging.properties");
        if (Files.exists(vendorLogging)) {
            Files.copy(vendorLogging, targetLogging, StandardCopyOption.REPLACE_EXISTING);
        } else {
            TomcatConfigSupport.applyTemplate("tomcat_template/conf/logging.properties", targetLogging, placeholders);
        }

        // Copy essential config files from CATALINA_HOME
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/catalina.properties");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/catalina.policy");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/context.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/tomcat-users.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/jaspic-providers.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/web.xml");

        // Patch the server's web.xml to add Lucee servlets (CFMLServlet, RESTServlet)
        Path serverWebXml = serverInstanceDir.resolve("conf/web.xml");
        webXmlPatcher.ensureLuceeServlets(serverWebXml, config, serverInstanceDir);

        // Apply configuration-driven patches (disable Lucee/REST servlets when
        // enableLucee=false, protect lucee.json, etc.). This must run after
        // ensureLuceeServlets so that vendor web.xml files that already ship
        // with Lucee servlets (e.g. Lucee Express) have them removed when the
        // user explicitly sets enableLucee=false.
        webXmlPatcher.patch(serverWebXml, config, projectDir, serverInstanceDir);

        // Configure URL rewrite via Tomcat's built-in RewriteValve.
        // This works across all Tomcat versions (8-11) with no external dependencies.
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            configureRewriteValve(serverInstanceDir, config, projectDir, placeholders);
        }

        // Generate setenv scripts (setenv.sh / setenv.bat) for JVM options
        TomcatConfigSupport.writeSetenvScripts(serverInstanceDir, config, projectDir);
    }

    // ── Dry-run / preview helpers ────────────────────────────────────────

    /**
     * Generate the patched server.xml content without any filesystem side effects.
     * Intended for {@code lucli server start --dry-run --include-tomcat-server}.
     */
    public String generatePatchedServerXmlContent(LuceeServerConfig.ServerConfig config,
                                                  Path projectDir,
                                                  Path serverInstanceDir,
                                                  Path catalinaHome) throws IOException {
        Path vendorServerXml = catalinaHome.resolve("conf/server.xml");
        if (!Files.exists(vendorServerXml)) {
            throw new IOException("server.xml not found in distribution at " + catalinaHome);
        }
        String vendor = Files.readString(vendorServerXml, StandardCharsets.UTF_8);
        return serverXmlPatcher.patchContent(vendor, config, projectDir, serverInstanceDir, false);
    }

    /**
     * Generate web.xml content as a string without writing to disk.
     * Used for dry-run preview.
     */
    public String generateWebXmlContent(LuceeServerConfig.ServerConfig config,
                                        Path projectDir,
                                        Path serverInstanceDir,
                                        Path catalinaHome) throws IOException {
        // Try webapps/ROOT/WEB-INF/web.xml first, then conf/web.xml
        Path vendorWebXml = catalinaHome.resolve("webapps/ROOT/WEB-INF/web.xml");
        if (!Files.exists(vendorWebXml)) {
            vendorWebXml = catalinaHome.resolve("conf/web.xml");
        }
        if (!Files.exists(vendorWebXml)) {
            throw new IOException("web.xml not found in distribution at " + catalinaHome);
        }
        return Files.readString(vendorWebXml, StandardCharsets.UTF_8);
    }

    /**
     * Load the raw (unpatched) server.xml content from CATALINA_HOME.
     */
    public String generateServerXmlContent(LuceeServerConfig.ServerConfig config,
                                           Path serverInstanceDir,
                                           Path catalinaHome) throws IOException {
        Path vendorServerXml = catalinaHome.resolve("conf/server.xml");
        if (!Files.exists(vendorServerXml)) {
            throw new IOException("server.xml not found in distribution at " + catalinaHome);
        }
        return Files.readString(vendorServerXml, StandardCharsets.UTF_8);
    }

    /**
     * Create the CATALINA_BASE directory structure.
     */
    private void createCatalinaBaseStructure(Path serverInstanceDir) throws IOException {
        // Create required directories
        Files.createDirectories(serverInstanceDir.resolve("conf"));
        Files.createDirectories(serverInstanceDir.resolve("conf/Catalina/localhost"));
        Files.createDirectories(serverInstanceDir.resolve("logs"));
        Files.createDirectories(serverInstanceDir.resolve("temp"));
        Files.createDirectories(serverInstanceDir.resolve("work"));
        Files.createDirectories(serverInstanceDir.resolve("webapps"));
        Files.createDirectories(serverInstanceDir.resolve("lucee-server"));
        Files.createDirectories(serverInstanceDir.resolve("lucee-web"));
    }

    /**
     * Copy server.xml from CATALINA_HOME and patch it with LuCLI configuration.
     */
    private void copyAndPatchServerXml(Path catalinaHome, Path serverInstanceDir,
                                       LuceeServerConfig.ServerConfig config,
                                       Path projectDir) throws IOException {
        Path vendorServerXml = catalinaHome.resolve("conf/server.xml");
        Path targetServerXml = serverInstanceDir.resolve("conf/server.xml");

        if (!Files.exists(vendorServerXml)) {
            throw new IOException("Vendor server.xml not found: " + vendorServerXml +
                    "\nCannot configure external Tomcat without server.xml");
        }

        // Copy vendor server.xml to CATALINA_BASE
        Files.copy(vendorServerXml, targetServerXml, StandardCopyOption.REPLACE_EXISTING);

        // Patch it with our configuration (ports, context, etc.)
        serverXmlPatcher.patch(targetServerXml, config, projectDir, serverInstanceDir);

        System.out.println("Copied and patched server.xml from: " + vendorServerXml);
    }

    /**
     * Copy a config file from CATALINA_HOME to CATALINA_BASE.
     */
    private void copyFromCatalinaHome(Path catalinaHome, Path catalinaBase, String relativePath)
            throws IOException {
        Path source = catalinaHome.resolve(relativePath);
        Path target = catalinaBase.resolve(relativePath);

        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    /**
     * Configure URL rewriting via Tomcat's built-in RewriteValve.
     *
     * This uses the Host-level RewriteValve which reads rewrite.config from
     * {@code conf/Catalina/<hostName>/rewrite.config} in CATALINA_BASE.
     *
     * The project webroot stays completely clean — no WEB-INF modifications.
     * Works across all Tomcat versions (8, 9, 10, 11) with no external dependencies.
     *
     * If HTTPS redirect is also enabled, the redirect rules are prepended
     * to the same rewrite.config file.
     */
    private void configureRewriteValve(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                       Path projectDir, Map<String, String> placeholders) throws IOException {
        if (!config.enableLucee || config.urlRewrite == null || !config.urlRewrite.enabled) {
            return;
        }

        // Warn about legacy urlrewrite.xml if present
        Path legacyUrlRewriteXml = projectDir.resolve("urlrewrite.xml");
        if (Files.exists(legacyUrlRewriteXml)) {
            System.out.println("\n\u26a0\ufe0f  Found urlrewrite.xml in project root.");
            System.out.println("   LuCLI now uses Tomcat's built-in RewriteValve with rewrite.config (mod_rewrite syntax).");
            System.out.println("   Your urlrewrite.xml (Tuckey format) is no longer used.");
            System.out.println("   To migrate, create a rewrite.config with equivalent mod_rewrite rules.");
            System.out.println("   See: https://tomcat.apache.org/tomcat-9.0-doc/rewrite.html\n");
        }

        // Parse the patched server.xml to determine the Host name and ensure RewriteValve
        Path serverXmlPath = serverInstanceDir.resolve("conf/server.xml");
        Document serverXmlDoc = parseServerXml(serverXmlPath);

        // Ensure RewriteValve is declared in server.xml
        if (serverXmlDoc != null) {
            serverXmlPatcher.ensureRewriteValve(serverXmlDoc);
            writeDocument(serverXmlDoc, serverXmlPath);
        }

        // Determine the rewrite.config target directory
        Path rewriteDir = serverXmlPatcher.getRewriteConfigDir(serverXmlDoc, serverInstanceDir);
        Files.createDirectories(rewriteDir);

        // Build the rewrite rules
        StringBuilder rules = new StringBuilder();

        // If HTTPS redirect is enabled, prepend those rules
        if (LuceeServerConfig.isHttpsEnabled(config) && LuceeServerConfig.isHttpsRedirectEnabled(config)) {
            rules.append(serverXmlPatcher.buildHttpsRedirectRules(config));
            rules.append("\n");
        }

        // Resolve user-provided rewrite.config from project
        String configFileName = (config.urlRewrite.configFile != null && !config.urlRewrite.configFile.isEmpty())
                ? config.urlRewrite.configFile
                : "rewrite.config";
        Path sourceRewriteConfig = projectDir.resolve(configFileName);

        if (Files.exists(sourceRewriteConfig)) {
            // User has their own rewrite.config — append it
            rules.append(Files.readString(sourceRewriteConfig, StandardCharsets.UTF_8));
            System.out.println("Deployed rewrite.config from: " + sourceRewriteConfig);
        } else {
            // Generate default rules from template
            rules.append(loadAndProcessTemplate("rewrite_template/rewrite.config", placeholders));
            System.out.println("Generated default rewrite.config (RewriteValve)");
            System.out.println("  \uD83D\uDCA1 Create " + configFileName + " in your project to customize rewrite rules.");
        }

        // Write combined rewrite.config
        Files.writeString(rewriteDir.resolve("rewrite.config"), rules.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Parse a server.xml file into a DOM Document.
     */
    private Document parseServerXml(Path serverXmlPath) {
        if (serverXmlPath == null || !Files.exists(serverXmlPath)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(serverXmlPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        } catch (Exception e) {
            System.err.println("Warning: Could not parse server.xml for RewriteValve configuration: " + e.getMessage());
            return null;
        }
    }

    /**
     * Write a DOM Document back to the given path.
     */
    private void writeDocument(Document document, Path path) throws IOException {
        try {
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");

            try (java.io.OutputStream out = Files.newOutputStream(path)) {
                transformer.transform(
                        new javax.xml.transform.dom.DOMSource(document),
                        new javax.xml.transform.stream.StreamResult(out));
            }
        } catch (javax.xml.transform.TransformerException e) {
            throw new IOException("Failed to write server.xml: " + e.getMessage(), e);
        }
    }

    /**
     * Load a template from the classpath, apply placeholders, and return the result.
     */
    private String loadAndProcessTemplate(String templateResourcePath,
                                          Map<String, String> placeholders) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templateResourcePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue());
            }
            return content;
        }
    }
}
