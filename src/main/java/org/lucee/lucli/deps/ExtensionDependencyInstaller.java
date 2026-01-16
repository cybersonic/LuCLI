package org.lucee.lucli.deps;

import org.lucee.lucli.config.DependencyConfig;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Installer for Lucee extension (.lex) dependencies.
 * 
 * During install phase: Records extension metadata in lock file
 * During server start: Deploys .lex files to lucee-server/deploy folder
 * 
 * Handles three scenarios:
 * 1. Extension with only ID/slug - recorded for LUCEE_EXTENSIONS env var
 * 2. Extension with URL - downloaded to a cache folder at install time
 * 3. Extension with path - path recorded (validated) and copied at server start
 * 
 * More information on ways to install extensions with lucee can be found here:
 * https://docs.lucee.org/recipes/extension-installation.html
 */
public class ExtensionDependencyInstaller implements DependencyInstaller {
    
    private final Path projectDir;
    private final Path cacheDir;

    public ExtensionDependencyInstaller(Path projectDir) {
        this.projectDir = projectDir != null ? projectDir : Paths.get(".");

        String lucliHome = System.getProperty("lucli.home");
        if (lucliHome == null) {
            lucliHome = System.getenv("LUCLI_HOME");
        }
        if (lucliHome == null) {
            lucliHome = System.getProperty("user.home") + "/.lucli";
        }
        this.cacheDir = Paths.get(lucliHome, "extensions-cache");
    }
    
    @Override
    public boolean supports(DependencyConfig dep) {
        return "extension".equals(dep.getType());
    }
    
    @Override
    public LockedDependency install(DependencyConfig dep) throws Exception {
        LockedDependency locked = new LockedDependency();
        locked.setType("extension");
        locked.setVersion(dep.getVersion() != null ? dep.getVersion() : "unknown");
        
        // 1) Path-based extension: validate and record absolute path. Lucee will
        //    install it from the server's deploy directory on startup.
        if (dep.getPath() != null && !dep.getPath().trim().isEmpty()) {
            String configured = dep.getPath().trim();
            Path configuredPath = Path.of(configured);
            Path resolvedPath = configuredPath.isAbsolute()
                ? configuredPath
                : projectDir.resolve(configuredPath).normalize();

            if (!Files.exists(resolvedPath)) {
                throw new IOException("Extension file not found: " + resolvedPath);
            }
            if (!Files.isRegularFile(resolvedPath)) {
                throw new IOException("Extension path is not a file: " + resolvedPath);
            }

            String absPath = resolvedPath.toAbsolutePath().toString();
            locked.setSource("path:" + absPath);
            locked.setInstallPath(absPath);
            System.out.println("  " + dep.getName() + " - recorded (extension from path: " + absPath + ")");
            return locked;
        }

        // 2) URL-based extension: download to cache folder at install time and
        //    record cached path so server start only needs to copy to deploy/.
        if (dep.getUrl() != null && !dep.getUrl().trim().isEmpty()) {
            String urlString = dep.getUrl().trim();
            String absPath = downloadExtensionToCache(urlString);
            locked.setSource("path:" + absPath);
            locked.setInstallPath(absPath);
            System.out.println("  " + dep.getName() + " - downloaded extension to " + absPath);
            return locked;
        }

        // 3) Provider-based extension via LUCEE_EXTENSIONS. We resolve the
        //    extension ID from either the explicit "id" field or the dependency
        //    name (slug) using DependencyConfig.getId(). If it cannot be
        //    resolved, we warn but do not fail the install.
        String resolvedId = dep.getId();
        String keyForMsg = dep.getRawId() != null && !dep.getRawId().trim().isEmpty()
            ? dep.getRawId()
            : dep.getName();

        locked.setSource("extension-provider");

        if (resolvedId == null || resolvedId.trim().isEmpty()) {
            System.err.println(
                "Warning: Extension '" + keyForMsg + "' was not found in the Lucee extension registry. " +
                "It will NOT be included in LUCEE_EXTENSIONS."
            );
            // Leave ID null so it is ignored when building LUCEE_EXTENSIONS
            return locked;
        }

        locked.setId(resolvedId.trim());
        System.out.println("  " + dep.getName() + " - recorded (extension ID: " + locked.getId() + ")");
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
                // Backwards compatibility: older lock files may still use the
                // raw URL as the source. In that case, download directly to the
                // deploy directory.
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

    /**
     * Download an extension .lex file into the shared cache directory and
     * return the absolute path to the cached file. If the file already
     * exists, it is reused without re-downloading.
     */
    private String downloadExtensionToCache(String urlString) throws Exception {
        Files.createDirectories(cacheDir);

        String filename = extractFilenameFromUrl(urlString);
        Path targetFile = cacheDir.resolve(filename);

        if (Files.exists(targetFile)) {
            return targetFile.toAbsolutePath().toString();
        }

        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download extension from " + urlString +
                        ". HTTP response code: " + responseCode);
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return targetFile.toAbsolutePath().toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
