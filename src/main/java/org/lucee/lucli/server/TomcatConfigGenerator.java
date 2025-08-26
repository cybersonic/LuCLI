package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates Tomcat configuration files for Lucee server instances using templates.
 * Copies template files from resources and replaces placeholders with actual values.
 */
public class TomcatConfigGenerator {
    
    /**
     * Generate complete server instance by copying Lucee Express and applying templates
     */
    public void generateConfiguration(Path serverInstanceDir, LuceeServerConfig.ServerConfig config, 
                                    Path projectDir, Path luceeExpressDir) throws IOException {
        
        // Copy entire Lucee Express distribution to server instance directory
        copyLuceeExpressDistribution(luceeExpressDir, serverInstanceDir);
        
        // Apply template configurations
        applyConfigurationTemplates(serverInstanceDir, config, projectDir);
    }
    
    /**
     * Apply configuration templates by processing template files and replacing placeholders
     */
    private void applyConfigurationTemplates(Path serverInstanceDir, LuceeServerConfig.ServerConfig config, 
                                           Path projectDir) throws IOException {
        
        // Create placeholder replacement map
        Map<String, String> placeholders = createPlaceholderMap(serverInstanceDir, config, projectDir);
        
        // Create the lucee-web directory in the server instance
        Path luceeWebDir = serverInstanceDir.resolve("lucee-web");
        Files.createDirectories(luceeWebDir);
        
        // Apply server.xml template
        applyTemplate("tomcat_template/conf/server.xml", serverInstanceDir.resolve("conf/server.xml"), placeholders);
        
        // Apply logging.properties template
        applyTemplate("tomcat_template/conf/logging.properties", serverInstanceDir.resolve("conf/logging.properties"), placeholders);
        
        // Apply web.xml template to server instance webapps/ROOT/WEB-INF directory (not project directory)
        Path webrootWebInf = serverInstanceDir.resolve("webapps/ROOT/WEB-INF");
        Files.createDirectories(webrootWebInf);
        applyTemplate("tomcat_template/webapps/ROOT/WEB-INF/web.xml", webrootWebInf.resolve("web.xml"), placeholders);
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
        int shutdownPort = LuceeServerConfig.getShutdownPort(config.port);
        
        // Add all placeholder values
        placeholders.put("${httpPort}", String.valueOf(config.port));
        placeholders.put("${shutdownPort}", String.valueOf(shutdownPort));
        placeholders.put("${jmxPort}", String.valueOf(config.monitoring.jmx.port));
        placeholders.put("${webroot}", webroot.toAbsolutePath().toString());
        placeholders.put("${luceeServerPath}", luceeServerPath.toAbsolutePath().toString());
        placeholders.put("${luceeWebPath}", luceeWebPath.toAbsolutePath().toString());
        placeholders.put("${jvmRoute}", config.name);
        placeholders.put("${logLevel}", "INFO");
        
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
    
}
