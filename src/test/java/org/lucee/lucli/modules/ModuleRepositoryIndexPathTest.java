package org.lucee.lucli.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.profile.CliProfile;
import org.lucee.lucli.profile.DefaultProfile;
import org.lucee.lucli.profile.WheelsProfile;

/**
 * Regression: {@link ModuleRepositoryIndex#getSettingsFilePath()} must route
 * through {@link org.lucee.lucli.paths.LucliPaths} so the active profile
 * determines the home directory, instead of hard-coding {@code ~/.lucli}.
 */
class ModuleRepositoryIndexPathTest {

    @TempDir
    Path fakeHome;

    private CliProfile savedProfile;
    private String savedLucliHome;

    @BeforeEach
    void setUp() {
        savedProfile = LuCLI.getActiveProfile();
        savedLucliHome = System.getProperty("lucli.home");
        System.clearProperty("lucli.home");
    }

    @AfterEach
    void tearDown() {
        LuCLI.setActiveProfile(savedProfile);
        if (savedLucliHome != null) {
            System.setProperty("lucli.home", savedLucliHome);
        } else {
            System.clearProperty("lucli.home");
        }
    }

    @Test
    void settingsFileTracksLucliHomeSystemProperty() throws Exception {
        LuCLI.setActiveProfile(new DefaultProfile());
        Path home = fakeHome.resolve(".lucli-override");
        System.setProperty("lucli.home", home.toString());

        assertEquals(
            home.resolve("settings.json").toAbsolutePath().normalize(),
            invokeGetSettingsFilePath()
        );
    }

    @Test
    void settingsFileTracksWheelsProfileWhenFallingBackToDefaultHome() throws Exception {
        // No lucli.home override -> LucliPaths falls back to the active profile's
        // default home directory. Under the Wheels profile this must resolve to
        // ~/.wheels/settings.json, not ~/.lucli/settings.json.
        LuCLI.setActiveProfile(new WheelsProfile());

        Path expected = Path.of(System.getProperty("user.home"), ".wheels", "settings.json")
            .toAbsolutePath()
            .normalize();
        assertEquals(expected, invokeGetSettingsFilePath());
    }

    /**
     * Reflectively invoke the private static helper so the regression test can
     * observe its output without changing visibility in production code.
     */
    private Path invokeGetSettingsFilePath() throws Exception {
        Method method = ModuleRepositoryIndex.class.getDeclaredMethod("getSettingsFilePath");
        method.setAccessible(true);
        Object result = method.invoke(null);
        return ((Path) result).toAbsolutePath().normalize();
    }
}
