package org.lucee.lucli.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration parser for lucee.json focusing on dependency management
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LuceeJsonConfig {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_COMMENTS);
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("dependencies")
    private Map<String, Object> dependencies;
    
    @JsonProperty("devDependencies")
    private Map<String, Object> devDependencies;
    
    @JsonProperty("dependencySettings")
    private DependencySettingsConfig dependencySettings;
    
    // Support legacy "packages" key
    @JsonProperty("packages")
    private DependencySettingsConfig packages;
    
    @JsonProperty("environments")
    private Map<String, EnvironmentConfig> environments = new HashMap<>();
    
    /**
     * Parse lucee.json from the given directory
     */
    public static LuceeJsonConfig load(Path projectDir) throws IOException {
        Path configFile = projectDir.resolve("lucee.json");
        
        if (!Files.exists(configFile)) {
            throw new IOException("lucee.json not found in " + projectDir);
        }
        
        return objectMapper.readValue(configFile.toFile(), LuceeJsonConfig.class);
    }
    
    /**
     * Parse dependencies map into list of DependencyConfig objects
     */
    public List<DependencyConfig> parseDependencies() {
        if (dependencies == null) {
            return new ArrayList<>();
        }
        return parseDependencyMap(dependencies);
    }
    
    /**
     * Parse devDependencies map into list of DependencyConfig objects
     */
    public List<DependencyConfig> parseDevDependencies() {
        if (devDependencies == null) {
            return new ArrayList<>();
        }
        return parseDependencyMap(devDependencies);
    }
    
    /**
     * Parse a dependency map (either dependencies or devDependencies)
     */
    private List<DependencyConfig> parseDependencyMap(Map<String, Object> depMap) {
        List<DependencyConfig> result = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : depMap.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            
            DependencyConfig dep = new DependencyConfig(name);
            
            if (value instanceof String) {
                // Short-hand: "framework-one": "4.3.0"
                dep.setVersion((String) value);
            } else if (value instanceof Map) {
                // Full object: "framework-one": { "version": "4.3.0", "source": "git" }
                @SuppressWarnings("unchecked")
                Map<String, Object> valueMap = (Map<String, Object>) value;
                populateDependencyFromMap(dep, valueMap);
            }
            
            // Apply defaults
            dep.applyDefaults();
            
            result.add(dep);
        }
        
        return result;
    }
    
    /**
     * Populate DependencyConfig from a map
     */
    private void populateDependencyFromMap(DependencyConfig dep, Map<String, Object> map) {
        if (map.containsKey("type")) {
            dep.setType((String) map.get("type"));
        }
        if (map.containsKey("version")) {
            dep.setVersion((String) map.get("version"));
        }
        if (map.containsKey("source")) {
            dep.setSource((String) map.get("source"));
        }
        if (map.containsKey("url")) {
            dep.setUrl((String) map.get("url"));
        }
        if (map.containsKey("ref")) {
            dep.setRef((String) map.get("ref"));
        }
        if (map.containsKey("subPath")) {
            dep.setSubPath((String) map.get("subPath"));
        }
        if (map.containsKey("installPath")) {
            dep.setInstallPath((String) map.get("installPath"));
        }
        if (map.containsKey("mapping")) {
            dep.setMapping((String) map.get("mapping"));
        }
        if (map.containsKey("path")) {
            dep.setPath((String) map.get("path"));
        }
        if (map.containsKey("groupId")) {
            dep.setGroupId((String) map.get("groupId"));
        }
        if (map.containsKey("artifactId")) {
            dep.setArtifactId((String) map.get("artifactId"));
        }
        if (map.containsKey("repository")) {
            dep.setRepository((String) map.get("repository"));
        }
    }
    
    /**
     * Apply environment-specific configuration
     */
    public void applyEnvironment(String envName) {
        if (envName == null || envName.trim().isEmpty()) {
            return;
        }
        
        if (!environments.containsKey(envName)) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Environment '").append(envName).append("' not found in lucee.json");
            
            if (!environments.isEmpty()) {
                errorMsg.append("\nAvailable environments: ");
                errorMsg.append(String.join(", ", environments.keySet()));
            } else {
                errorMsg.append("\nNo environments are defined in lucee.json");
            }
            
            throw new IllegalArgumentException(errorMsg.toString());
        }
        
        EnvironmentConfig env = environments.get(envName);
        
        // Merge dependencySettings configuration
        DependencySettingsConfig envSettings = env.dependencySettings != null ? env.dependencySettings : env.packages;
        if (envSettings != null) {
            mergeDependencySettings(envSettings);
        }
    }
    
    /**
     * Merge environment dependency settings into base config
     */
    private void mergeDependencySettings(DependencySettingsConfig envSettings) {
        DependencySettingsConfig base = getDependencySettings();
        if (envSettings.getInstallDevDependencies() != null) {
            base.setInstallDevDependencies(envSettings.getInstallDevDependencies());
        }
        // Add other settings as needed
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Map<String, Object> getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(Map<String, Object> dependencies) {
        this.dependencies = dependencies;
    }
    
    public Map<String, Object> getDevDependencies() {
        return devDependencies;
    }
    
    public void setDevDependencies(Map<String, Object> devDependencies) {
        this.devDependencies = devDependencies;
    }
    
    public DependencySettingsConfig getDependencySettings() {
        // Prefer dependencySettings, fallback to packages for backwards compatibility
        if (dependencySettings != null) {
            return dependencySettings;
        }
        if (packages == null) {
            packages = new DependencySettingsConfig();
        }
        return packages;
    }
    
    public void setDependencySettings(DependencySettingsConfig dependencySettings) {
        this.dependencySettings = dependencySettings;
    }
    
    @Deprecated // use getDependencySettings() instead
    public DependencySettingsConfig getPackages() {
        return getDependencySettings();
    }
    
    @Deprecated
    public void setPackages(DependencySettingsConfig packages) {
        this.packages = packages;
    }
    
    public Map<String, EnvironmentConfig> getEnvironments() {
        return environments;
    }
    
    public void setEnvironments(Map<String, EnvironmentConfig> environments) {
        this.environments = environments;
    }
    
    /**
     * Environment-specific configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnvironmentConfig {
        @JsonProperty("dependencySettings")
        public DependencySettingsConfig dependencySettings;
        
        // Support legacy "packages" key
        @JsonProperty("packages")
        public DependencySettingsConfig packages;
    }
}
