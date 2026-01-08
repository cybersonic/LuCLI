package org.lucee.lucli.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper for programmatically editing lucee.json (adding/updating dependencies).
 */
public class LuceeJsonEditor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final Path projectDir;
    private final Path configFile;
    private ObjectNode root;

    public LuceeJsonEditor(Path projectDir) throws IOException {
        this.projectDir = projectDir;
        this.configFile = projectDir.resolve("lucee.json");
        load();
    }

    private void load() throws IOException {
        if (!Files.exists(configFile)) {
            throw new IOException("lucee.json not found in " + projectDir.toAbsolutePath());
        }
        JsonNode node = MAPPER.readTree(configFile.toFile());
        if (!(node instanceof ObjectNode)) {
            throw new IOException("lucee.json must be a JSON object at the root");
        }
        this.root = (ObjectNode) node;
    }

    /**
     * Add or update a dependency entry under dependencies or devDependencies.
     *
     * @param name          dependency key
     * @param dep           config
     * @param devDependency true for devDependencies, false for dependencies
     */
    public void addOrUpdateDependency(String name, DependencyConfig dep, boolean devDependency) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Dependency name cannot be empty");
        }
        name = name.trim();

        // Make sure defaults are applied so we persist a sensible config
        dep.setName(name);
        dep.applyDefaults();

        ObjectNode depsNode = getOrCreateObject(devDependency ? "devDependencies" : "dependencies");

        // Build JSON object matching the schema's dependency definition
        ObjectNode depNode = MAPPER.createObjectNode();

        putIfNotNull(depNode, "type", dep.getType());
        putIfNotNull(depNode, "version", dep.getVersion());
        putIfNotNull(depNode, "source", dep.getSource());
        putIfNotNull(depNode, "url", dep.getUrl());
        putIfNotNull(depNode, "ref", dep.getRef());
        putIfNotNull(depNode, "subPath", dep.getSubPath());
        putIfNotNull(depNode, "installPath", dep.getInstallPath());
        putIfNotNull(depNode, "mapping", dep.getMapping());
        putIfNotNull(depNode, "path", dep.getPath());
        putIfNotNull(depNode, "groupId", dep.getGroupId());
        putIfNotNull(depNode, "artifactId", dep.getArtifactId());
        putIfNotNull(depNode, "repository", dep.getRepository());
        // For extensions, store the raw id field; DependencyConfig will resolve it at install time
        if (dep.getRawId() != null && !dep.getRawId().trim().isEmpty()) {
            depNode.put("id", dep.getRawId());
        }

        depsNode.set(name, depNode);
    }

    private ObjectNode getOrCreateObject(String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node instanceof ObjectNode) {
            return (ObjectNode) node;
        }
        ObjectNode created = MAPPER.createObjectNode();
        root.set(fieldName, created);
        return created;
    }

    private static void putIfNotNull(ObjectNode node, String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            node.put(field, value);
        }
    }

    public void save() throws IOException {
        MAPPER.writeValue(configFile.toFile(), root);
    }
}
