package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceeServerManagerVersionChangeTest {

    @TempDir
    Path tempDir;

    private String previousLucliHome;

    @BeforeEach
    void setUp() {
        previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", tempDir.resolve(".lucli-home").toString());
    }

    @AfterEach
    void tearDown() {
        if (previousLucliHome == null) {
            System.clearProperty("lucli.home");
        } else {
            System.setProperty("lucli.home", previousLucliHome);
        }
    }

    @Test
    void shouldRecreateServerForVersionChange_whenVersionMarkerDiffers_returnsTrue() throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Path serverDir = manager.getServersDir().resolve("version-diff");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve(".lucee-version"), "6.2.2.91");
        Files.writeString(serverDir.resolve(".lucee-variant"), "standard");

        LuceeServerConfig.ServerConfig config = serverConfig("7.0.4.34", "standard");
        assertTrue(manager.shouldRecreateServerForVersionChange(serverDir, config, "lucee-express"));
    }

    @Test
    void shouldRecreateServerForVersionChange_whenMarkersMatch_returnsFalse() throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Path serverDir = manager.getServersDir().resolve("version-same");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve(".lucee-version"), "7.0.4.34");
        Files.writeString(serverDir.resolve(".lucee-variant"), "standard");

        LuceeServerConfig.ServerConfig config = serverConfig("7.0.4.34", "standard");
        assertFalse(manager.shouldRecreateServerForVersionChange(serverDir, config, "lucee-express"));
    }

    @Test
    void shouldRecreateServerForVersionChange_whenLegacyExpressHasNoMarkers_returnsTrue() throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Path serverDir = manager.getServersDir().resolve("legacy-express");
        Files.createDirectories(serverDir);

        LuceeServerConfig.ServerConfig config = serverConfig("7.0.4.34", "standard");
        assertTrue(manager.shouldRecreateServerForVersionChange(serverDir, config, "lucee-express"));
    }

    @Test
    void shouldRecreateServerForVersionChange_whenTomcatJarMatches_returnsFalse() throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Path serverDir = manager.getServersDir().resolve("tomcat-match");
        Path libDir = serverDir.resolve("lib");
        Files.createDirectories(libDir);
        Files.writeString(libDir.resolve("lucee-6.2.2.91.jar"), "stub");

        LuceeServerConfig.ServerConfig config = serverConfig("6.2.2.91", "standard");
        assertFalse(manager.shouldRecreateServerForVersionChange(serverDir, config, "tomcat"));
    }

    @Test
    void shouldRecreateServerForVersionChange_whenTomcatJarDiffers_returnsTrue() throws Exception {
        LuceeServerManager manager = new LuceeServerManager();
        Path serverDir = manager.getServersDir().resolve("tomcat-diff");
        Path libDir = serverDir.resolve("lib");
        Files.createDirectories(libDir);
        Files.writeString(libDir.resolve("lucee-6.2.2.91.jar"), "stub");

        LuceeServerConfig.ServerConfig config = serverConfig("7.0.4.34", "standard");
        assertTrue(manager.shouldRecreateServerForVersionChange(serverDir, config, "tomcat"));
    }

    private LuceeServerConfig.ServerConfig serverConfig(String version, String variant) {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        LuceeServerConfig.setLuceeVersion(config, version);
        if (config.lucee == null) {
            config.lucee = new LuceeServerConfig.LuceeEngineConfig();
        }
        config.lucee.variant = variant;
        return config;
    }
}
