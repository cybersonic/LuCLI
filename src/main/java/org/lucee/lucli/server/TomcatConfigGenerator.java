package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates Tomcat configuration files for Lucee server instances using templates.
 * Copies template files from resources and replaces placeholders with actual values.
 */
public class TomcatConfigGenerator {
    
    private static final String URLREWRITE_VERSION = "5.1.3";
    private static final String URLREWRITE_MAVEN_URL =
        "https://repo1.maven.org/maven2/org/tuckey/urlrewritefilter/" + URLREWRITE_VERSION +
        "/urlrewritefilter-" + URLREWRITE_VERSION + ".jar";
    
    /**
     * Generate complete server instance by copying Lucee Express and applying templates.
     *
     * @param overwriteProjectConfig when true, project-level config files under WEB-INF
     *                               (web.xml, urlrewrite.xml, UrlRewriteFilter JAR) will be
     *                               overwritten even if they already exist. When false, any
     *                               existing project files are preserved.
     */
    public void generateConfiguration(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                    Path projectDir, Path luceeExpressDir,
                                    boolean overwriteProjectConfig) throws IOException {
        
        // Copy entire Lucee Express distribution to server instance directory
        copyLuceeExpressDistribution(luceeExpressDir, serverInstanceDir);
        
        // Apply template configurations
        applyConfigurationTemplates(serverInstanceDir, config, projectDir, overwriteProjectConfig);
    }
    
    /**
     * Apply configuration templates by processing template files and replacing placeholders
     */
    private void applyConfigurationTemplates(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                           Path projectDir, boolean overwriteProjectConfig) throws IOException {
        
        // Create placeholder replacement map
        Map<String, String> placeholders = createPlaceholderMap(serverInstanceDir, config, projectDir);
        
        // Create the lucee-web directory in the server instance
        Path luceeWebDir = serverInstanceDir.resolve("lucee-web");
        Files.createDirectories(luceeWebDir);
        
        // Ensure the global patches directory exists
        Path patchesDir = getLucliHome().resolve("patches");
        Files.createDirectories(patchesDir);
        
        // Apply server.xml template
        applyTemplate("tomcat_template/conf/server.xml", serverInstanceDir.resolve("conf/server.xml"), placeholders);
        
        // Apply logging.properties template
        applyTemplate("tomcat_template/conf/logging.properties", serverInstanceDir.resolve("conf/logging.properties"), placeholders);
        
        // Apply web.xml template to PROJECT directory's WEB-INF (for docBase configuration reading)
        Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        Path webrootWebInf = webroot.resolve("WEB-INF");
        Files.createDirectories(webrootWebInf);
        Path projectWebXml = webrootWebInf.resolve("web.xml");
        
        if (overwriteProjectConfig || !Files.exists(projectWebXml)) {
            applyWebXmlTemplate("tomcat_template/webapps/ROOT/WEB-INF/web.xml", projectWebXml, placeholders, config);
        } else {
            System.out.println("Preserving existing project web.xml at " + projectWebXml.toAbsolutePath());
        }
        
        // Apply urlrewrite.xml template to PROJECT directory only if URL rewrite is enabled
        if (config.urlRewrite != null && config.urlRewrite.enabled) {
            Path projectUrlRewriteXml = webrootWebInf.resolve("urlrewrite.xml");
            if (overwriteProjectConfig || !Files.exists(projectUrlRewriteXml)) {
                applyTemplate("tomcat_template/webapps/ROOT/WEB-INF/urlrewrite.xml", projectUrlRewriteXml, placeholders);
            } else {
                System.out.println("Preserving existing project urlrewrite.xml at " + projectUrlRewriteXml.toAbsolutePath());
            }
        }
        
        // Deploy UrlRewriteFilter JAR to PROJECT directory's WEB-INF/lib
        // CRITICAL: When docBase points to the project directory, Tomcat's classloader
        // loads JARs from docBase/WEB-INF/lib, NOT from CATALINA_BASE/webapps/ROOT/WEB-INF/lib
        if (config.urlRewrite != null && config.urlRewrite.enabled) {
            try {
                Path projectWebInfLib = webrootWebInf.resolve("lib");
                Files.createDirectories(projectWebInfLib);
                
                Path urlRewriteJar = ensureUrlRewriteFilter();
                Path targetJar = projectWebInfLib.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
                if (overwriteProjectConfig || !Files.exists(targetJar)) {
                    Files.copy(urlRewriteJar, targetJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    System.out.println("Preserving existing UrlRewriteFilter JAR at " + targetJar.toAbsolutePath());
                }
                
                System.out.println("Deployed UrlRewriteFilter to project docBase:");
                System.out.println("  JAR: " + targetJar.toAbsolutePath());
                System.out.println("  web.xml: " + projectWebXml.toAbsolutePath());
                System.out.println("  urlrewrite.xml: " + webrootWebInf.resolve("urlrewrite.xml").toAbsolutePath());
            } catch (Exception e) {
                throw new IOException("Failed to deploy UrlRewriteFilter JAR: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Create a map of placeholder values for template processing
     */
    private Map<String, String> createPlaceholderMap(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                                   Path projectDir) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Calculate paths
        Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        Path luceeServerPath = serverInstanceDir.resolve("lucee-server");
        Path luceeWebPath = serverInstanceDir.resolve("lucee-web");
        Path luceePatchesPath = getLucliHome().resolve("patches");
        int shutdownPort = LuceeServerConfig.getShutdownPort(config.port);
        
        // Add all placeholder values
        placeholders.put("${httpPort}", String.valueOf(config.port));
        placeholders.put("${shutdownPort}", String.valueOf(shutdownPort));
        placeholders.put("${jmxPort}", String.valueOf(config.monitoring.jmx.port));
        placeholders.put("${webroot}", webroot.toAbsolutePath().toString());
        placeholders.put("${luceeServerPath}", luceeServerPath.toAbsolutePath().toString());
        placeholders.put("${luceeWebPath}", luceeWebPath.toAbsolutePath().toString());
        placeholders.put("${luceePatches}", luceePatchesPath.toAbsolutePath().toString());
        placeholders.put("${jvmRoute}", config.name);
        placeholders.put("${logLevel}", "INFO");
        
        // Add URL rewrite configuration placeholders
        String routerFile = (config.urlRewrite != null && config.urlRewrite.routerFile != null)
            ? config.urlRewrite.routerFile
            : "index.cfm";
        placeholders.put("${routerFile}", routerFile);
        
        return placeholders;
    }
    
    /**
     * Apply a template file by loading it from resources and replacing placeholders
     */
    private void applyTemplate(String templateResourcePath, Path outputPath, Map<String, String> placeholders) throws IOException {
        // Load template from resources
        String templateContent;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templateResourcePath);
            }
            templateContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        // Replace placeholders
        String processedContent = templateContent;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processedContent = processedContent.replace(entry.getKey(), entry.getValue());
        }
        
        // Ensure output directory exists
        Files.createDirectories(outputPath.getParent());
        
        // Write processed content to output file
        Files.writeString(outputPath, processedContent, StandardCharsets.UTF_8);
    }
    
    /**
     * Apply web.xml template with conditional admin servlet mappings
     */
    private void applyWebXmlTemplate(String templateResourcePath, Path outputPath, Map<String, String> placeholders,
                                    LuceeServerConfig.ServerConfig config) throws IOException {
        // Load template from resources
        String templateContent;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templateResourcePath);
            }
            templateContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        // Replace placeholders
        String processedContent = templateContent;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processedContent = processedContent.replace(entry.getKey(), entry.getValue());
        }
        
        // Apply simple conditional blocks (UrlRewrite, admin, etc.) based on lucee.json
        processedContent = applyConditionalBlocks(processedContent, config);
        
        // Ensure output directory exists
        Files.createDirectories(outputPath.getParent());
        
        // Write processed content to output file
        Files.writeString(outputPath, processedContent, StandardCharsets.UTF_8);
    }
    
    /**
     * Apply conditional comment blocks in the web.xml template based on config.
     * Currently supports:
     *   <!-- IF_URLREWRITE_ENABLED --> ... <!-- END_IF_URLREWRITE_ENABLED -->
     *   <!-- IF_ADMIN_ENABLED -->        ... <!-- END_IF_ADMIN_ENABLED -->
     */
    private String applyConditionalBlocks(String content, LuceeServerConfig.ServerConfig config) {
        boolean urlRewriteEnabled = config.urlRewrite != null && config.urlRewrite.enabled;
        content = processBooleanBlock(content, "IF_URLREWRITE_ENABLED", urlRewriteEnabled);
        
        // Admin: default to enabled if config.admin is null (backwards compatibility)
        boolean adminEnabled = (config.admin == null) || config.admin.enabled;
        content = processBooleanBlock(content, "IF_ADMIN_ENABLED", adminEnabled);
        
        return content;
    }
    
    /**
     * Process a boolean block of the form:
     *   <!-- TAG --> ... <!-- END_TAG -->
     * If keep == true, the inner content is kept; otherwise, the whole block is removed.
     */
    private String processBooleanBlock(String content, String tag, boolean keep) {
        String start = "<!-- " + tag + " -->";
        String end = "<!-- END_" + tag + " -->";
        int idxStart = content.indexOf(start);
        while (idxStart != -1) {
            int idxEnd = content.indexOf(end, idxStart);
            if (idxEnd == -1) {
                break; // malformed block, stop processing
            }
            int blockEnd = idxEnd + end.length();
            String before = content.substring(0, idxStart);
            String inside = content.substring(idxStart + start.length(), idxEnd);
            String after = content.substring(blockEnd);
            if (keep) {
                content = before + inside + after;
                idxStart = content.indexOf(start, before.length() + inside.length());
            } else {
                content = before + after;
                idxStart = content.indexOf(start, before.length());
            }
        }
        return content;
    }
    
    /**
     * Copy the entire Lucee Express distribution to the server instance directory
     */
    private void copyLuceeExpressDistribution(Path luceeExpressDir, Path serverInstanceDir) throws IOException {
        // Recursively copy entire Lucee Express directory to server instance
        copyDirectoryRecursively(luceeExpressDir, serverInstanceDir);
    }
    
    /**
     * Recursively copy a directory and all its contents
     */
    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath + " to " + target, e);
            }
        });
    }
    
    /**
     * Download and deploy the UrlRewriteFilter JAR (now handled in applyConfigurationTemplates)
     */
    private void deployUrlRewriteFilter(Path serverInstanceDir) throws Exception {
        // This method is now deprecated - JAR deployment moved to applyConfigurationTemplates
        // Kept for backward compatibility but does nothing
    }
    
    /**
     * Ensure the UrlRewriteFilter JAR is available, downloading if necessary
     */
    private Path ensureUrlRewriteFilter() throws Exception {
        // Get LuCLI home directory
        Path lucliHome = getLucliHome();
        Path dependenciesDir = lucliHome.resolve("dependencies");
        Files.createDirectories(dependenciesDir);
        
        // Check if JAR already exists
        Path jarFile = dependenciesDir.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
        if (Files.exists(jarFile)) {
            return jarFile;
        }
        
        // Download the JAR
        System.out.println("Downloading UrlRewriteFilter " + URLREWRITE_VERSION + "...");
        downloadFile(URLREWRITE_MAVEN_URL, jarFile);
        System.out.println("UrlRewriteFilter downloaded successfully.");
        
        return jarFile;
    }
    
    /**
     * Get the LuCLI home directory
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
    
    /**
     * Download a file from URL
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
    
}
