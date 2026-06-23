package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.server.LuceeServerConfig;

public class DockerRuntimeProviderTest {

    @TempDir
    Path tempDir;

    private DockerRuntimeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DockerRuntimeProvider();
    }

    @Test
    void buildDockerRunCommand_includesEnvFileVariables() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "RUNTIME_ENV=from-env-file\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "docker-envfile-test";
        config.port = 8080;
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";

        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        List<String> command = invokeBuildDockerRunCommand(
                config,
                config.runtime,
                tempDir,
                tempDir.resolve("server-instance"),
                "lucli-envfile-test");

        assertTrue(command.contains("RUNTIME_ENV=from-env-file"),
                "Docker runtime command should include env vars sourced from envFile/.env");
    }

    @Test
    void buildDockerRunCommand_envVarsOverrideEnvFileValues() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "APP_MODE=from-env-file\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "docker-env-override-test";
        config.port = 8080;
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";
        config.envVars.put("APP_MODE", "from-config");

        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        List<String> command = invokeBuildDockerRunCommand(
                config,
                config.runtime,
                tempDir,
                tempDir.resolve("server-instance"),
                "lucli-env-override-test");

        assertTrue(command.contains("APP_MODE=from-config"),
                "envVars should override values loaded from envFile/.env");
        assertFalse(command.contains("APP_MODE=from-env-file"),
                "Docker runtime command should not contain overridden envFile value");
    }

    @Test
    void buildDockerRunCommand_envVarsNullUnsetsEnvFileValue() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "APP_MODE=from-env-file\n");

        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "docker-env-unset-test";
        config.port = 8080;
        config.runtime = new LuceeServerConfig.RuntimeConfig();
        config.runtime.type = "docker";
        config.envVars.put("APP_MODE", null);

        LuceeServerConfig.reloadConfiguredEnvFile(config, tempDir);

        List<String> command = invokeBuildDockerRunCommand(
                config,
                config.runtime,
                tempDir,
                tempDir.resolve("server-instance"),
                "lucli-env-unset-test");

        assertFalse(command.contains("APP_MODE=from-env-file"),
                "null envVars value should unset APP_MODE inherited from envFile/.env");
        assertFalse(command.stream().anyMatch(value -> value.startsWith("APP_MODE=")),
                "Docker runtime command should not include APP_MODE when explicitly unset");
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeBuildDockerRunCommand(
            LuceeServerConfig.ServerConfig config,
            LuceeServerConfig.RuntimeConfig runtime,
            Path projectDir,
            Path serverInstanceDir,
            String containerName) throws Exception {
        Method method = DockerRuntimeProvider.class.getDeclaredMethod(
                "buildDockerRunCommand",
                LuceeServerConfig.ServerConfig.class,
                LuceeServerConfig.RuntimeConfig.class,
                Path.class,
                Path.class,
                String.class);
        method.setAccessible(true);
        try {
            return (List<String>) method.invoke(provider, config, runtime, projectDir, serverInstanceDir, containerName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
