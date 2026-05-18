package org.lucee.lucli.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledModuleInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void installBundledModulesFromPath_copiesNewModules() throws Exception {
        Path bundledRoot = tempDir.resolve("bundled");
        Path modulesDir = tempDir.resolve("home").resolve("modules");
        createModule(bundledRoot, "markspresso", "bundled-markspresso");
        createModule(bundledRoot, "bitbucket", "bundled-bitbucket");

        BundledModuleInstaller.installBundledModulesFromPath(bundledRoot, modulesDir);

        assertTrue(Files.exists(modulesDir.resolve("markspresso").resolve("Module.cfc")));
        assertTrue(Files.exists(modulesDir.resolve("markspresso").resolve("module.json")));
        assertTrue(Files.exists(modulesDir.resolve("bitbucket").resolve("Module.cfc")));
        assertEquals(
            "bundled-markspresso",
            Files.readString(modulesDir.resolve("markspresso").resolve("README.md")).trim()
        );
    }

    @Test
    void installBundledModulesFromPath_doesNotOverwriteExistingInstalledModule() throws Exception {
        Path bundledRoot = tempDir.resolve("bundled");
        Path modulesDir = tempDir.resolve("home").resolve("modules");
        createModule(bundledRoot, "markspresso", "bundled");
        createModule(modulesDir, "markspresso", "installed");

        BundledModuleInstaller.installBundledModulesFromPath(bundledRoot, modulesDir);

        assertEquals(
            "installed",
            Files.readString(modulesDir.resolve("markspresso").resolve("README.md")).trim()
        );
    }

    private static void createModule(Path root, String moduleName, String readmeText) throws Exception {
        Path moduleDir = root.resolve(moduleName);
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("Module.cfc"), "component {}");
        Files.writeString(moduleDir.resolve("module.json"), "{\"name\":\"" + moduleName + "\"}");
        Files.writeString(moduleDir.resolve("README.md"), readmeText);
    }
}
