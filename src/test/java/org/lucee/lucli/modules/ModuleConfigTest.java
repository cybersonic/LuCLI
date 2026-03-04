package org.lucee.lucli.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ModuleConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesPermissionsAndShortcutAliases() throws Exception {
        Path moduleDir = tempDir.resolve("demo-module");
        Files.createDirectories(moduleDir);

        Files.writeString(moduleDir.resolve("module.json"), """
            {
              "name": "demo-module",
              "version": "1.0.0",
              "description": "demo",
              "main": "Module.cfc",
              "permissions": {
                "env": [
                  { "alias": "BITBUCKET_WORKSPACE", "required": true, "description": "workspace" }
                ],
                "secrets": [
                  { "alias": "BITBUCKET_AUTH_TOKEN", "required": true, "description": "token" }
                ]
              },
              "envVars": ["BITBUCKET_REPO_SLUG"],
              "secrets": ["BITBUCKET_AUTH_TOKEN"]
            }
            """);

        ModuleConfig config = ModuleConfig.load(moduleDir);
        assertTrue(config.isValid());
        assertEquals(2, config.getEnvPermissions().size(), "expected explicit + shortcut env aliases");
        assertEquals("BITBUCKET_WORKSPACE", config.getEnvPermissions().get(0).getAlias());
        assertEquals("BITBUCKET_REPO_SLUG", config.getEnvPermissions().get(1).getAlias());
        assertEquals(1, config.getSecretPermissions().size(), "duplicate secret alias should collapse");
        assertEquals("BITBUCKET_AUTH_TOKEN", config.getSecretPermissions().get(0).getAlias());
        assertTrue(config.getSecretPermissions().get(0).isRequired());
    }

    @Test
    void shortcutsAreMarkedRequired() throws Exception {
        Path moduleDir = tempDir.resolve("demo-shortcuts");
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("module.json"), """
            {
              "name": "demo-shortcuts",
              "version": "1.0.0",
              "description": "demo",
              "main": "Module.cfc",
              "envVars": ["FOO"],
              "secrets": ["BAR"]
            }
            """);

        ModuleConfig config = ModuleConfig.load(moduleDir);
        assertEquals(1, config.getEnvPermissions().size());
        assertEquals("FOO", config.getEnvPermissions().get(0).getAlias());
        assertTrue(config.getEnvPermissions().get(0).isRequired());
        assertEquals(1, config.getSecretPermissions().size());
        assertEquals("BAR", config.getSecretPermissions().get(0).getAlias());
        assertTrue(config.getSecretPermissions().get(0).isRequired());
    }
}
