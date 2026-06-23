package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
