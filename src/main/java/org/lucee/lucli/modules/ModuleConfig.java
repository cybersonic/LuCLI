package org.lucee.lucli.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a loaded module.json configuration file.
 * 
 * Loads the JSON once and provides typed access to all fields with sensible defaults.
 * Also detects module status (DEV, INSTALLED) based on filesystem state.
 * 
 * Usage:
 * <pre>
 * ModuleConfig config = ModuleConfig.load(moduleDir);
 * String name = config.getName();
 * String version = config.getVersion();
 * </pre>
 */
public class ModuleConfig {
    
    // Default values
    private static final String DEFAULT_VERSION = "0.0.0";
    private static final String DEFAULT_DESCRIPTION = "No description available";
    private static final String DEFAULT_AUTHOR = "";
    private static final String DEFAULT_LICENSE = "";
    
    private final Path moduleDir;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String license;
    private final String main;
    private final String repository;
    private final List<String> tags;
    private final List<Dependency> dependencies;
    private final boolean valid;
    private final String status;
    
    /**
     * Represents a module dependency
     */
    public static class Dependency {
        private final String name;
        private final String version;
        
        public Dependency(String name, String version) {
            this.name = name;
            this.version = version != null ? version : "*";
        }
        
        public String getName() { return name; }
        public String getVersion() { return version; }
        
        @Override
        public String toString() {
            return name + "@" + version;
        }
    }
    
    private ModuleConfig(Path moduleDir, String name, String version, String description,
                         String author, String license, String main, String repository,
                         List<String> tags, List<Dependency> dependencies, 
                         boolean valid, String status) {
        this.moduleDir = moduleDir;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.license = license;
        this.main = main;
        this.repository = repository;
        this.tags = Collections.unmodifiableList(tags);
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.valid = valid;
        this.status = status;
    }
    
    /**
     * Load a module configuration from a directory.
     * 
     * @param moduleDir the module directory containing module.json
     * @return ModuleConfig with values from module.json or defaults
     */
    public static ModuleConfig load(Path moduleDir) {
        String name = moduleDir.getFileName().toString();
        String version = DEFAULT_VERSION;
        String description = DEFAULT_DESCRIPTION;
        String author = DEFAULT_AUTHOR;
        String license = DEFAULT_LICENSE;
        String main = null;
        String repository = null;
        List<String> tags = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
        boolean valid = false;
        
        Path moduleJsonPath = moduleDir.resolve("module.json");
        
        if (Files.exists(moduleJsonPath)) {
            try {
                String json = Files.readString(moduleJsonPath);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);
                
                // Extract all fields with defaults
                name = getStringOrDefault(root, "name", name);
                version = getStringOrDefault(root, "version", DEFAULT_VERSION);
                description = getStringOrDefault(root, "description", DEFAULT_DESCRIPTION);
                author = getStringOrDefault(root, "author", DEFAULT_AUTHOR);
                license = getStringOrDefault(root, "license", DEFAULT_LICENSE);
                main = getStringOrDefault(root, "main", null);
                repository = getStringOrDefault(root, "repository", null);
                
                // Parse tags array
                JsonNode tagsNode = root.get("tags");
                if (tagsNode != null && tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        if (tag.isTextual()) {
                            tags.add(tag.asText());
                        }
                    }
                }
                
                // Parse dependencies object
                JsonNode depsNode = root.get("dependencies");
                if (depsNode != null && depsNode.isObject()) {
                    depsNode.fields().forEachRemaining(entry -> {
                        dependencies.add(new Dependency(entry.getKey(), entry.getValue().asText("*")));
                    });
                }
                
                valid = true;
                
            } catch (IOException e) {
                // Failed to parse, use defaults
            }
        }
        
        // Determine status based on filesystem
        String status = determineStatus(moduleDir);
        
        return new ModuleConfig(moduleDir, name, version, description, author, license,
                                main, repository, tags, dependencies, valid, status);
    }
    
    /**
     * Create an "unavailable" config for modules that exist in repo but aren't installed
     */
    public static ModuleConfig unavailable(String name, String description, String version) {
        return new ModuleConfig(
            null,
            name,
            version != null ? version : DEFAULT_VERSION,
            description != null ? description : DEFAULT_DESCRIPTION,
            DEFAULT_AUTHOR,
            DEFAULT_LICENSE,
            null,
            null,
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            "AVAILABLE"
        );
    }
    
    private static String getStringOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node != null && node.isTextual() && !node.asText().trim().isEmpty()) {
            return node.asText().trim();
        }
        return defaultValue;
    }
    
    private static String determineStatus(Path moduleDir) {
        if (moduleDir == null || !Files.exists(moduleDir)) {
            return "AVAILABLE";
        }
        // Check if it's a git working copy (development module)
        if (Files.isDirectory(moduleDir.resolve(".git"))) {
            return "DEV";
        }
        return "INSTALLED";
    }
    
    // Getters
    
    public Path getModuleDir() { return moduleDir; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getLicense() { return license; }
    public String getMain() { return main; }
    public String getRepository() { return repository; }
    public List<String> getTags() { return tags; }
    public List<Dependency> getDependencies() { return dependencies; }
    public boolean isValid() { return valid; }
    public String getStatus() { return status; }
    
    /**
     * Check if the module is installed locally
     */
    public boolean isInstalled() {
        return moduleDir != null && Files.exists(moduleDir);
    }
    
    /**
     * Check if this is a development module (has .git directory)
     */
    public boolean isDev() {
        return "DEV".equals(status);
    }
    
    @Override
    public String toString() {
        return String.format("ModuleConfig{name='%s', version='%s', status='%s'}", 
                             name, version, status);
    }
}
