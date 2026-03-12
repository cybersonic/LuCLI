package org.lucee.lucli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;

class SystemCommandTest {

    @TempDir
    Path tempDir;

    private String originalLucliHome;

    @BeforeEach
    void setUp() {
        originalLucliHome = System.getProperty("lucli.home");
        System.setProperty("lucli.home", tempDir.resolve("lucli-home").toString());
    }

    @AfterEach
    void tearDown() {
        if (originalLucliHome == null) {
            System.clearProperty("lucli.home");
        } else {
            System.setProperty("lucli.home", originalLucliHome);
        }
    }

    @Test
    void pathsJsonIncludesResolvedHome() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new SystemCommand.PathsCommand()).execute("--json");
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(out.toString(StandardCharsets.UTF_8), Map.class);
        assertEquals(tempDir.resolve("lucli-home").toAbsolutePath().normalize().toString(), parsed.get("home"));
        assertEquals("system-property", parsed.get("homeSource"));
    }

    @Test
    void systemDefaultCommandShowsHelp() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new SystemCommand()).execute();
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Usage:"));
        assertTrue(output.contains("inspect"));
    }

    @Test
    void systemInspectLuceePrettyPrintsCfConfig() throws Exception {
        Path cfConfigPath = tempDir.resolve("lucli-home/lucee-server/lucee-server/context/.CFConfig.json");
        Files.createDirectories(cfConfigPath.getParent());
        Files.writeString(
            cfConfigPath,
            "{\"admin\":{\"password\":\"secret\"},\"datasources\":[{\"name\":\"primary\"}]}"
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new SystemCommand()).execute("inspect", "--lucee");
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.contains("\n"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(output);
        assertEquals("secret", json.path("admin").path("password").asText());
        assertEquals("primary", json.path("datasources").get(0).path("name").asText());
    }

    @Test
    void systemInspectLuceeSupportsCustomPath() throws Exception {
        Path customCfConfig = tempDir.resolve("custom/.CFConfig.json");
        Files.createDirectories(customCfConfig.getParent());
        Files.writeString(customCfConfig, "{\"mode\":\"custom\"}");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new SystemCommand()).execute(
                "inspect",
                "--lucee",
                "--path",
                customCfConfig.toString()
            );
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(out.toString(StandardCharsets.UTF_8));
        assertEquals("custom", json.path("mode").asText());
    }

    @Test
    void cleanDryRunLeavesCachesUntouched() throws Exception {
        Path expressDir = tempDir.resolve("lucli-home/express");
        Files.createDirectories(expressDir);
        Path cacheFile = expressDir.resolve("cache.zip");
        Files.writeString(cacheFile, "cache");

        int exitCode = new CommandLine(new SystemCommand.CleanCommand()).execute("--caches");
        assertEquals(0, exitCode);
        assertTrue(Files.exists(cacheFile));
    }

    @Test
    void backupCreateVerifyAndRestoreFlow() throws Exception {
        Path home = tempDir.resolve("lucli-home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("settings.json"), "true");

        int createExit = new CommandLine(new SystemCommand.BackupCommand.CreateCommand()).execute("--name", "test-backup", "--progress");
        assertEquals(0, createExit);

        Path archive = tempDir.resolve(".lucli_backups/test-backup.zip");
        assertTrue(Files.exists(archive));

        int verifyExit = new CommandLine(new SystemCommand.BackupCommand.VerifyCommand()).execute("test-backup");
        assertEquals(0, verifyExit);

        Path restoreTarget = tempDir.resolve("restored-home");
        int restoreDryRun = new CommandLine(new SystemCommand.BackupCommand.RestoreCommand()).execute(
            "test-backup",
            "--to",
            restoreTarget.toString()
        );
        assertEquals(0, restoreDryRun);
        assertFalse(Files.exists(restoreTarget.resolve("settings.json")));

        int restoreApply = new CommandLine(new SystemCommand.BackupCommand.RestoreCommand()).execute(
            "test-backup",
            "--to",
            restoreTarget.toString(),
            "--force"
        );
        assertEquals(0, restoreApply);
        assertTrue(Files.exists(restoreTarget.resolve("settings.json")));
    }

    @Test
    void backupPruneDryRunAndForce() throws Exception {
        Path home = tempDir.resolve("lucli-home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("settings.json"), "true");

        int createExit = new CommandLine(new SystemCommand.BackupCommand.CreateCommand()).execute("--name", "prune-me");
        assertEquals(0, createExit);

        Path archive = tempDir.resolve(".lucli_backups/prune-me.zip");
        assertTrue(Files.exists(archive));

        int dryRunExit = new CommandLine(new SystemCommand.BackupCommand.PruneCommand()).execute(
            "--older-than",
            "0s",
            "--keep",
            "0"
        );
        assertEquals(0, dryRunExit);
        assertTrue(Files.exists(archive));

        int forceExit = new CommandLine(new SystemCommand.BackupCommand.PruneCommand()).execute(
            "--older-than",
            "0s",
            "--keep",
            "0",
            "--force"
        );
        assertEquals(0, forceExit);
        assertFalse(Files.exists(archive));
    }
}
