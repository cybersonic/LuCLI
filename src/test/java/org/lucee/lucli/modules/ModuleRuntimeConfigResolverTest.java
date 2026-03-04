package org.lucee.lucli.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.secrets.LocalSecretStore;

public class ModuleRuntimeConfigResolverTest {

    @TempDir
    Path tempDir;

    private final Map<String, String> originalScriptEnv = new HashMap<>(LuCLI.scriptEnvironment);
    private final String originalLucliHome = System.getProperty("lucli.home");

    @AfterEach
    void cleanup() {
        LuCLI.scriptEnvironment.clear();
        LuCLI.scriptEnvironment.putAll(originalScriptEnv);
        if (originalLucliHome == null) {
            System.clearProperty("lucli.home");
        } else {
            System.setProperty("lucli.home", originalLucliHome);
        }
    }

    @Test
    void resolvesFromDotEnvLucliAndIncludesAmbientInCompatibilityMode() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve(".env.lucli"), """
            FOO=from-file
            """);

        ModuleConfig config = writeAndLoadModule(projectDir.resolve("modules").resolve("demo"), """
            {
              "name": "demo",
              "version": "1.0.0",
              "description": "demo",
              "main": "Module.cfc",
              "permissions": {
                "env": [{ "alias": "FOO", "required": true }]
              }
            }
            """);

        LuCLI.scriptEnvironment.clear();
        LuCLI.scriptEnvironment.put("BAR", "ambient-value");

        ModuleRuntimeConfigResolver resolver = new ModuleRuntimeConfigResolver(false, false);
        ModuleRuntimeConfigResolver.ResolutionResult result = resolver.resolve("demo", config, projectDir);

        assertEquals("from-file", result.getEnvVars().get("FOO"));
        assertTrue(result.getServerEnv().containsKey("BAR"), "compatibility env should keep ambient keys");
        assertEquals("ambient-value", result.getServerEnv().get("BAR"));
    }

    @Test
    void strictModeOnlyInjectsDeclaredValues() throws Exception {
        Path projectDir = tempDir.resolve("strict-project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve(".env.lucli"), """
            FOO=from-file
            """);

        ModuleConfig config = writeAndLoadModule(projectDir.resolve("modules").resolve("demo"), """
            {
              "name": "demo",
              "version": "1.0.0",
              "description": "demo",
              "main": "Module.cfc",
              "permissions": {
                "env": [{ "alias": "FOO", "required": true }]
              }
            }
            """);

        LuCLI.scriptEnvironment.clear();
        LuCLI.scriptEnvironment.put("BAR", "ambient-value");

        ModuleRuntimeConfigResolver resolver = new ModuleRuntimeConfigResolver(true, false);
        ModuleRuntimeConfigResolver.ResolutionResult result = resolver.resolve("demo", config, projectDir);

        assertTrue(result.getServerEnv().containsKey("FOO"));
        assertFalse(result.getServerEnv().containsKey("BAR"), "strict env should drop undeclared ambient keys");
    }

    @Test
    void resolvesSecretPlaceholdersFromLocalSecretStore() throws Exception {
        Path projectDir = tempDir.resolve("secret-project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve(".env.lucli"), """
            BITBUCKET_AUTH_TOKEN=#secret:bitbucket.authToken#
            """);

        Path moduleDir = projectDir.resolve("modules").resolve("demo");
        ModuleConfig config = writeAndLoadModule(moduleDir, """
            {
              "name": "demo",
              "version": "1.0.0",
              "description": "demo",
              "main": "Module.cfc",
              "permissions": {
                "secrets": [{ "alias": "BITBUCKET_AUTH_TOKEN", "required": true }]
              }
            }
            """);

        Path lucliHome = tempDir.resolve("lucli-home");
        System.setProperty("lucli.home", lucliHome.toString());
        Path storePath = lucliHome.resolve("secrets").resolve("local.json");
        LocalSecretStore store = new LocalSecretStore(storePath, "test-pass".toCharArray());
        store.put("bitbucket.authToken", "secret-value".toCharArray(), "test");

        LuCLI.scriptEnvironment.clear();
        ModuleRuntimeConfigResolver resolver =
            new ModuleRuntimeConfigResolver(true, false, "test-pass".toCharArray());
        ModuleRuntimeConfigResolver.ResolutionResult result = resolver.resolve("demo", config, projectDir);

        assertEquals("secret-value", result.getSecrets().get("BITBUCKET_AUTH_TOKEN"));
        assertEquals("secret-value", result.getServerEnv().get("BITBUCKET_AUTH_TOKEN"));
    }

    private ModuleConfig writeAndLoadModule(Path moduleDir, String moduleJson) throws Exception {
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("module.json"), moduleJson);
        return ModuleConfig.load(moduleDir);
    }
}
