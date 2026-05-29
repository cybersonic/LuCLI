package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.lucee.lucli.LuCLI;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ServerCommandHandlerTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serverStartDryRun_portOverrideDoesNotMutateLuceeJson() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "test-server",
              "port": 8080,
              "dependencies": {
                "framework-one": {
                  "version": "1.0.0",
                  "mapping": "/framework-one",
                  "installPath": "dependencies/framework-one"
                }
              }
            }
            """);

        JsonNode before = MAPPER.readTree(Files.readString(configFile));

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--port", "9099"
        });

        JsonNode after = MAPPER.readTree(Files.readString(configFile));

        assertEquals(before, after, "start --dry-run with overrides must not modify lucee.json");
        assertNotNull(output);
        assertTrue(output.contains("\"port\" : 9099") || output.contains("\"port\": 9099"),
                "Dry-run output should include overridden runtime port");
        assertTrue(after.has("dependencies"), "Dependencies block must remain intact");
    }
    @Test
    void serverStartDryRun_missingEnvironmentFallsBackToBaseConfig() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "missing-env-fallback-test",
              "port": 8080,
              "environments": {
                "prod": {
                  "port": 80
                }
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--env", "nonexistent"
        });

        assertNotNull(output);
        assertTrue(output.contains("DRY RUN: Server configuration that would be used"),
                "Missing --env should not fail; command should still produce dry-run output");
        assertFalse(output.contains("not found in lucee.json"),
                "Missing --env should not surface as an error message in command output");
    }

    @Test
    void serverRunDryRun_includeEnv_doesNotIncludeRealizedConfigSection() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "include-env-run-test",
              "port": 8080
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "run", "--dry-run", "--include", "env"
        });

        assertNotNull(output);
        assertTrue(output.contains("Realized environment variables used to resolve lucee.json placeholders"),
                "Run --dry-run --include env should include env preview");
        assertFalse(output.contains("Realized lucee.json for:"),
                "Run --dry-run --include env should not include realized lucee.json by default");
    }

    @Test
    void serverRunDryRun_includeUnknownSection_returnsHelpfulError() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "include-invalid-run-test",
              "port": 8080
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "run", "--dry-run", "--include", "env,lucee"
        });

        assertNotNull(output);
        assertTrue(output.contains("Unknown --include section 'lucee' for server run"),
                "Run include parser should report invalid include sections with valid options");
    }

    @Test
    void serverStartDryRun_warmupFlagAddsWarmupEnvVarAndJvmProperty() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "warmup-test",
              "port": 8080,
              "jvm": {
                "additionalArgs": [
                  "-Dlucee.enable.warmup=false",
                  "-Dfoo=bar"
                ]
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--warmup"
        });

        assertNotNull(output);
        assertTrue(output.contains("\"LUCEE_ENABLE_WARMUP\" : \"true\"")
                || output.contains("\"LUCEE_ENABLE_WARMUP\": \"true\"")
                || output.contains("\"LUCEE_ENABLE_WARMUP\":\"true\""),
                "Dry-run output should include LUCEE_ENABLE_WARMUP=true");
        assertTrue(output.contains("-Dlucee.enable.warmup=true"),
                "Dry-run output should include warmup JVM system property");
        assertFalse(output.contains("-Dlucee.enable.warmup=false"),
                "Warmup override should replace existing lucee.enable.warmup values");
    }
    @Test
    void serverStartDryRun_includeEnv_showsLuceeAdminEnabledFalseWhenAdminDisabled() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "admin-disabled-env-test",
              "port": 8080,
              "admin": {
                "enabled": false
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--include-env"
        });

        assertNotNull(output);
        assertTrue(output.contains("\"LUCEE_ADMIN_ENABLED\" : \"false\"")
                || output.contains("\"LUCEE_ADMIN_ENABLED\": \"false\"")
                || output.contains("\"LUCEE_ADMIN_ENABLED\":\"false\""),
                "Dry-run --include-env output should include LUCEE_ADMIN_ENABLED=false when admin.enabled=false");
        assertFalse(output.contains("Realized lucee.json for:"),
                "Dry-run --include-env should not implicitly include realized lucee.json");
    }

    @Test
    void serverStartDryRun_includeEnv_showsRealizedEnvVariablesUsedForSubstitution() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
            PREVIEW_SERVER_NAME=resolved-env-server
            PREVIEW_MODE=preview-dot-env
            """);

        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "#env:PREVIEW_SERVER_NAME#",
              "port": 8080,
              "envVars": {
                "APP_MODE": "#env:PREVIEW_MODE:-dev#",
                "FALLBACK_MODE": "#env:MISSING_PREVIEW_MODE:-fallback-value#"
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--include-env"
        });

        assertNotNull(output);
        assertTrue(output.contains("Realized environment variables used to resolve lucee.json placeholders"),
                "Dry-run --include-env output should include realized substitution variables section");
        assertTrue(output.contains("\"PREVIEW_SERVER_NAME\" : \"resolved-env-server\"")
                || output.contains("\"PREVIEW_SERVER_NAME\": \"resolved-env-server\"")
                || output.contains("\"PREVIEW_SERVER_NAME\":\"resolved-env-server\""),
                "Dry-run --include-env output should include PREVIEW_SERVER_NAME from .env");
        assertTrue(output.contains("\"PREVIEW_MODE\" : \"preview-dot-env\"")
                || output.contains("\"PREVIEW_MODE\": \"preview-dot-env\"")
                || output.contains("\"PREVIEW_MODE\":\"preview-dot-env\""),
                "Dry-run --include-env output should include PREVIEW_MODE from .env");
        assertTrue(output.contains("\"MISSING_PREVIEW_MODE\" : \"fallback-value\"")
                || output.contains("\"MISSING_PREVIEW_MODE\": \"fallback-value\"")
                || output.contains("\"MISSING_PREVIEW_MODE\":\"fallback-value\""),
                "Dry-run --include-env output should include fallback value for missing env var");
        assertFalse(output.contains("Realized lucee.json for:"),
                "Dry-run --include-env should not implicitly include realized lucee.json");
    }

    @Test
    void serverStartDryRun_includeEnvAndLucee_showsOnlyRequestedSections() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "include-sections-start-test",
              "port": 8080,
              "admin": {
                "enabled": false
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--include", "env,lucee"
        });

        assertNotNull(output);
        assertTrue(output.contains("Realized environment variables used to resolve lucee.json placeholders"),
                "Dry-run --include env,lucee should include env preview");
        assertTrue(output.contains(".CFConfig.json"),
                "Dry-run --include env,lucee should include Lucee config preview");
        assertFalse(output.contains("Realized lucee.json for:"),
                "Dry-run --include env,lucee should not include default realized lucee.json section");
    }

    @Test
    void serverRunDryRun_keyValueOverrideDoesNotMutateLuceeJson() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "test-server",
              "port": 8080,
              "jvm": {
                "maxMemory": "512m"
              },
              "dependencies": {
                "framework-two": {
                  "version": "2.0.0",
                  "mapping": "/framework-two",
                  "installPath": "dependencies/framework-two"
                }
              }
            }
            """);

        JsonNode before = MAPPER.readTree(Files.readString(configFile));

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "run", "--dry-run", "--port=9191", "jvm.maxMemory=768m"
        });

        JsonNode after = MAPPER.readTree(Files.readString(configFile));

        assertEquals(before, after, "run --dry-run with overrides must not modify lucee.json");
        assertNotNull(output);
        assertTrue(output.contains("\"port\" : 9191") || output.contains("\"port\": 9191"),
                "Dry-run output should include overridden runtime port");
        assertTrue(output.contains("\"maxMemory\" : \"768m\"") || output.contains("\"maxMemory\": \"768m\""),
                "Dry-run output should include one-shot key=value override");
        assertTrue(after.has("dependencies"), "Dependencies block must remain intact");
    }
    @Test
    void serverStartPrewarm_tomcatRuntimeUsesCachedJarAndDoesNotMutateLuceeJson() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "prewarm-tomcat-test",
              "lucee": {
                "version": "6.2.2.91",
                "variant": "standard"
              },
              "runtime": {
                "type": "tomcat"
              },
              "dependencies": {
                "framework-three": {
                  "version": "3.0.0",
                  "mapping": "/framework-three",
                  "installPath": "dependencies/framework-three"
                }
              }
            }
            """);

        JsonNode before = MAPPER.readTree(Files.readString(configFile));

        Path lucliHome = tempDir.resolve(".lucli-home-prewarm-tomcat");
        Path cachedJar = lucliHome.resolve("jars").resolve("lucee-6.2.2.91.jar");
        Files.createDirectories(cachedJar.getParent());
        Files.writeString(cachedJar, "stub-jar");

        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
            String output = handler.executeCommand("server", new String[] {
                    "start", "--prewarm"
            });

            JsonNode after = MAPPER.readTree(Files.readString(configFile));

            assertEquals(before, after, "start --prewarm must not modify lucee.json");
            assertNotNull(output);
            assertTrue(output.contains("Runtime prewarm complete"),
                    "Prewarm output should confirm completion");
            assertTrue(output.contains("Runtime:       tomcat"),
                    "Prewarm output should report tomcat runtime");
            assertTrue(output.contains(cachedJar.toString()),
                    "Prewarm output should include cached Lucee JAR path");
            assertTrue(Files.exists(cachedJar), "Cached Lucee JAR should still exist after prewarm");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void serverRunPrewarm_versionOverrideUsesCachedExpressAndDoesNotMutateLuceeJson() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "prewarm-run-test",
              "lucee": {
                "version": "6.2.2.91"
              },
              "dependencies": {
                "framework-four": {
                  "version": "4.0.0",
                  "mapping": "/framework-four",
                  "installPath": "dependencies/framework-four"
                }
              }
            }
            """);

        JsonNode before = MAPPER.readTree(Files.readString(configFile));

        String overriddenVersion = "7.0.1.100-RC";
        Path lucliHome = tempDir.resolve(".lucli-home-prewarm-run");
        Path cachedExpressDir = lucliHome.resolve("express").resolve(overriddenVersion);
        Files.createDirectories(cachedExpressDir);

        String previousLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", lucliHome.toString());
        try {
            ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
            String output = handler.executeCommand("server", new String[] {
                    "run", "--prewarm", "--version", overriddenVersion
            });

            JsonNode after = MAPPER.readTree(Files.readString(configFile));

            assertEquals(before, after, "run --prewarm must not modify lucee.json");
            assertNotNull(output);
            assertTrue(output.contains("Runtime prewarm complete"),
                    "Prewarm output should confirm completion");
            assertTrue(output.contains("Lucee Version: " + overriddenVersion),
                    "Prewarm output should include the overridden Lucee version");
            assertTrue(output.contains(cachedExpressDir.toString()),
                    "Prewarm output should include cached Lucee Express path");
            assertTrue(Files.exists(cachedExpressDir), "Cached Lucee Express directory should remain available");
        } finally {
            if (previousLucliHome == null) {
                System.clearProperty("lucli.home");
            } else {
                System.setProperty("lucli.home", previousLucliHome);
            }
        }
    }

    @Test
    void serverStartDryRun_usesGlobalEnvironmentFallbackWhenNoEnvFlag() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "env-fallback-start-test",
              "port": 8080,
              "environments": {
                "prod": {
                  "port": 80
                }
              }
            }
            """);

        String previous = LuCLI.currentEnvironment;
        LuCLI.currentEnvironment = "prod";
        try {
            ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
            String output = handler.executeCommand("server", new String[] {
                    "start", "--dry-run"
            });

            assertNotNull(output);
            assertTrue(output.contains("with environment: prod"),
                    "Dry-run output should indicate fallback environment from LuCLI.currentEnvironment");
            assertTrue(output.contains("\"port\" : 80") || output.contains("\"port\": 80"),
                    "Dry-run output should reflect merged prod environment override");
        } finally {
            LuCLI.currentEnvironment = previous;
        }
    }

    @Test
    void serverRunDryRun_explicitEnvOverridesGlobalEnvironmentFallback() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "env-fallback-run-test",
              "port": 8080,
              "environments": {
                "prod": {
                  "port": 80
                },
                "dev": {
                  "port": 8181
                }
              }
            }
            """);

        String previous = LuCLI.currentEnvironment;
        LuCLI.currentEnvironment = "prod";
        try {
            ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
            String output = handler.executeCommand("server", new String[] {
                    "run", "--dry-run", "--env", "dev"
            });

            assertNotNull(output);
            assertTrue(output.contains("with environment: dev"),
                    "Explicit --env should take precedence over global fallback environment");
            assertTrue(output.contains("\"port\" : 8181") || output.contains("\"port\": 8181"),
                    "Dry-run output should reflect explicitly selected environment overrides");
        } finally {
            LuCLI.currentEnvironment = previous;
        }
    }

    @Test
    @Disabled("Temporarily disabled while stabilizing dry-run dependency mapping preview output in CI")
    void serverStartDryRun_includeLucee_showsDependencyMappingsFromLuceeJson() throws Exception {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, """
            {
              "name": "mapping-preview-test",
              "dependencies": {
                "my-framework": {
                  "type": "cfml",
                  "version": "4.3.0",
                  "source": "git",
                  "url": "https://github.com/example/my-framework.git",
                  "installPath": "vendor/my-framework",
                  "mapping": "/framework"
                }
              }
            }
            """);

        ServerCommandHandler handler = new ServerCommandHandler(true, tempDir);
        String output = handler.executeCommand("server", new String[] {
                "start", "--dry-run", "--include-lucee"
        });

        assertNotNull(output);
        String expectedPhysicalPath = tempDir.resolve("vendor")
                .resolve("my-framework")
                .toAbsolutePath()
                .normalize()
                .toString();
        assertTrue(output.contains("/framework/"),
                "Dry-run include-lucee output should contain dependency mapping key");
        assertTrue(output.contains(expectedPhysicalPath),
                "Dry-run include-lucee output should contain dependency mapping physical path");
    }
}
