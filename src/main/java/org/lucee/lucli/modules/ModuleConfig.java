package org.lucee.lucli.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final List<PermissionRequirement> envPermissions;
    private final List<PermissionRequirement> secretPermissions;
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

    /**
     * Represents a declared runtime input required by a module.
     */
    public static class PermissionRequirement {
        private final String alias;
        private final boolean required;
        private final String description;

        public PermissionRequirement(String alias, boolean required, String description) {
            this.alias = alias;
            this.required = required;
            this.description = description != null ? description : "";
        }

        public String getAlias() { return alias; }
        public boolean isRequired() { return required; }
        public String getDescription() { return description; }
    }

    private ModuleConfig(Path moduleDir, String name, String version, String description,
                         String author, String license, String main, String repository,
                         List<String> tags, List<Dependency> dependencies,
                         List<PermissionRequirement> envPermissions,
                         List<PermissionRequirement> secretPermissions,
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
        this.envPermissions = Collections.unmodifiableList(envPermissions);
        this.secretPermissions = Collections.unmodifiableList(secretPermissions);
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
        List<PermissionRequirement> envPermissions = new ArrayList<>();
        List<PermissionRequirement> secretPermissions = new ArrayList<>();
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

                parsePermissions(root, envPermissions, secretPermissions);
                valid = true;

            } catch (IOException e) {
                // Failed to parse, use defaults
            }
        }

        // Determine status based on filesystem
        String status = determineStatus(moduleDir);

        return new ModuleConfig(moduleDir, name, version, description, author, license,
                                main, repository, tags, dependencies, envPermissions,
                                secretPermissions, valid, status);
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
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            "AVAILABLE"
        );
    }

    private static void parsePermissions(JsonNode root,
                                         List<PermissionRequirement> envPermissions,
                                         List<PermissionRequirement> secretPermissions) {
        Map<String, PermissionRequirement> envByAlias = new LinkedHashMap<>();
        Map<String, PermissionRequirement> secretByAlias = new LinkedHashMap<>();

        JsonNode permissionsNode = root.get("permissions");
        if (permissionsNode != null && permissionsNode.isObject()) {
            parsePermissionArray(permissionsNode.get("env"), envByAlias, false);
            parsePermissionArray(permissionsNode.get("secrets"), secretByAlias, false);
        }

        // Legacy/top-level shortcuts (always treated as required aliases).
        parseShortcutArray(root.get("envVars"), envByAlias);
        parseShortcutArray(root.get("secrets"), secretByAlias);

        envPermissions.addAll(envByAlias.values());
        secretPermissions.addAll(secretByAlias.values());
    }

    private static void parsePermissionArray(JsonNode arrayNode,
                                             Map<String, PermissionRequirement> out,
                                             boolean requiredDefault) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (JsonNode item : arrayNode) {
            if (item.isTextual()) {
                String alias = item.asText().trim();
                if (!alias.isEmpty()) {
                    out.putIfAbsent(alias, new PermissionRequirement(alias, requiredDefault, ""));
                }
                continue;
            }
            if (!item.isObject()) {
                continue;
            }
            String alias = getStringOrDefault(item, "alias", null);
            if (alias == null || alias.isBlank()) {
                continue;
            }
            boolean required = item.has("required") ? item.get("required").asBoolean(requiredDefault) : requiredDefault;
            String description = getStringOrDefault(item, "description", "");
            out.putIfAbsent(alias, new PermissionRequirement(alias, required, description));
        }
    }

    private static void parseShortcutArray(JsonNode arrayNode, Map<String, PermissionRequirement> out) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (JsonNode item : arrayNode) {
            if (!item.isTextual()) {
                continue;
            }
            String alias = item.asText().trim();
            if (!alias.isEmpty() && !out.containsKey(alias)) {
                out.put(alias, new PermissionRequirement(alias, true, ""));
            }
        }
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
    public List<PermissionRequirement> getEnvPermissions() { return envPermissions; }
    public List<PermissionRequirement> getSecretPermissions() { return secretPermissions; }
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

    /**
     * Whether this module declares explicit env or secret requirements.
     */
    public boolean hasDeclaredPermissions() {
        return !envPermissions.isEmpty() || !secretPermissions.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("ModuleConfig{name='%s', version='%s', status='%s'}",
                             name, version, status);
    }
}
