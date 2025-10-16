package org.lucee.lucli.commands;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.lucee.lucli.server.LuceeServerConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simplified helper class for server configuration operations
 * Handles basic configuration get/set operations and provides version caching placeholder
 */
public class ServerConfigHelper {
    
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.lucli";
    private static final String VERSION_CACHE_FILE = "lucee-versions.json";
    
    /**
     * Get a configuration value by key using dot notation
     */
    public String getConfigValue(LuceeServerConfig.ServerConfig config, String key) {
        String[] keyParts = key.split("\\.");
        return getNestedValue(config, keyParts);
    }
    
    /**
     * Set a configuration value by key using dot notation
     */
    public void setConfigValue(LuceeServerConfig.ServerConfig config, String key, String value) {
        String[] keyParts = key.split("\\.");
        setNestedValue(config, keyParts, value);
    }
    
    /**
     * Get list of available configuration keys
     */
    public List<String> getAvailableKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("version");
        keys.add("port");
        keys.add("name");
        keys.add("webroot");
        keys.add("jvm.maxMemory");
        keys.add("jvm.minMemory");
        keys.add("jvm.additionalArgs");
        keys.add("monitoring.enabled");
        keys.add("monitoring.jmx.port");
        return keys;
    }
    
    /**
     * Get available Lucee versions (fallback to common versions for now)
     */
    public List<String> getAvailableVersions() {
        return getAvailableVersions(false);
    }
    
    /**
     * Get available Lucee versions with option to bypass cache
     */
    public List<String> getAvailableVersions(boolean bypassCache) {
        try {
            Path cacheFile = getCacheFilePath();
            
            // Check if cache exists and is not bypassed
            if (!bypassCache && Files.exists(cacheFile)) {
                // Check if cache is recent (less than 24 hours old)
                long cacheAge = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
                if (cacheAge < 24 * 60 * 60 * 1000) { // 24 hours in milliseconds
                    return loadVersionsFromCache(cacheFile);
                }
            }
            
            // Fetch versions from remote API
            List<String> versions = fetchVersionsFromAPI();
            
            // Cache the results
            saveVersionsToCache(versions, cacheFile);
            
            return versions;
            
        } catch (Exception e) {
            System.err.println("Warning: Failed to fetch Lucee versions: " + e.getMessage());
            
            // Try to load from cache as fallback
            try {
                Path cacheFile = getCacheFilePath();
                if (Files.exists(cacheFile)) {
                    return loadVersionsFromCache(cacheFile);
                }
            } catch (Exception ex) {
                // Ignore cache load errors
            }
            
            // Final fallback to hardcoded versions
            return Arrays.asList(
                "7.0.0.346", "7.0.0.145", "7.0.0.090",
                "6.2.2.91", "6.2.1.75", "6.2.0.66", "6.1.8.29", 
                "6.1.7.25", "6.1.6.16", "6.0.4.10", "5.4.5.17"
            );
        }
    }
    
    /**
     * Clear the version cache
     */
    public void clearVersionCache() {
        try {
            Path cacheFile = getCacheFilePath();
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to clear version cache: " + e.getMessage());
        }
    }
    
    /**
     * Get nested value from configuration object using key path
     */
    private String getNestedValue(LuceeServerConfig.ServerConfig config, String[] keyPath) {
        if (config == null || keyPath.length == 0) {
            return null;
        }
        
        if (keyPath.length == 1) {
            String key = keyPath[0];
            switch (key) {
                case "version":
                    return config.version;
                case "port":
                    return String.valueOf(config.port);
                case "name":
                    return config.name;
                case "webroot":
                    return config.webroot;
            }
        } else if (keyPath.length == 2) {
            String category = keyPath[0];
            String key = keyPath[1];
            
            switch (category) {
                case "jvm":
                    return getJVMConfigValue(config.jvm, key);
                case "monitoring":
                    return getMonitoringConfigValue(config.monitoring, key);
            }
        } else if (keyPath.length == 3) {
            String category = keyPath[0];
            String subcategory = keyPath[1];
            String key = keyPath[2];
            
            if ("monitoring".equals(category) && "jmx".equals(subcategory)) {
                return getJMXConfigValue(config.monitoring != null ? config.monitoring.jmx : null, key);
            }
        }
        
        return null;
    }
    
    /**
     * Set nested value in configuration object using key path
     */
    private void setNestedValue(LuceeServerConfig.ServerConfig config, String[] keyPath, String value) {
        if (keyPath.length == 1) {
            String key = keyPath[0];
            switch (key) {
                case "version":
                    config.version = value;
                    break;
                case "port":
                    try {
                        config.port = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + value);
                    }
                    break;
                case "name":
                    config.name = value;
                    break;
            }
        } else if (keyPath.length == 2) {
            String category = keyPath[0];
            String key = keyPath[1];
            
            switch (category) {
                case "jvm":
                    if (config.jvm == null) config.jvm = new LuceeServerConfig.JvmConfig();
                    setJVMConfigValue(config.jvm, key, value);
                    break;
                case "monitoring":
                    if (config.monitoring == null) config.monitoring = new LuceeServerConfig.MonitoringConfig();
                    setMonitoringConfigValue(config.monitoring, key, value);
                    break;
            }
        } else if (keyPath.length == 3) {
            String category = keyPath[0];
            String subcategory = keyPath[1];
            String key = keyPath[2];
            
            if ("monitoring".equals(category) && "jmx".equals(subcategory)) {
                if (config.monitoring == null) config.monitoring = new LuceeServerConfig.MonitoringConfig();
                if (config.monitoring.jmx == null) config.monitoring.jmx = new LuceeServerConfig.JmxConfig();
                setJMXConfigValue(config.monitoring.jmx, key, value);
            }
        }
    }
    
    private String getJVMConfigValue(LuceeServerConfig.JvmConfig jvm, String key) {
        if (jvm == null) return null;
        switch (key) {
            case "maxMemory": return jvm.maxMemory;
            case "minMemory": return jvm.minMemory;
            case "additionalArgs": return jvm.additionalArgs != null ? String.join(" ", jvm.additionalArgs) : null;
            default: return null;
        }
    }
    
    private void setJVMConfigValue(LuceeServerConfig.JvmConfig jvm, String key, String value) {
        switch (key) {
            case "maxMemory": jvm.maxMemory = value; break;
            case "minMemory": jvm.minMemory = value; break;
            case "additionalArgs": 
                if (value != null && !value.trim().isEmpty()) {
                    jvm.additionalArgs = value.split("\\s+");
                } else {
                    jvm.additionalArgs = new String[0];
                }
                break;
        }
    }
    
    private String getMonitoringConfigValue(LuceeServerConfig.MonitoringConfig monitoring, String key) {
        if (monitoring == null) return null;
        switch (key) {
            case "enabled": return String.valueOf(monitoring.enabled);
            default: return null;
        }
    }
    
    private void setMonitoringConfigValue(LuceeServerConfig.MonitoringConfig monitoring, String key, String value) {
        switch (key) {
            case "enabled": monitoring.enabled = Boolean.parseBoolean(value); break;
        }
    }
    
    private String getJMXConfigValue(LuceeServerConfig.JmxConfig jmx, String key) {
        if (jmx == null) return null;
        switch (key) {
            case "port": return String.valueOf(jmx.port);
            default: return null;
        }
    }
    
    private void setJMXConfigValue(LuceeServerConfig.JmxConfig jmx, String key, String value) {
        switch (key) {
            case "port": 
                try { 
                    jmx.port = Integer.parseInt(value); 
                } catch (NumberFormatException e) { 
                    System.err.println("Invalid JMX port number: " + value); 
                }
                break;
        }
    }
    
    /**
     * Get the path to the version cache file
     */
    private Path getCacheFilePath() throws IOException {
        Path cacheDir = Paths.get(CACHE_DIR);
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        return cacheDir.resolve(VERSION_CACHE_FILE);
    }
    
    /**
     * Fetch versions from the Lucee update API
     */
    private List<String> fetchVersionsFromAPI() throws IOException, InterruptedException {
        String apiUrl = "https://update.lucee.org/rest/update/provider/list";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "LuCLI/1.0")
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch versions: HTTP " + response.statusCode());
        }
        
        return parseVersionsFromResponse(response.body());
    }
    
    /**
     * Parse versions from the API response
     */
    private List<String> parseVersionsFromResponse(String jsonResponse) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);
        
        Set<String> versionSet = new TreeSet<>(Collections.reverseOrder()); // Sort versions in descending order
        
        if (root.isArray()) {
            for (JsonNode item : root) {
                JsonNode versionNode = item.get("version");
                if (versionNode != null && versionNode.isTextual()) {
                    String version = versionNode.asText();
                    
                    // Filter out snapshot and RC versions for cleaner completion
                    // Only include stable releases (no -SNAPSHOT or -RC suffix)
                    if (!version.contains("-SNAPSHOT") && !version.contains("-RC")) {
                        versionSet.add(version);
                    }
                }
            }
        }
        
        return new ArrayList<>(versionSet);
    }
    
    /**
     * Load versions from cache file
     */
    private List<String> loadVersionsFromCache(Path cacheFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        
        String jsonContent = Files.readString(cacheFile);
        JsonNode root = mapper.readTree(jsonContent);
        
        List<String> versions = new ArrayList<>();
        JsonNode versionsNode = root.get("versions");
        
        if (versionsNode != null && versionsNode.isArray()) {
            for (JsonNode version : versionsNode) {
                if (version.isTextual()) {
                    versions.add(version.asText());
                }
            }
        }
        
        return versions;
    }
    
    /**
     * Save versions to cache file
     */
    private void saveVersionsToCache(List<String> versions, Path cacheFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("versions", versions);
        cacheData.put("lastUpdated", System.currentTimeMillis());
        cacheData.put("source", "https://update.lucee.org/rest/update/provider/list");
        
        String jsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cacheData);
        Files.writeString(cacheFile, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
