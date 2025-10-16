package org.lucee.lucli.cflint;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads and dynamically loads CFLint library on demand
 */
public class CFLintDownloader {
    private static final String CFLINT_VERSION = "1.5.6";
    private static final String CFLINT_JAR_NAME = "cflint-" + CFLINT_VERSION + "-all.jar";
    private static final String CFLINT_DOWNLOAD_URL = "https://github.com/cfmleditor/CFLint/releases/download/" + CFLINT_VERSION + "/" + CFLINT_JAR_NAME;
    
    private static final AtomicBoolean downloadInProgress = new AtomicBoolean(false);
    private static URLClassLoader cfLintClassLoader;
    private static Object cfLintInstance;
    private static Class<?> cfLintClass;
    
    /**
     * Get the CFLint library directory path
     */
    public static Path getCFLintLibsDir() {
        String lucliHome = System.getProperty("lucli.home");
        if (lucliHome == null) {
            lucliHome = System.getProperty("user.home") + "/.lucli";
        }
        return Paths.get(lucliHome, "libs");
    }
    
    /**
     * Get the CFLint JAR file path
     */
    public static Path getCFLintJarPath() {
        return getCFLintLibsDir().resolve(CFLINT_JAR_NAME);
    }
    
    /**
     * Check if CFLint is available (either downloaded or can be downloaded)
     */
    public static boolean isCFLintAvailable() {
        return Files.exists(getCFLintJarPath()) || canDownloadCFLint();
    }
    
    /**
     * Check if we can download CFLint (internet connection available)
     */
    private static boolean canDownloadCFLint() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(CFLINT_DOWNLOAD_URL).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Ensure CFLint is available, downloading if necessary
     */
    public static boolean ensureCFLintAvailable() throws Exception {
        Path jarPath = getCFLintJarPath();
        
        if (Files.exists(jarPath)) {
            return true;
        }
        
        // Prevent multiple simultaneous downloads
        if (downloadInProgress.compareAndSet(false, true)) {
            try {
                return downloadCFLint();
            } finally {
                downloadInProgress.set(false);
            }
        } else {
            // Wait for other download to complete
            while (downloadInProgress.get()) {
                Thread.sleep(100);
            }
            return Files.exists(jarPath);
        }
    }
    
    /**
     * Download CFLint JAR from GitHub releases
     */
    private static boolean downloadCFLint() throws Exception {
        Path libsDir = getCFLintLibsDir();
        Path jarPath = getCFLintJarPath();
        
        // Create libs directory if it doesn't exist
        Files.createDirectories(libsDir);
        
        System.out.println("📥 Downloading CFLint " + CFLINT_VERSION + "...");
        System.out.println("🔗 From: " + CFLINT_DOWNLOAD_URL);
        System.out.println("📁 To: " + jarPath);
        
        // Download with progress indication
        HttpURLConnection connection = (HttpURLConnection) new URL(CFLINT_DOWNLOAD_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download CFLint. HTTP response code: " + responseCode);
        }
        
        long fileSize = connection.getContentLengthLong();
        
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(jarPath.toFile())) {
            
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;
            long lastProgressUpdate = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // Show progress every 500KB or at the end
                if (fileSize > 0 && (totalBytesRead - lastProgressUpdate > 500000 || totalBytesRead == fileSize)) {
                    int percentage = (int) ((totalBytesRead * 100) / fileSize);
                    System.out.printf("📊 Progress: %d%% (%s / %s)\n", 
                        percentage, 
                        formatBytes(totalBytesRead), 
                        formatBytes(fileSize));
                    lastProgressUpdate = totalBytesRead;
                }
            }
        }
        
        connection.disconnect();
        
        if (Files.exists(jarPath)) {
            System.out.println("✅ CFLint downloaded successfully!");
            return true;
        } else {
            throw new IOException("Download completed but JAR file not found");
        }
    }
    
    /**
     * Load CFLint classes dynamically
     */
    public static synchronized Object getCFLintInstance() throws Exception {
        if (cfLintInstance != null) {
            return cfLintInstance;
        }
        
        if (!ensureCFLintAvailable()) {
            throw new RuntimeException("CFLint is not available and cannot be downloaded");
        }
        
        if (cfLintClassLoader == null) {
            Path jarPath = getCFLintJarPath();
            URL jarUrl = jarPath.toUri().toURL();
            cfLintClassLoader = new URLClassLoader(new URL[]{jarUrl}, CFLintDownloader.class.getClassLoader());
            
            cfLintClass = cfLintClassLoader.loadClass("com.cflint.CFLint");
            
            // Try different constructors based on actual CFLint API
            try {
                // First try CFLintAPI (recommended approach)
                try {
                    Class<?> cfLintAPIClass = cfLintClassLoader.loadClass("com.cflint.api.CFLintAPI");
                    cfLintInstance = cfLintAPIClass.getDeclaredConstructor().newInstance();
                    cfLintClass = cfLintAPIClass; // Use CFLintAPI instead of CFLint
                } catch (Exception apiException) {
                    // Fall back to CFLint class with CFLintConfiguration
                    Class<?> cfLintConfigClass = cfLintClassLoader.loadClass("com.cflint.config.CFLintConfiguration");
                    Class<?> configBuilderClass = cfLintClassLoader.loadClass("com.cflint.config.ConfigBuilder");
                    
                    // Create configuration using ConfigBuilder
                    Object configBuilder = configBuilderClass.getDeclaredConstructor().newInstance();
                    java.lang.reflect.Method buildMethod = configBuilderClass.getMethod("build");
                    Object config = buildMethod.invoke(configBuilder);
                    
                    // Use CFLint constructor that takes CFLintConfiguration
                    cfLintInstance = cfLintClass.getDeclaredConstructor(cfLintConfigClass)
                                               .newInstance(config);
                }
            } catch (Exception e1) {
                throw new RuntimeException("Failed to create CFLint instance. CFLint requires CFLintConfiguration parameter.", e1);
            }
        }
        
        return cfLintInstance;
    }
    
    /**
     * Get the CFLint class for reflective access
     */
    public static synchronized Class<?> getCFLintClass() throws Exception {
        if (cfLintClass != null) {
            return cfLintClass;
        }
        
        getCFLintInstance(); // This will initialize the class
        return cfLintClass;
    }
    
    /**
     * Check if CFLint is currently loaded
     */
    public static boolean isCFLintLoaded() {
        return cfLintInstance != null;
    }
    
    /**
     * Get information about CFLint availability
     */
    public static String getCFLintStatus() {
        Path jarPath = getCFLintJarPath();
        
        if (Files.exists(jarPath)) {
            try {
                long size = Files.size(jarPath);
                return String.format("✅ CFLint %s available (%s)", CFLINT_VERSION, formatBytes(size));
            } catch (IOException e) {
                return "⚠️ CFLint JAR exists but cannot read file info";
            }
        } else if (canDownloadCFLint()) {
            return "📥 CFLint available for download";
        } else {
            return "❌ CFLint not available (no internet connection)";
        }
    }
    
    /**
     * Format bytes for display
     */
    private static String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        double size = bytes;
        
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        
        return String.format("%.1f %s", size, units[unit]);
    }
    
    /**
     * Clean up resources
     */
    public static void cleanup() {
        if (cfLintClassLoader != null) {
            try {
                cfLintClassLoader.close();
            } catch (IOException e) {
                // Ignore cleanup errors
            }
            cfLintClassLoader = null;
        }
        cfLintInstance = null;
        cfLintClass = null;
    }
}
