package org.lucee.lucli.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.profile.CliProfile;
import org.lucee.lucli.profile.DefaultProfile;
import org.lucee.lucli.profile.MarkspressoProfile;
import org.lucee.lucli.profile.WheelsProfile;

class LucliPathsTest {

    private CliProfile savedProfile;

    @BeforeEach
    void saveProfile() {
        savedProfile = LuCLI.getActiveProfile();
        // Reset to default so tests have a known starting point
        LuCLI.setActiveProfile(new DefaultProfile());
    }

    @AfterEach
    void restoreProfile() {
        LuCLI.setActiveProfile(savedProfile);
    }

    @Test
    void resolvePrefersSystemPropertyOverEnvironment() {
        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            "/tmp/lucli-prop",
            "/tmp/lucli-env",
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/lucli-prop").toAbsolutePath().normalize(), paths.home());
        assertEquals("system-property", paths.homeSource());
        assertEquals(Path.of("/tmp/.lucli_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void resolveUsesEnvironmentWhenSystemPropertyMissing() {
        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            null,
            "/tmp/lucli-env",
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/lucli-env").toAbsolutePath().normalize(), paths.home());
        assertEquals("environment", paths.homeSource());
        assertEquals(Path.of("/tmp/.lucli_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void resolveFallsBackToUserHomeDefault() {
        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            null,
            null,
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/home/.lucli").toAbsolutePath().normalize(), paths.home());
        assertEquals("default", paths.homeSource());
        assertEquals(Path.of("/tmp/home/.lucli_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void resolveFallsBackToWheelsHomeWhenProfileIsWheels() {
        LuCLI.setActiveProfile(new WheelsProfile());

        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            null,
            null,
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/home/.wheels").toAbsolutePath().normalize(), paths.home());
        assertEquals("default", paths.homeSource());
        assertEquals(Path.of("/tmp/home/.wheels_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void derivedPathsAreBuiltFromHome() {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(Path.of("/tmp/lucli"), "test");

        assertEquals(Path.of("/tmp/lucli").toAbsolutePath().normalize(), paths.home());
        assertEquals(Path.of("/tmp/lucli/servers").toAbsolutePath().normalize(), paths.serversDir());
        assertEquals(Path.of("/tmp/lucli/express").toAbsolutePath().normalize(), paths.expressDir());
        assertEquals(Path.of("/tmp/lucli/deps/git-cache").toAbsolutePath().normalize(), paths.depsGitCacheDir());
        assertEquals(Path.of("/tmp/lucli/modules").toAbsolutePath().normalize(), paths.modulesDir());
        assertEquals(Path.of("/tmp/lucli/secrets/local.json").toAbsolutePath().normalize(), paths.secretsStoreFile());
        assertEquals(Path.of("/tmp/lucli/settings.json").toAbsolutePath().normalize(), paths.settingsFile());
    }

    @Test
    void forHomeBackupsDirFollowsActiveProfile() {
        LuCLI.setActiveProfile(new WheelsProfile());

        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(Path.of("/tmp/wheels"), "test");

        assertEquals(Path.of("/tmp/.wheels_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void resolveFallsBackToMarkspressoHomeWhenProfileIsMarkspresso() {
        LuCLI.setActiveProfile(new MarkspressoProfile());

        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            null,
            null,
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/home/.markspresso").toAbsolutePath().normalize(), paths.home());
        assertEquals("default", paths.homeSource());
        assertEquals(Path.of("/tmp/home/.markspresso_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void forHomeBackupsDirDefaultsToLucliProfile() {
        LucliPaths.ResolvedPaths paths = LucliPaths.forHome(Path.of("/tmp/lucli"), "test");

        assertEquals(Path.of("/tmp/.lucli_backups").toAbsolutePath().normalize(), paths.backupsDir());
    }

    @Test
    void resolveSupportsExplicitBackupPathOverride() {
        LucliPaths.ResolvedPaths paths = LucliPaths.resolve(
            "/tmp/lucli",
            null,
            "/var/backups/lucli",
            null,
            "/tmp/home"
        );

        assertEquals(Path.of("/tmp/lucli").toAbsolutePath().normalize(), paths.home());
        assertEquals(Path.of("/var/backups/lucli").toAbsolutePath().normalize(), paths.backupsDir());
    }
}
