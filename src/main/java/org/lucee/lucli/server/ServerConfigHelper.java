package org.lucee.lucli.server;

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
        keys.add("shutdownPort");
        keys.add("name");
        keys.add("host");
        keys.add("webroot");
        keys.add("jvm.maxMemory");
        keys.add("jvm.minMemory");
        keys.add("jvm.additionalArgs");
        keys.add("monitoring.enabled");
        keys.add("monitoring.jmx.port");
        keys.add("admin.enabled");
        keys.add("admin.password");
        keys.add("enableLucee");
        keys.add("enableREST");
        keys.add("urlRewrite.enabled");
        keys.add("urlRewrite.routerFile");
        keys.add("openBrowser");
        keys.add("openBrowserURL");
        keys.add("https.enabled");
        keys.add("https.port");
        keys.add("https.redirect");
        keys.add("ajp.enabled");
        keys.add("ajp.port");
        keys.add("configurationFile");
        keys.add("envFile");
        return keys;
    }
    
    /**
     * Check if a key is known/documented in the configuration
     */
    public boolean isKnownKey(String key) {
        return getAvailableKeys().contains(key);
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
                case "shutdownPort":
                    return config.shutdownPort != null ? String.valueOf(config.shutdownPort) : null;
                case "name":
                    return config.name;
                case "host":
                    return config.host;
                case "webroot":
                    return config.webroot;
                case "enableLucee":
                    return String.valueOf(config.enableLucee);
                case "enableREST":
                    return String.valueOf(config.enableREST);
                case "openBrowser":
                    return String.valueOf(config.openBrowser);
                case "openBrowserURL":
                    return config.openBrowserURL;
                case "configurationFile":
                    return config.configurationFile;
                case "envFile":
                    return config.envFile;
            }
        } else if (keyPath.length == 2) {
            String category = keyPath[0];
            String key = keyPath[1];
            
            switch (category) {
                case "jvm":
                    return getJVMConfigValue(config.jvm, key);
                case "monitoring":
                    return getMonitoringConfigValue(config.monitoring, key);
                case "admin":
                    return getAdminConfigValue(config.admin, key);
                case "urlRewrite":
                    return getUrlRewriteConfigValue(config.urlRewrite, key);
                case "https":
                    return getHttpsConfigValue(config.https, key);
                case "ajp":
                    return getAjpConfigValue(config.ajp, key);
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
                case "shutdownPort":
                    try {
                        config.shutdownPort = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid shutdown port number: " + value);
                    }
                    break;
                case "name":
                    config.name = value;
                    break;
                case "host":
                    config.host = value;
                    break;
                case "webroot":
                    config.webroot = value;
                    break;
                case "enableLucee":
                    config.enableLucee = Boolean.parseBoolean(value);
                    break;
                case "enableREST":
                    config.enableREST = Boolean.parseBoolean(value);
                    break;
                case "openBrowser":
                    config.openBrowser = Boolean.parseBoolean(value);
                    break;
                case "openBrowserURL":
                    config.openBrowserURL = value;
                    break;
                case "configurationFile":
                    config.configurationFile = value;
                    break;
                case "envFile":
                    config.envFile = value;
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
                case "admin":
                    if (config.admin == null) config.admin = new LuceeServerConfig.AdminConfig();
                    setAdminConfigValue(config.admin, key, value);
                    break;
                case "urlRewrite":
                    if (config.urlRewrite == null) config.urlRewrite = new LuceeServerConfig.UrlRewriteConfig();
                    setUrlRewriteConfigValue(config.urlRewrite, key, value);
                    break;
                case "https":
                    if (config.https == null) config.https = new LuceeServerConfig.HttpsConfig();
                    setHttpsConfigValue(config.https, key, value);
                    break;
                case "ajp":
                    if (config.ajp == null) config.ajp = new LuceeServerConfig.AjpConfig();
                    setAjpConfigValue(config.ajp, key, value);
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
    
    private String getAdminConfigValue(LuceeServerConfig.AdminConfig admin, String key) {
        if (admin == null) return null;
        switch (key) {
            case "enabled": return String.valueOf(admin.enabled);
            case "password": return admin.password;
            default: return null;
        }
    }
    
    private void setAdminConfigValue(LuceeServerConfig.AdminConfig admin, String key, String value) {
        switch (key) {
            case "enabled": admin.enabled = Boolean.parseBoolean(value); break;
            case "password": admin.password = value; break;
        }
    }
    
    private String getAjpConfigValue(LuceeServerConfig.AjpConfig ajp, String key) {
        if (ajp == null) return null;
        switch (key) {
            case "enabled": return String.valueOf(ajp.enabled);
            case "port": return ajp.port != null ? String.valueOf(ajp.port) : null;
            default: return null;
        }
    }
    
    private void setAjpConfigValue(LuceeServerConfig.AjpConfig ajp, String key, String value) {
        switch (key) {
            case "enabled":
                ajp.enabled = Boolean.parseBoolean(value);
                break;
            case "port":
                try {
                    ajp.port = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid AJP port number: " + value);
                }
                break;
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
    
    private String getUrlRewriteConfigValue(LuceeServerConfig.UrlRewriteConfig urlRewrite, String key) {
        if (urlRewrite == null) return null;
        switch (key) {
            case "enabled":
                return String.valueOf(urlRewrite.enabled);
            case "routerFile":
                return urlRewrite.routerFile;
            default:
                return null;
        }
    }
    
    private void setUrlRewriteConfigValue(LuceeServerConfig.UrlRewriteConfig urlRewrite, String key, String value) {
        switch (key) {
            case "enabled":
                urlRewrite.enabled = Boolean.parseBoolean(value);
                break;
            case "routerFile":
                urlRewrite.routerFile = value;
                break;
        }
    }

    private String getHttpsConfigValue(LuceeServerConfig.HttpsConfig https, String key) {
        if (https == null) return null;
        switch (key) {
            case "enabled":
                return String.valueOf(https.enabled);
            case "port":
                return https.port != null ? String.valueOf(https.port) : null;
            case "redirect":
                return https.redirect != null ? String.valueOf(https.redirect) : null;
            default:
                return null;
        }
    }

    private void setHttpsConfigValue(LuceeServerConfig.HttpsConfig https, String key, String value) {
        switch (key) {
            case "enabled":
                https.enabled = Boolean.parseBoolean(value);
                break;
            case "port":
                try {
                    https.port = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid HTTPS port number: " + value);
                }
                break;
            case "redirect":
                https.redirect = Boolean.parseBoolean(value);
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
                    versionSet.add(version);
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
