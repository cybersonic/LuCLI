package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceeServerManagerLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void getServerInfoByName_recoversLivePidFromCatalinaPidWhenServerPidIsStale() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();

            String serverName = "stale-pid-server";
            int serverPort = 18080;
            long livePid = ProcessHandle.current().pid();
            long stalePid = findUnusedPidNear(livePid + 100_000L);

            Path projectDir = tempDir.resolve("project");
            Files.createDirectories(projectDir);

            Path serverDir = lucliHome.resolve("servers").resolve(serverName);
            Files.createDirectories(serverDir);
            Files.writeString(serverDir.resolve(".project-path"), projectDir.toAbsolutePath().toString());
            Files.writeString(serverDir.resolve("server.pid"), stalePid + ":" + serverPort);
            Files.writeString(serverDir.resolve("catalina.pid"), String.valueOf(livePid));

            LuceeServerManager.ServerInfo info = manager.getServerInfoByName(serverName);
            assertNotNull(info, "Server info should resolve when server directory exists");
            assertTrue(info.isRunning(),
                    "Server should be considered running when catalina.pid points to a live process");
            assertEquals(livePid, info.getPid(),
                    "Recovered running PID should come from catalina.pid when server.pid is stale");
            assertEquals(serverPort, info.getPort(), "Recovered server info should preserve port from server.pid");

            String refreshedServerPid = Files.readString(serverDir.resolve("server.pid")).trim();
            assertEquals(livePid + ":" + serverPort, refreshedServerPid,
                    "server.pid should be refreshed to the recovered live PID for future stop/status calls");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void getRunningServer_recoversProjectScopedServerWhenOnlyCatalinaPidIsLive() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-project");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();

            String serverName = "project-stale-pid-server";
            int serverPort = 18081;
            long livePid = ProcessHandle.current().pid();
            long stalePid = findUnusedPidNear(livePid + 200_000L);

            Path projectDir = tempDir.resolve("project-scope");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("lucee.json"), """
                    {
                      "name": "%s",
                      "port": %d,
                      "openBrowser": false
                    }
                    """.formatted(serverName, serverPort));

            Path serverDir = lucliHome.resolve("servers").resolve(serverName);
            Files.createDirectories(serverDir);
            Files.writeString(serverDir.resolve(".project-path"), projectDir.toAbsolutePath().toString());
            Files.writeString(serverDir.resolve("server.pid"), stalePid + ":" + serverPort);
            Files.writeString(serverDir.resolve("catalina.pid"), String.valueOf(livePid));

            LuceeServerManager.ServerInstance running = manager.getRunningServer(projectDir);
            assertNotNull(running,
                    "Project-scoped running server lookup should recover from stale server.pid via catalina.pid");
            assertEquals(serverName, running.getServerName());
            assertEquals(livePid, running.getPid(),
                    "Recovered running server PID should be sourced from catalina.pid");
            assertEquals(serverPort, running.getPort());
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void runServerStartLifecycleHooks_beforeExecutesCommandInProjectDir() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-hooks");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("hooks-project");
            Files.createDirectories(projectDir);

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.events.before.serverStart = java.util.List.of("echo before-hook > before-hook.txt");

            manager.runServerStartLifecycleHooks(config, projectDir, true);

            Path hookOutput = projectDir.resolve("before-hook.txt");
            assertTrue(Files.exists(hookOutput));
            assertTrue(Files.readString(hookOutput).contains("before-hook"));
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void runServerStartLifecycleHooks_throwsWhenCommandFails() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-hooks-fail");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("hooks-project-fail");
            Files.createDirectories(projectDir);

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.events.before.serverStart = java.util.List.of("exit 7");

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> manager.runServerStartLifecycleHooks(config, projectDir, true)
            );
            assertTrue(ex.getMessage().contains("Lifecycle hook command failed"));
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void runServerRestartLifecycleHooks_executesBeforeAndAfterCommands() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-hooks-restart");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("hooks-project-restart");
            Files.createDirectories(projectDir);

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.events.before.serverRestart = java.util.List.of("echo restart-before > restart-before.txt");
            config.events.after.serverRestart = java.util.List.of("echo restart-after > restart-after.txt");

            manager.runServerRestartLifecycleHooks(config, projectDir, true);
            manager.runServerRestartLifecycleHooks(config, projectDir, false);

            assertTrue(Files.exists(projectDir.resolve("restart-before.txt")));
            assertTrue(Files.exists(projectDir.resolve("restart-after.txt")));
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void runDepsInstallLifecycleHooks_executesConfiguredCommands() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-hooks-deps");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("hooks-project-deps");
            Files.createDirectories(projectDir);

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.events.before.depsInstall = java.util.List.of("echo deps-before > deps-before.txt");
            config.events.after.depsInstall = java.util.List.of("echo deps-after > deps-after.txt");

            manager.runDepsInstallLifecycleHooks(config, projectDir, true);
            manager.runDepsInstallLifecycleHooks(config, projectDir, false);

            assertTrue(Files.exists(projectDir.resolve("deps-before.txt")));
            assertTrue(Files.exists(projectDir.resolve("deps-after.txt")));
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void runServerStartFailureLifecycleHooks_executesConfiguredCommands() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-hooks-failure");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("hooks-project-failure");
            Files.createDirectories(projectDir);

            LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
            config.events.on.serverStartFailure = java.util.List.of("echo start-failure > start-failure.txt");

            manager.runServerStartFailureLifecycleHooks(config, projectDir);

            assertTrue(Files.exists(projectDir.resolve("start-failure.txt")));
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    private long findUnusedPidNear(long candidate) {
        long pid = Math.max(100L, candidate);
        for (int i = 0; i < 10_000; i++) {
            if (ProcessHandle.of(pid).isEmpty()) {
                return pid;
            }
            pid++;
        }
        return candidate + 500_000L;
    }
}
