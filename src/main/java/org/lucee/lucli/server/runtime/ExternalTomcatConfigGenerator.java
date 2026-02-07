package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.TomcatServerXmlPatcher;
import org.lucee.lucli.server.TomcatWebXmlPatcher;

/**
 * Generates Tomcat configuration for external Tomcat installations.
 *
 * Unlike the standard TomcatConfigGenerator which copies an entire Lucee Express
 * distribution, this generator creates a minimal CATALINA_BASE structure suitable
 * for use with an external CATALINA_HOME.
 *
 * The generated CATALINA_BASE contains:
 * - conf/server.xml - Tomcat server configuration with ports and contexts (patched from vendor)
 * - conf/web.xml - Global web.xml with Lucee servlets (patched from vendor)
 * - conf/logging.properties - Logging configuration
 * - bin/setenv.sh / setenv.bat - JVM options
 * - lib/lucee-{version}.jar - Lucee JAR
 * - logs/ - Log directory
 * - temp/ - Temp directory
 * - work/ - Work directory for compiled JSPs
 * - lucee-server/ - Lucee server context
 * - lucee-web/ - Lucee web context
 *
 * The project webroot stays clean - all configuration is at the server level:
 * - Lucee servlets configured in CATALINA_BASE/conf/web.xml
 * - URL rewrite (if enabled) configured in CATALINA_BASE/conf/urlrewrite.xml
 * - UrlRewriteFilter JAR in CATALINA_BASE/lib/
 *
 * User can provide urlrewrite.xml in project root (or custom path via urlRewrite.configFile);
 * LuCLI will copy it to the server on startup.
 */
public class ExternalTomcatConfigGenerator {

    // UrlRewriteFilter for javax.servlet (Tomcat 9 and below)
    // Note: No Jakarta-compatible version available on Maven Central yet
    private static final String URLREWRITE_VERSION = "5.1.3";
    private static final String URLREWRITE_MAVEN_URL =
            "https://repo1.maven.org/maven2/org/tuckey/urlrewritefilter/" + URLREWRITE_VERSION +
                    "/urlrewritefilter-" + URLREWRITE_VERSION + ".jar";

    private final TomcatServerXmlPatcher serverXmlPatcher = new TomcatServerXmlPatcher();
    private final TomcatWebXmlPatcher webXmlPatcher = new TomcatWebXmlPatcher();

    /**
     * Generate CATALINA_BASE configuration for external Tomcat.
     *
     * @param serverInstanceDir The CATALINA_BASE directory (~/.lucli/servers/<name>)
     * @param config Server configuration from lucee.json
     * @param projectDir Project directory
     * @param catalinaHome External Tomcat installation directory
     * @param tomcatMajorVersion The major version of Tomcat (e.g., 9, 10, 11)
     * @param overwriteProjectConfig Whether to overwrite existing project WEB-INF files
     */
    public void generateConfiguration(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                      Path projectDir, Path catalinaHome,
                                      int tomcatMajorVersion,
                                      boolean overwriteProjectConfig) throws IOException {

        // Create CATALINA_BASE directory structure
        createCatalinaBaseStructure(serverInstanceDir);

        // Create placeholder map for template processing
        Map<String, String> placeholders = createPlaceholderMap(serverInstanceDir, config, projectDir);

        // Copy server.xml from vendor (CATALINA_HOME) and patch it
        copyAndPatchServerXml(catalinaHome, serverInstanceDir, config, projectDir);

        // Copy logging.properties from vendor, or use our template as fallback
        Path vendorLogging = catalinaHome.resolve("conf/logging.properties");
        Path targetLogging = serverInstanceDir.resolve("conf/logging.properties");
        if (Files.exists(vendorLogging)) {
            Files.copy(vendorLogging, targetLogging, StandardCopyOption.REPLACE_EXISTING);
        } else {
            applyTemplate("tomcat_template/conf/logging.properties", targetLogging, placeholders);
        }

        // Copy essential config files from external Tomcat
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/catalina.properties");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/catalina.policy");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/context.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/tomcat-users.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/jaspic-providers.xml");
        copyFromCatalinaHome(catalinaHome, serverInstanceDir, "conf/web.xml"); // Global web.xml

        // Patch the server's web.xml to add Lucee servlets (CFMLServlet, RESTServlet)
        Path serverWebXml = serverInstanceDir.resolve("conf/web.xml");
        webXmlPatcher.ensureLuceeServlets(serverWebXml, config, serverInstanceDir);

        // Configure URL rewrite at server level (copies urlrewrite.xml, deploys JAR)
        // Note: UrlRewriteFilter uses javax.servlet and is not compatible with Tomcat 10+
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            if (tomcatMajorVersion >= 10) {
                System.out.println("\n⚠️  URL Rewriting is not yet supported with Tomcat " + tomcatMajorVersion + ".x");
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
        writeSetenvScripts(serverInstanceDir, config, projectDir);
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
     * Create placeholder map for template processing.
     */
    private Map<String, String> createPlaceholderMap(Path serverInstanceDir,
                                                     LuceeServerConfig.ServerConfig config,
                                                     Path projectDir) {
        Map<String, String> placeholders = new HashMap<>();

        Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        Path luceeServerPath = serverInstanceDir.resolve("lucee-server");
        Path luceeWebPath = serverInstanceDir.resolve("lucee-web");
        Path luceePatchesPath = getLucliHome().resolve("patches");
        int shutdownPort = LuceeServerConfig.getEffectiveShutdownPort(config);

        placeholders.put("${httpPort}", String.valueOf(config.port));
        placeholders.put("${shutdownPort}", String.valueOf(shutdownPort));
        placeholders.put("${jmxPort}", String.valueOf(config.monitoring.jmx.port));
        placeholders.put("${webroot}", webroot.toAbsolutePath().toString());
        placeholders.put("${luceeServerPath}", luceeServerPath.toAbsolutePath().toString());
        placeholders.put("${luceeWebPath}", luceeWebPath.toAbsolutePath().toString());
        placeholders.put("${luceePatches}", luceePatchesPath.toAbsolutePath().toString());
        placeholders.put("${jvmRoute}", config.name);
        placeholders.put("${logLevel}", "INFO");

        String routerFile = (config.urlRewrite != null && config.urlRewrite.routerFile != null)
                ? config.urlRewrite.routerFile
                : "index.cfm";
        placeholders.put("${routerFile}", routerFile);

        return placeholders;
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
            // Copy user's urlrewrite.xml to server conf
            Files.copy(sourceUrlRewriteXml, targetUrlRewriteXml, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Deployed urlrewrite.xml from: " + sourceUrlRewriteXml);
        } else {
            // Generate default urlrewrite.xml from template
            applyTemplate("tomcat_template/webapps/ROOT/WEB-INF/urlrewrite.xml",
                    targetUrlRewriteXml, placeholders);
            System.out.println("Generated default urlrewrite.xml: " + targetUrlRewriteXml);
            System.out.println("  💡 Create " + configFileName + " in your project to customize rewrite rules.");
        }

        // Deploy UrlRewriteFilter JAR to CATALINA_BASE/lib/
        try {
            Path serverLibDir = serverInstanceDir.resolve("lib");
            Files.createDirectories(serverLibDir);

            Path urlRewriteJar = ensureUrlRewriteFilter();
            Path targetJar = serverLibDir.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
            if (!Files.exists(targetJar)) {
                Files.copy(urlRewriteJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Deployed UrlRewriteFilter " + URLREWRITE_VERSION + " to: " + targetJar);
            }
        } catch (Exception e) {
            throw new IOException("Failed to deploy UrlRewriteFilter JAR: " + e.getMessage(), e);
        }
    }

    /**
     * Apply a template file by loading from resources and replacing placeholders.
     */
    private void applyTemplate(String templateResourcePath, Path outputPath, Map<String, String> placeholders)
            throws IOException {
        String templateContent;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templateResourcePath);
            }
            templateContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            templateContent = templateContent.replace(entry.getKey(), entry.getValue());
        }

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, templateContent, StandardCharsets.UTF_8);
    }

    /**
     * Ensure UrlRewriteFilter JAR is available.
     */
    private Path ensureUrlRewriteFilter() throws Exception {
        Path lucliHome = getLucliHome();
        Path dependenciesDir = lucliHome.resolve("dependencies");
        Files.createDirectories(dependenciesDir);

        Path jarFile = dependenciesDir.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
        if (Files.exists(jarFile)) {
            return jarFile;
        }

        System.out.println("Downloading UrlRewriteFilter " + URLREWRITE_VERSION + "...");
        downloadFile(URLREWRITE_MAVEN_URL, jarFile);
        System.out.println("UrlRewriteFilter downloaded successfully.");

        return jarFile;
    }

    /**
     * Download a file from URL.
     */
    private void downloadFile(String urlString, Path destinationFile) throws IOException {
        Files.createDirectories(destinationFile.getParent());

        URL url = new URL(urlString);
        try (InputStream in = url.openStream();
             OutputStream out = Files.newOutputStream(destinationFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Write Tomcat setenv scripts (setenv.sh / setenv.bat) into the server
     * instance's bin directory so that JVM options derived from lucee.json are
     * available when Tomcat starts.
     *
     * For external Tomcat, CATALINA_BASE/bin/setenv.sh takes precedence over
     * CATALINA_HOME/bin/setenv.sh, allowing per-server JVM configuration.
     */
    private void writeSetenvScripts(Path serverInstanceDir,
                                    LuceeServerConfig.ServerConfig config,
                                    Path projectDir) throws IOException {
        Path binDir = serverInstanceDir.resolve("bin");
        Files.createDirectories(binDir);

        // Build JVM options using LuceeServerManager's logic
        List<String> opts;
        try {
            LuceeServerManager manager = new LuceeServerManager();
            opts = manager.buildCatalinaOpts(config, null, projectDir);
        } catch (Exception e) {
            throw new IOException("Failed to build JVM options for setenv scripts: " + e.getMessage(), e);
        }

        if (opts == null || opts.isEmpty()) {
            return; // Nothing to persist
        }

        String joinedOpts = String.join(" ", opts);

        // --- setenv.sh ---
        Path setenvSh = binDir.resolve("setenv.sh");
        String escapedShOpts = joinedOpts.replace("\"", "\\\"");
        StringBuilder sh = new StringBuilder();
        sh.append("#!/bin/sh\n");
        sh.append("# Auto-generated by LuCLI from lucee.json. Manual changes may be overwritten.\n");
        sh.append("BASE_CATALINA_OPTS=\"").append(escapedShOpts).append("\"\n");
        sh.append("if [ -z \"$CATALINA_OPTS\" ]; then\n");
        sh.append("  CATALINA_OPTS=\"$BASE_CATALINA_OPTS\"\n");
        sh.append("fi\n");
        sh.append("export CATALINA_OPTS\n");
        Files.writeString(setenvSh, sh.toString(), StandardCharsets.UTF_8);
        setenvSh.toFile().setExecutable(true);

        // --- setenv.bat ---
        Path setenvBat = binDir.resolve("setenv.bat");
        String escapedBatOpts = joinedOpts.replace("\"", "\\\"");
        StringBuilder bat = new StringBuilder();
        bat.append("@echo off\r\n");
        bat.append("REM Auto-generated by LuCLI from lucee.json. Manual changes may be overwritten.\r\n");
        bat.append("IF NOT DEFINED CATALINA_OPTS (\r\n");
        bat.append("  set \"CATALINA_OPTS=").append(escapedBatOpts).append("\"\r\n");
        bat.append(")\r\n");
        Files.writeString(setenvBat, bat.toString(), StandardCharsets.UTF_8);

        System.out.println("Generated setenv scripts in: " + binDir);
    }

    /**
     * Get LuCLI home directory.
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
}
