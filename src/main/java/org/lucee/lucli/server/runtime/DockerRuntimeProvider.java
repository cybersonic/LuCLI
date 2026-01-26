package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Runtime provider for the "docker" runtime type.
 *
 * First-pass implementation focused on sane defaults so that users can enable
 * Docker simply with:
 *
 *   "runtime": "docker"
 *
 * Advanced fields (image/tag/containerName/runMode) can be configured via the
 * existing RuntimeConfig structure when needed, but are optional.
 */
public final class DockerRuntimeProvider implements RuntimeProvider {

    private static final String DEFAULT_IMAGE = "lucee/lucee";
    private static final String DEFAULT_TAG = "latest";
    private static final String DEFAULT_APP_PATH = "/app";
    private static final String DEFAULT_CATALINA_BASE_PATH = "/opt/lucee/tomcat";
    private static final int DEFAULT_CONTAINER_HTTP_PORT = 8080;

    @Override
    public String getType() {
        return "docker";
    }

    @Override
    public LuceeServerManager.ServerInstance start(
            LuceeServerManager manager,
            LuceeServerConfig.ServerConfig config,
            Path projectDir,
            String environment,
            LuceeServerManager.AgentOverrides agentOverrides,
            boolean foreground,
            boolean forceReplace
    ) throws Exception {
        // Normalize runtime configuration and apply lightweight defaults.
        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        System.out.println("Using runtime.type=\"docker\"");

        // Resolve port conflicts similar to other runtimes and fail fast with
        // helpful diagnostics when conflicts exist.
        LuceeServerConfig.PortConflictResult portResult =
                LuceeServerConfig.resolvePortConflicts(config, false, manager);
        manager.checkAndReportPortConflicts(config, portResult);
        config = portResult.updatedConfig;

        // Prepare per-server instance directory under ~/.lucli/servers
        Path serversDir = manager.getServersDir();
        Path serverInstanceDir = serversDir.resolve(config.name);
        if (Files.exists(serverInstanceDir) && forceReplace) {
            deleteDirectoryRecursively(serverInstanceDir);
        }
        Files.createDirectories(serverInstanceDir);
        Files.createDirectories(serverInstanceDir.resolve("logs"));

        // Build docker run command using sane defaults.
        List<String> command = buildDockerRunCommand(config, rt, projectDir, serverInstanceDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir.toFile());
        // For now we always run detached and redirect logs to the server dir.
        pb.redirectOutput(serverInstanceDir.resolve("logs/docker.out").toFile());
        pb.redirectError(serverInstanceDir.resolve("logs/docker.err").toFile());

        // Start the docker process. In detached mode docker will exit quickly
        // after starting the container.
        Process process = pb.start();

        // Persist project and environment markers similar to other runtimes.
        Files.writeString(serverInstanceDir.resolve(".project-path"),
                projectDir.toAbsolutePath().toString());
        if (environment != null && !environment.trim().isEmpty()) {
            Files.writeString(serverInstanceDir.resolve(".environment"), environment.trim());
        }

        // For now we don't track a real host PID for the container process; we
        // record a placeholder in server.pid so that existing tooling can still
        // display basic server info without attempting host-PID based checks.
        long pseudoPid = -1L;
        Files.writeString(serverInstanceDir.resolve("server.pid"),
                pseudoPid + ":" + config.port);

        LuceeServerManager.ServerInstance instance =
                new LuceeServerManager.ServerInstance(
                        config.name,
                        pseudoPid,
                        config.port,
                        serverInstanceDir,
                        projectDir
                );

        // Docker foreground mode is not yet implemented; for now we always
        // behave like background mode and optionally log a notice.
        if (foreground) {
            System.out.println("docker runtime does not support foreground mode yet; starting in background.");
        }

        // Reuse existing startup wait + browser behaviour.
        manager.waitForServerStartup(instance, 30);
        manager.openBrowserForServer(instance, config);

        return instance;
    }

    private List<String> buildDockerRunCommand(
            LuceeServerConfig.ServerConfig config,
            LuceeServerConfig.RuntimeConfig rt,
            Path projectDir,
            Path serverInstanceDir
    ) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");

        // Container name: overridable via runtime.containerName, otherwise
        // derive from the logical server name.
        String containerName =
                (rt.containerName != null && !rt.containerName.trim().isEmpty())
                        ? rt.containerName.trim()
                        : "lucli-" + config.name;
        cmd.add("--name");
        cmd.add(containerName);

        // Port mapping: host HTTP port (config.port) -> container's HTTP port.
        int containerHttpPort = DEFAULT_CONTAINER_HTTP_PORT;
        cmd.add("-p");
        cmd.add(config.port + ":" + containerHttpPort);

        // Volume: project directory mounted as application root (/app by default).
        String appPath = DEFAULT_APP_PATH;
        cmd.add("-v");
        cmd.add(projectDir.toAbsolutePath() + ":" + appPath);

        // Volume: server instance directory mounted as Lucee/Tomcat data directory.
        String basePath = DEFAULT_CATALINA_BASE_PATH;
        cmd.add("-v");
        cmd.add(serverInstanceDir.toAbsolutePath() + ":" + basePath);

        // Environment variables: reuse values from config where possible.
        // LUCEE_ADMIN_PASSWORD from admin.password, when present.
        if (config.admin != null && config.admin.password != null && !config.admin.password.isEmpty()) {
            cmd.add("-e");
            cmd.add("LUCEE_ADMIN_PASSWORD=" + config.admin.password);
        }

        // LUCEE_EXTENSIONS from dependency lock file via existing helper.
        String extensions = LuceeServerManager.buildLuceeExtensions(projectDir);
        if (extensions != null && !extensions.isEmpty()) {
            cmd.add("-e");
            cmd.add("LUCEE_EXTENSIONS=" + extensions);
        }

        // Additional envVars from lucee.json.
        if (config.envVars != null && !config.envVars.isEmpty()) {
            for (Map.Entry<String, String> entry : config.envVars.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.trim().isEmpty() || value == null) {
                    continue;
                }
                cmd.add("-e");
                cmd.add(key + "=" + value);
            }
        }

        // Honour basic docker runtime image/tag overrides when provided.
        String image = (rt.image != null && !rt.image.trim().isEmpty())
                ? rt.image.trim()
                : DEFAULT_IMAGE;
        String tag = (rt.tag != null && !rt.tag.trim().isEmpty())
                ? rt.tag.trim()
                : DEFAULT_TAG;

        cmd.add(image + ":" + tag);
        return cmd;
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Best-effort cleanup; log and continue.
                        System.err.println("Warning: Failed to delete " + path + ": " + e.getMessage());
                    }
                });
    }
}
