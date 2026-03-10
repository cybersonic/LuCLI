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
}
