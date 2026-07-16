package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    @Test
    void loadLifecycleConfigForServer_usesPersistedConfigFileMarkerForStopHooks() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-config-marker");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("project-config-marker");
            Files.createDirectories(projectDir);

            Files.writeString(projectDir.resolve("lucee.json"), """
                    {
                      "name": "config-marker-server",
                      "events": {
                        "before": {
                          "serverStop": [
                            "echo default-stop > stop.txt"
                          ]
                        }
                      }
                    }
                    """);

            Files.writeString(projectDir.resolve("lucee-static.json"), """
                    {
                      "name": "config-marker-server",
                      "events": {
                        "before": {
                          "serverStop": [
                            "echo static-stop-base > stop.txt"
                          ]
                        }
                      },
                      "environments": {
                        "prod": {
                          "events": {
                            "before": {
                              "serverStop": [
                                "echo static-stop-prod > stop.txt"
                              ]
                            }
                          }
                        }
                      }
                    }
                    """);

            Path serverDir = lucliHome.resolve("servers").resolve("config-marker-server");
            Files.createDirectories(serverDir);
            Files.writeString(serverDir.resolve(".config-file"), "lucee-static.json");
            Files.writeString(serverDir.resolve(".environment"), "prod");

            Method method = LuceeServerManager.class
                    .getDeclaredMethod("loadLifecycleConfigForServer", Path.class, Path.class);
            method.setAccessible(true);
            LuceeServerConfig.ServerConfig config =
                    (LuceeServerConfig.ServerConfig) method.invoke(manager, projectDir, serverDir);

            assertNotNull(config, "Lifecycle config should load when persisted config marker exists");
            assertNotNull(config.events);
            assertNotNull(config.events.before);
            assertNotNull(config.events.before.serverStop);
            assertEquals("echo static-stop-prod > stop.txt", config.events.before.serverStop.get(0),
                    "Stop hooks should come from persisted config file with environment overrides");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void persistBootstrapOverridesForNewConfig_appliesPortOverrideAndDerivedShutdownPort() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-bootstrap-overrides");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("bootstrap-overrides-project");
            Files.createDirectories(projectDir);

            // Simulate first run where loadConfig creates default lucee.json.
            LuceeServerConfig.loadConfig(projectDir, "lucee.json");

            LuceeServerManager.StartConfigOverrides overrides = new LuceeServerManager.StartConfigOverrides();
            overrides.portOverride = 8123;

            Method persistMethod = LuceeServerManager.class.getDeclaredMethod(
                    "persistBootstrapOverridesForNewConfig",
                    Path.class,
                    String.class,
                    boolean.class,
                    LuceeServerManager.StartConfigOverrides.class,
                    String.class
            );
            persistMethod.setAccessible(true);
            persistMethod.invoke(manager, projectDir, "lucee.json", true, overrides, null);

            LuceeServerConfig.ServerConfig persisted = LuceeServerConfig.loadConfig(projectDir, "lucee.json");
            assertEquals(8123, persisted.port,
                    "Newly created lucee.json should persist the first-run --port override");
            assertEquals(9123, persisted.shutdownPort,
                    "Shutdown port should track the derived HTTP+1000 value when default-derived");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void autoInstallExtensionDependenciesIfEnabled_propagatesBeforeDepsHookFailure() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-before-deps-fail");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("project-before-deps-fail");
            Files.createDirectories(projectDir);

            Files.writeString(projectDir.resolve("lucee.json"), """
                    {
                      "name": "before-deps-fail-server",
                      "dependencySettings": {
                        "autoInstallOnServerStart": true
                      },
                      "events": {
                        "before": {
                          "depsInstall": [
                            "exit 9"
                          ]
                        }
                      }
                    }
                    """);

            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            Method method = LuceeServerManager.class.getDeclaredMethod(
                    "autoInstallExtensionDependenciesIfEnabled",
                    Path.class,
                    String.class,
                    LuceeServerConfig.ServerConfig.class
            );
            method.setAccessible(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                try {
                    method.invoke(manager, projectDir, null, config);
                } catch (InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof IllegalStateException) {
                        throw (IllegalStateException) cause;
                    }
                    throw new RuntimeException(cause);
                }
            });
            assertTrue(ex.getMessage().contains("events.before.depsInstall"),
                    "before.depsInstall hook failures should propagate as startup errors");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void autoInstallExtensionDependenciesIfEnabled_propagatesAfterDepsHookFailure() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-after-deps-fail");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("project-after-deps-fail");
            Files.createDirectories(projectDir);

            Files.writeString(projectDir.resolve("lucee.json"), """
                    {
                      "name": "after-deps-fail-server",
                      "dependencySettings": {
                        "autoInstallOnServerStart": true
                      },
                      "events": {
                        "after": {
                          "depsInstall": [
                            "exit 11"
                          ]
                        }
                      }
                    }
                    """);

            LuceeServerConfig.ServerConfig config = LuceeServerConfig.loadConfig(projectDir);
            Method method = LuceeServerManager.class.getDeclaredMethod(
                    "autoInstallExtensionDependenciesIfEnabled",
                    Path.class,
                    String.class,
                    LuceeServerConfig.ServerConfig.class
            );
            method.setAccessible(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                try {
                    method.invoke(manager, projectDir, null, config);
                } catch (InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof IllegalStateException) {
                        throw (IllegalStateException) cause;
                    }
                    throw new RuntimeException(cause);
                }
            });
            assertTrue(ex.getMessage().contains("events.after.depsInstall"),
                    "after.depsInstall hook failures should propagate as startup errors");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void startServerInternal_runsStartFailureHooksForPreProviderValidationErrors() throws Exception {
        Path lucliHome = tempDir.resolve(".lucli-home-start-failure-pre-provider");
        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            LuceeServerManager manager = new LuceeServerManager();
            Path projectDir = tempDir.resolve("project-start-failure-pre-provider");
            Files.createDirectories(projectDir);

            Files.writeString(projectDir.resolve("lucee.json"), """
                    {
                      "name": "start-failure-pre-provider-server",
                      "lucee": {
                        "version": "5.0.0.1"
                      },
                      "events": {
                        "on": {
                          "serverStartFailure": [
                            "echo pre-provider-failure-hook > start-failure-pre-provider.txt"
                          ]
                        }
                      }
                    }
                    """);

            Method method = LuceeServerManager.class.getDeclaredMethod(
                    "startServerInternal",
                    Path.class,
                    String.class,
                    boolean.class,
                    String.class,
                    LuceeServerManager.AgentOverrides.class,
                    String.class,
                    String.class,
                    boolean.class,
                    LuceeServerManager.StartConfigOverrides.class
            );
            method.setAccessible(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                try {
                    method.invoke(manager, projectDir, null, false, null, null, null, null, false, null);
                } catch (InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof IllegalStateException) {
                        throw (IllegalStateException) cause;
                    }
                    throw new RuntimeException(cause);
                }
            });
            assertTrue(ex.getMessage().contains("below LuCLI's supported cutoff"),
                    "Pre-provider version validation should still fail startup");
            assertTrue(Files.exists(projectDir.resolve("start-failure-pre-provider.txt")),
                    "events.on.serverStartFailure should run even when startup fails before provider.start");
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
