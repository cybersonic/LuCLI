package org.lucee.lucli.deps;

import org.lucee.lucli.LuCLI;
import org.lucee.lucli.config.DependencyConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of dependency shortcuts loaded from classpath resources.
 *
 * Expected JSON shape:
 * {
 *   "shortcut": { ... dependency fields ... }
 * }
 */
public class DependencyShortcutRegistry {

    private static final String RESOURCE_PATH = "/dependencies/dependency-shortcuts.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, DependencyConfig> shortcuts;

    private DependencyShortcutRegistry(Map<String, DependencyConfig> shortcuts) {
        this.shortcuts = shortcuts;
    }

    /**
     * Load the bundled dependency shortcut registry from resources.
     */
    public static DependencyShortcutRegistry loadDefault() {
        Map<String, DependencyConfig> loaded = new LinkedHashMap<>();
        try (InputStream is = DependencyShortcutRegistry.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                return new DependencyShortcutRegistry(Collections.emptyMap());
            }

            JsonNode root = MAPPER.readTree(is);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(entry -> {
                    String shortcut = normalizeShortcut(entry.getKey());
                    if (shortcut == null) {
                        return;
                    }

                    JsonNode depNode = entry.getValue();
                    if (depNode == null || !depNode.isObject()) {
                        return;
                    }

                    try {
                        DependencyConfig dep = MAPPER.treeToValue(depNode, DependencyConfig.class);
                        if (dep.getName() == null || dep.getName().isBlank()) {
                            dep.setName(entry.getKey().trim());
                        }
                        dep.applyDefaults();
                        loaded.put(shortcut, dep);
                    } catch (Exception e) {
                        if (LuCLI.verbose || LuCLI.debug) {
                            System.err.println("Warning: Skipping invalid dependency shortcut '" + entry.getKey() + "': " + e.getMessage());
                        }
                    }
                });
            }
        } catch (IOException e) {
            if (LuCLI.verbose || LuCLI.debug) {
                System.err.println("Warning: Failed to load dependency shortcuts from " + RESOURCE_PATH + ": " + e.getMessage());
            }
            return new DependencyShortcutRegistry(Collections.emptyMap());
        }

        return new DependencyShortcutRegistry(Collections.unmodifiableMap(loaded));
    }

    /**
     * Resolve a dependency shortcut into a copy of its dependency template.
     *
     * @param shortcut shortcut name (case-insensitive)
     * @return cloned dependency config template, or null if unknown
     */
    public DependencyConfig resolveShortcut(String shortcut) {
        String key = normalizeShortcut(shortcut);
        if (key == null) {
            return null;
        }
        DependencyConfig dep = shortcuts.get(key);
        if (dep == null) {
            return null;
        }
        return cloneDependency(dep);
    }

    /**
     * List all registered shortcut names.
     */
    public Set<String> listShortcuts() {
        return shortcuts.keySet();
    }

    private static String normalizeShortcut(String shortcut) {
        if (shortcut == null) {
            return null;
        }
        String normalized = shortcut.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private static DependencyConfig cloneDependency(DependencyConfig source) {
        DependencyConfig copy = new DependencyConfig();
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setVersion(source.getVersion());
        copy.setSource(source.getSource());
        copy.setUrl(source.getUrl());
        copy.setRef(source.getRef());
        copy.setSubPath(source.getSubPath());
        copy.setInstallPath(source.getInstallPath());
        copy.setMapping(source.getMapping());
        copy.setPath(source.getPath());
        copy.setGroupId(source.getGroupId());
        copy.setArtifactId(source.getArtifactId());
        copy.setRepository(source.getRepository());
        copy.setId(source.getRawId());
        copy.setEnabled(source.getEnabled());
        return copy;
    }
}
