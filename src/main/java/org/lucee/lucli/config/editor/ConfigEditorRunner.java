package org.lucee.lucli.config.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerConfig.ServerConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MVP runner for a schema-driven lucee.json editor.
 *
 * For now this focuses on the "General" tab fields and uses
 * a simple prompt-based editor rather than a full curses UI.
 */
public class ConfigEditorRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void run(Path projectDir, Path configFile, String environment) throws IOException {
        if (!Files.exists(configFile)) {
            System.err.println("❌ Config file not found: " + configFile.toAbsolutePath());
            return;
        }

        // Load server config via existing helper so we respect defaults/env vars.
        ServerConfig baseConfig = LuceeServerConfig.loadConfig(projectDir, configFile.getFileName().toString());
        ServerConfig effectiveConfig = baseConfig;

        if (environment != null && !environment.trim().isEmpty()) {
            try {
                effectiveConfig = LuceeServerConfig.applyEnvironment(baseConfig, environment.trim());
            } catch (IllegalArgumentException ex) {
                System.err.println("❌ " + ex.getMessage());
                return;
            }
        }

        // Load JSON schema (v1) for field descriptions and future layout / validation hints.
        JsonNode schema = loadSchema(projectDir);

        // Use the prompt-based ConfigEditor for now (portable and easy to extend
        // with small UX niceties). We keep the TUI around for potential future use.
        ConfigEditor editor = new ConfigEditor();
        boolean changed = editor.edit(projectDir, configFile, effectiveConfig, schema, environment);

        if (!changed) {
            System.out.println("No changes made.");
        }
    }

    private JsonNode loadSchema(Path projectDir) {
        // Try the standard path under the project first
        Path schemaPath = projectDir.resolve("schemas/v1/lucee.schema.json");
        try {
            if (Files.exists(schemaPath)) {
                return MAPPER.readTree(schemaPath.toFile());
            }
        } catch (IOException e) {
            System.err.println("⚠️ Failed to load schema from " + schemaPath + ": " + e.getMessage());
        }
        return null;
    }
}
