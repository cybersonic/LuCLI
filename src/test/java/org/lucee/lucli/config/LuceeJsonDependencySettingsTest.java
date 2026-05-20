package org.lucee.lucli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuceeJsonDependencySettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void dependencySettings_useLockFile_defaultsToDisabled() {
        DependencySettingsConfig settings = new DependencySettingsConfig();

        assertNull(settings.getUseLockFile());
        assertFalse(settings.isUseLockFileEnabled());
    }

    @Test
    void dependencySettings_materializeExtensionsOnInstall_defaultsToEnabled() {
        DependencySettingsConfig settings = new DependencySettingsConfig();

        assertNull(settings.getMaterializeExtensionsOnInstall());
        assertTrue(settings.isMaterializeExtensionsOnInstallEnabled());
    }

    @Test
    void luceeJson_parsesUseLockFileWhenEnabled() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "lock-enabled-test",
              "dependencySettings": {
                "useLockFile": true
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);

        assertEquals(Boolean.TRUE, config.getDependencySettings().getUseLockFile());
        assertTrue(config.getDependencySettings().isUseLockFileEnabled());
    }

    @Test
    void environmentOverride_canDisableUseLockFile() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-disable-test",
              "dependencySettings": {
                "useLockFile": true
              },
              "environments": {
                "prod": {
                  "dependencySettings": {
                    "useLockFile": false
                  }
                }
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);
        assertTrue(config.getDependencySettings().isUseLockFileEnabled());

        config.applyEnvironment("prod");

        assertEquals(Boolean.FALSE, config.getDependencySettings().getUseLockFile());
        assertFalse(config.getDependencySettings().isUseLockFileEnabled());
    }

    @Test
    void environmentOverride_canEnableUseLockFile() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-enable-test",
              "dependencySettings": {},
              "environments": {
                "dev": {
                  "dependencySettings": {
                    "useLockFile": true
                  }
                }
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);
        assertFalse(config.getDependencySettings().isUseLockFileEnabled());

        config.applyEnvironment("dev");

        assertEquals(Boolean.TRUE, config.getDependencySettings().getUseLockFile());
        assertTrue(config.getDependencySettings().isUseLockFileEnabled());
    }

    @Test
    void luceeJson_parsesMaterializeExtensionsOnInstallWhenDisabled() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "materialize-disabled-test",
              "dependencySettings": {
                "materializeExtensionsOnInstall": false
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);

        assertEquals(Boolean.FALSE, config.getDependencySettings().getMaterializeExtensionsOnInstall());
        assertFalse(config.getDependencySettings().isMaterializeExtensionsOnInstallEnabled());
    }

    @Test
    void environmentOverride_canDisableMaterializeExtensionsOnInstall() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-materialize-disable-test",
              "dependencySettings": {
                "materializeExtensionsOnInstall": true
              },
              "environments": {
                "prod": {
                  "dependencySettings": {
                    "materializeExtensionsOnInstall": false
                  }
                }
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);
        assertTrue(config.getDependencySettings().isMaterializeExtensionsOnInstallEnabled());

        config.applyEnvironment("prod");

        assertEquals(Boolean.FALSE, config.getDependencySettings().getMaterializeExtensionsOnInstall());
        assertFalse(config.getDependencySettings().isMaterializeExtensionsOnInstallEnabled());
    }

    @Test
    void dependency_enabled_defaultsToTrue() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "enabled-default-test",
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
                }
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);
        java.util.List<DependencyConfig> deps = config.parseDependencies();

        assertEquals(1, deps.size());
        assertTrue(deps.get(0).isEnabled());
    }

    @Test
    void environmentOverride_canDisableDependencyViaEnabledFlag() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "dep-enabled-override-test",
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
                  "enabled": true
                }
              },
              "environments": {
                "prod": {
                  "dependencies": {
                    "h2": {
                      "enabled": false
                    }
                  }
                }
              }
            }
            """);

        LuceeJsonConfig config = LuceeJsonConfig.load(tempDir);
        assertTrue(config.parseDependencies().get(0).isEnabled());

        config.applyEnvironment("prod");

        assertFalse(config.parseDependencies().get(0).isEnabled());
    }
}
