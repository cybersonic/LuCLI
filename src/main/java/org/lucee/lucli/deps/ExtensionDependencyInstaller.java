package org.lucee.lucli.deps;

import org.lucee.lucli.config.DependencyConfig;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Installer for Lucee extension (.lex) dependencies.
 * 
 * During install phase: Records extension metadata in lock file
 * During server start: Deploys .lex files to lucee-server/deploy folder
 * 
 * Handles three scenarios:
 * 1. Extension with only ID - recorded for LUCEE_EXTENSIONS env var
 * 2. Extension with URL - URL stored in lock file, downloaded at server start
 * 3. Extension with path - path stored in lock file, copied at server start
 */
public class ExtensionDependencyInstaller implements DependencyInstaller {
    
    @Override
    public boolean supports(DependencyConfig dep) {
        return "extension".equals(dep.getType());
    }
    
    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        LockedDependency locked = new LockedDependency();
        locked.setType("extension");
        locked.setVersion(dep.getVersion() != null ? dep.getVersion() : "unknown");
        
        // Store extension ID if provided
        if (dep.getId() != null && !dep.getId().trim().isEmpty()) {
            locked.setId(dep.getId().trim());
        }
        
        // Record URL or path in lock file - actual deployment happens at server start
        if (dep.getUrl() != null && !dep.getUrl().trim().isEmpty()) {
            locked.setSource(dep.getUrl());
            System.out.println("  " + dep.getName() + " - recorded (extension from URL)");
        } else if (dep.getPath() != null && !dep.getPath().trim().isEmpty()) {
            locked.setSource("path:" + dep.getPath());
            locked.setInstallPath(dep.getPath());
            System.out.println("  " + dep.getName() + " - recorded (extension from path)");
        } else if (locked.getId() != null) {
            // Only ID provided - will be installed via LUCEE_EXTENSIONS env var
            locked.setSource("extension-provider");
            System.out.println("  " + dep.getName() + " - recorded (extension ID: " + locked.getId() + ")");
        } else {
            throw new Exception("Extension must have either 'id', 'url', or 'path' specified");
        }
        
        return locked;
    }
    
    /**
     * Deploy extensions to server's lucee-server/deploy folder.
     * Called during server start.
     * 
     * @param extensions List of extension locked dependencies
     * @param serverInstanceDir Path to server instance directory
     */
    public static void deployExtensions(java.util.Collection<LockedDependency> extensions, Path serverInstanceDir) throws Exception {
        if (extensions == null || extensions.isEmpty()) {
            return;
        }
        
        Path deployDir = serverInstanceDir.resolve("lucee-server").resolve("deploy");
        Files.createDirectories(deployDir);
        
        for (LockedDependency ext : extensions) {
            if (!"extension".equals(ext.getType())) {
                continue;
            }
            
            String source = ext.getSource();
            if (source == null || "extension-provider".equals(source)) {
                // ID-only extension, handled via LUCEE_EXTENSIONS env var
                continue;
            }
            
            if (source.startsWith("path:")) {
                deployExtensionFromPath(source.substring(5), deployDir);
            } else {
                // Assume URL
                deployExtensionFromUrl(source, deployDir);
            }
        }
    }
    
    /**
     * Download .lex file from URL and deploy it to deploy folder
     */
    private static void deployExtensionFromUrl(String urlString, Path deployDir) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = null;
        
        try {
            // Extract filename from URL
            String filename = extractFilenameFromUrl(urlString);
            Path targetFile = deployDir.resolve(filename);
            
            // Skip if already exists
            if (Files.exists(targetFile)) {
                System.out.println("  Extension already deployed: " + filename);
                return;
            }
            
            // Download file
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download extension from " + urlString + 
                                    ". HTTP response code: " + responseCode);
            }
            
            // Copy to deploy directory
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            System.out.println("  Downloaded extension: " + filename);
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Copy .lex file from local path to deploy folder
     */
    private static void deployExtensionFromPath(String pathString, Path deployDir) throws Exception {
        Path sourcePath = Path.of(pathString);
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("Extension file not found: " + pathString);
        }
        
        if (!Files.isRegularFile(sourcePath)) {
            throw new IOException("Extension path is not a file: " + pathString);
        }
        
        // Determine filename
        String filename = sourcePath.getFileName().toString();
        Path targetFile = deployDir.resolve(filename);
        
        // Skip if already exists and is same file
        if (Files.exists(targetFile)) {
            try {
                if (Files.isSameFile(sourcePath, targetFile)) {
                    System.out.println("  Extension already deployed: " + filename);
                    return;
                }
            } catch (IOException e) {
                // Not same file, continue with copy
            }
        }
        
        // Copy file
        Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("  Copied extension: " + filename);
    }
    
    /**
     * Extract filename from URL, falling back to timestamp-based name
     */
    private static String extractFilenameFromUrl(String urlString) {
        String path = urlString;
        
        // Remove query parameters
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }
        
        // Get last path segment
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < path.length() - 1) {
            String filename = path.substring(lastSlash + 1);
            if (filename.endsWith(".lex")) {
                return filename;
            }
        }
        
        // Fallback to hash-based name
        return "extension-" + Math.abs(urlString.hashCode()) + ".lex";
    }
}
