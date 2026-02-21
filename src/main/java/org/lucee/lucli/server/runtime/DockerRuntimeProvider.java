package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Runtime provider for the "docker" runtime type.
 *
 * <strong>EXPERIMENTAL</strong> — this provider works for basic use-cases but
 * may not yet cover all Docker image variants or advanced configuration.
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
    /** The official lucee/lucee image uses docBase=/var/www in server.xml. */
    private static final String DEFAULT_APP_PATH = "/var/www";
    /** The official lucee/lucee image runs Tomcat on port 8888. */
    private static final int DEFAULT_CONTAINER_HTTP_PORT = 8888;

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

        System.out.println("Using runtime.type=\"docker\" (experimental)");

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
            org.lucee.lucli.server.TomcatConfigSupport.deleteDirectoryRecursively(serverInstanceDir);
        }
        Files.createDirectories(serverInstanceDir);
        Files.createDirectories(serverInstanceDir.resolve("logs"));

        // Resolve the container name early so we can clean up stale containers.
        String containerName =
                (rt.containerName != null && !rt.containerName.trim().isEmpty())
                        ? rt.containerName.trim()
                        : "lucli-" + config.name;

        // Remove any stale container with the same name (stopped or otherwise)
        // so that `docker run --name` doesn't conflict.
        removeStaleContainer(containerName);

        // Build docker run command using sane defaults.
        List<String> command = buildDockerRunCommand(config, rt, projectDir, serverInstanceDir, containerName);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir.toFile());
        // Capture stdout (container ID) and stderr separately.
        pb.redirectOutput(serverInstanceDir.resolve("logs/docker.out").toFile());
        pb.redirectError(serverInstanceDir.resolve("logs/docker.err").toFile());

        // Start the docker process. In detached mode docker exits quickly
        // after starting the container.
        Process process = pb.start();

        // Wait for `docker run -d` to finish and check exit code.
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("docker run timed out after 30 seconds");
        }
        if (process.exitValue() != 0) {
            String errContent = "";
            Path errFile = serverInstanceDir.resolve("logs/docker.err");
            if (Files.exists(errFile)) {
                errContent = Files.readString(errFile).trim();
            }
            throw new IOException("docker run failed (exit " + process.exitValue() + "): " + errContent);
        }

        // Persist project and environment markers similar to other runtimes.
        Files.writeString(serverInstanceDir.resolve(".project-path"),
                projectDir.toAbsolutePath().toString());
        if (environment != null && !environment.trim().isEmpty()) {
            Files.writeString(serverInstanceDir.resolve(".environment"), environment.trim());
        }

        // Write container name marker so stop/status can manage the container.
        Files.writeString(serverInstanceDir.resolve(".docker-container"), containerName);

        // We don't have a host PID for the container; record a placeholder in
        // server.pid so that existing tooling can display basic server info.
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

    /**
     * Remove a stale Docker container with the given name, if it exists.
     * This is a best-effort operation; errors are silently ignored.
     */
    private void removeStaleContainer(String containerName) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Best-effort cleanup
        }
    }

    /**
     * Check whether a Docker container with the given name is currently running.
     */
    public static boolean isDockerContainerRunning(String containerName) {
        try {
            Process p = new ProcessBuilder(
                    "docker", "inspect", "--format", "{{.State.Running}}", containerName)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0 && "true".equals(output);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stop and remove a Docker container by name.
     * Returns true if the container was stopped.
     */
    public static boolean stopDockerContainer(String containerName) {
        try {
            Process stop = new ProcessBuilder("docker", "stop", containerName)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            stop.waitFor(30, TimeUnit.SECONDS);

            // Remove the stopped container
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);

            return stop.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> buildDockerRunCommand(
            LuceeServerConfig.ServerConfig config,
            LuceeServerConfig.RuntimeConfig rt,
            Path projectDir,
            Path serverInstanceDir,
            String containerName
    ) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");

        cmd.add("--name");
        cmd.add(containerName);

        // Port mapping: host HTTP port (config.port) -> container's HTTP port.
        int containerHttpPort = DEFAULT_CONTAINER_HTTP_PORT;
        cmd.add("-p");
        cmd.add(config.port + ":" + containerHttpPort);

        // Volume: project directory mounted as the Lucee webroot.
        // The default lucee/lucee image serves from /var/www — mount there.
        String appPath = DEFAULT_APP_PATH;
        cmd.add("-v");
        cmd.add(projectDir.toAbsolutePath() + ":" + appPath);

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

}
