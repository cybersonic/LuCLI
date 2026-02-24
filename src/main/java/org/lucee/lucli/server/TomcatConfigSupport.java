package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for Tomcat-related configuration that are independent of
 * whether we are targeting the embedded Lucee Express Tomcat layout or an
 * external Tomcat installation.
 *
 * All static utility methods live here so that runtime providers and config
 * generators have a single source of truth.
 */
public final class TomcatConfigSupport {

    private static final String URLREWRITE_VERSION = "5.1.3";
    private static final String URLREWRITE_MAVEN_URL =
            "https://repo1.maven.org/maven2/org/tuckey/urlrewritefilter/" + URLREWRITE_VERSION +
                    "/urlrewritefilter-" + URLREWRITE_VERSION + ".jar";

    private TomcatConfigSupport() {
    }

    // ── LuCLI home ──────────────────────────────────────────────────────

    /**
     * Resolve the LuCLI home directory. Checks (in order):
     * 1. System property {@code lucli.home}
     * 2. Environment variable {@code LUCLI_HOME}
     * 3. {@code ~/.lucli}
     */
    public static Path getLucliHome() {
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

    // ── Port details display ────────────────────────────────────────────

    /**
     * Print a summary of the ports a server will bind to.
     *
     * @param config        server configuration
     * @param foreground    whether the server is running in foreground mode
     * @param runtimeLabel  optional extra label (e.g. "external Tomcat"), may be null
     */
    public static void displayPortDetails(LuceeServerConfig.ServerConfig config,
                                          boolean foreground,
                                          String runtimeLabel) {
        StringBuilder portInfo = new StringBuilder();
        String label = (runtimeLabel != null && !runtimeLabel.isEmpty())
                ? " (" + runtimeLabel + ")"
                : "";
        if (foreground) {
            portInfo.append("Running server '").append(config.name).append("'" + label + " in foreground mode:");
        } else {
            portInfo.append("Starting server '").append(config.name).append("'" + label + " on:");
        }
        portInfo.append("\n  HTTP port:     ").append(config.port);
        portInfo.append("\n  Shutdown port: ").append(LuceeServerConfig.getEffectiveShutdownPort(config));
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            portInfo.append("\n  JMX port:      ").append(config.monitoring.jmx.port);
        }
        if (LuceeServerConfig.isHttpsEnabled(config)) {
            portInfo.append("\n  HTTPS port:    ").append(LuceeServerConfig.getEffectiveHttpsPort(config));
            portInfo.append("\n  HTTPS redirect:")
                    .append(LuceeServerConfig.isHttpsRedirectEnabled(config) ? " enabled" : " disabled");
        }
        if (foreground) {
            portInfo.append("\n\nPress Ctrl+C to stop the server\n");
        }
        System.out.println(portInfo.toString());
    }

    // ── Directory utilities ─────────────────────────────────────────────

    /**
     * Recursively delete a directory and all its contents.
     */
    public static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to delete " + path + ": " + e.getMessage());
                    }
                });
    }

    // ── Placeholder map ─────────────────────────────────────────────────

    /**
     * Build the standard placeholder map used for template processing.
     */
    public static Map<String, String> createPlaceholderMap(Path serverInstanceDir,
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

    // ── Template processing ─────────────────────────────────────────────

    /**
     * Load a template from the classpath, replace placeholders, and write
     * the result to the given output path.
     */
    public static void applyTemplate(String templateResourcePath, Path outputPath,
                                     Map<String, String> placeholders) throws IOException {
        String templateContent;
        try (InputStream is = TomcatConfigSupport.class.getClassLoader()
                .getResourceAsStream(templateResourcePath)) {
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

    // ── setenv scripts ──────────────────────────────────────────────────

    /**
     * Write Tomcat setenv scripts (setenv.sh / setenv.bat) into the given
     * CATALINA_BASE's bin directory.
     */
    public static void writeSetenvScripts(Path catalinaBase,
                                          LuceeServerConfig.ServerConfig config,
                                          Path projectDir) throws IOException {
        Path binDir = catalinaBase.resolve("bin");
        Files.createDirectories(binDir);

        List<String> opts;
        try {
            LuceeServerManager manager = new LuceeServerManager();
            opts = manager.buildCatalinaOpts(config, null, projectDir);
        } catch (Exception e) {
            throw new IOException("Failed to build JVM options for setenv scripts: " + e.getMessage(), e);
        }

        if (opts == null || opts.isEmpty()) {
            return;
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

    // ── UrlRewriteFilter ────────────────────────────────────────────────

    /**
     * Return the UrlRewriteFilter version string managed by LuCLI.
     */
    public static String getUrlRewriteVersion() {
        return URLREWRITE_VERSION;
    }

    /**
     * Ensure the UrlRewriteFilter JAR is cached under ~/.lucli/dependencies
     * and return the path to it.
     */
    public static Path ensureUrlRewriteFilter() throws IOException {
        Path dependenciesDir = getLucliHome().resolve("dependencies");
        Files.createDirectories(dependenciesDir);

        Path jarFile = dependenciesDir.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
        if (Files.exists(jarFile)) {
            return jarFile;
        }

        System.out.println("Downloading UrlRewriteFilter " + URLREWRITE_VERSION + "...");
        downloadFileSimple(URLREWRITE_MAVEN_URL, jarFile);
        System.out.println("UrlRewriteFilter downloaded successfully.");

        return jarFile;
    }

    // ── Simple file download (no progress bar) ──────────────────────────

    /**
     * Download a file from the given URL. This is a simple byte-copy without
     * a progress bar and is suitable for small artefacts (JARs, config files).
     */
    public static void downloadFileSimple(String urlString, Path destinationFile) throws IOException {
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
}
