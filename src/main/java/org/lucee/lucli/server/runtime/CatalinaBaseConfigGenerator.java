package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.TomcatConfigSupport;
import org.lucee.lucli.server.TomcatServerXmlPatcher;
import org.lucee.lucli.server.TomcatWebXmlPatcher;

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

        // Configure URL rewrite at server level (copies urlrewrite.xml, deploys JAR)
        // Note: UrlRewriteFilter uses javax.servlet and is not compatible with Tomcat 10+
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            if (tomcatMajorVersion >= 10) {
                System.out.println("\n\u26a0\ufe0f  URL Rewriting is not yet supported with Tomcat " + tomcatMajorVersion + ".x");
                System.out.println("   UrlRewriteFilter uses javax.servlet which is incompatible with Tomcat 10+.");
                System.out.println("   Alternatives:");
                System.out.println("     - Use Tomcat's built-in RewriteValve (Apache mod_rewrite syntax)");
                System.out.println("     - Use OCPsoft Rewrite (org.ocpsoft.rewrite:rewrite-servlet:10.0.1.Final)");
                System.out.println("   Skipping URL rewrite configuration.\n");
            } else {
                configureUrlRewrite(serverInstanceDir, config, projectDir, placeholders);
                webXmlPatcher.ensureUrlRewriteFilter(serverWebXml, serverInstanceDir);
            }
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
     * Configure URL rewrite at the server level.
     *
     * This deploys URL rewrite to CATALINA_BASE instead of the project's WEB-INF:
     * - Copies urlrewrite.xml from project to CATALINA_BASE/conf/
     * - Deploys UrlRewriteFilter JAR to CATALINA_BASE/lib/
     * - Filter is configured in server's web.xml via TomcatWebXmlPatcher
     *
     * This keeps the project webroot completely clean.
     *
     * Note: Only works with Tomcat 9 and below (javax.servlet).
     */
    private void configureUrlRewrite(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                     Path projectDir, Map<String, String> placeholders) throws IOException {
        // Only needed if URL rewrite is enabled
        if (!config.enableLucee || config.urlRewrite == null || !config.urlRewrite.enabled) {
            return;
        }

        // Resolve source urlrewrite.xml from project
        String configFileName = (config.urlRewrite.configFile != null && !config.urlRewrite.configFile.isEmpty())
                ? config.urlRewrite.configFile
                : "urlrewrite.xml";
        Path sourceUrlRewriteXml = projectDir.resolve(configFileName);

        // Target is CATALINA_BASE/conf/urlrewrite.xml
        Path targetUrlRewriteXml = serverInstanceDir.resolve("conf/urlrewrite.xml");

        if (Files.exists(sourceUrlRewriteXml)) {
            Files.copy(sourceUrlRewriteXml, targetUrlRewriteXml, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Deployed urlrewrite.xml from: " + sourceUrlRewriteXml);
        } else {
            TomcatConfigSupport.applyTemplate("tomcat_template/webapps/ROOT/WEB-INF/urlrewrite.xml",
                    targetUrlRewriteXml, placeholders);
            System.out.println("Generated default urlrewrite.xml: " + targetUrlRewriteXml);
            System.out.println("  \uD83D\uDCA1 Create " + configFileName + " in your project to customize rewrite rules.");
        }

        // Deploy UrlRewriteFilter JAR to CATALINA_BASE/lib/
        String urlRewriteVersion = TomcatConfigSupport.getUrlRewriteVersion();
        try {
            Path serverLibDir = serverInstanceDir.resolve("lib");
            Files.createDirectories(serverLibDir);

            Path urlRewriteJar = TomcatConfigSupport.ensureUrlRewriteFilter();
            Path targetJar = serverLibDir.resolve("urlrewritefilter-" + urlRewriteVersion + ".jar");
            if (!Files.exists(targetJar)) {
                Files.copy(urlRewriteJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Deployed UrlRewriteFilter " + urlRewriteVersion + " to: " + targetJar);
            }
        } catch (Exception e) {
            throw new IOException("Failed to deploy UrlRewriteFilter JAR: " + e.getMessage(), e);
        }
    }
}
