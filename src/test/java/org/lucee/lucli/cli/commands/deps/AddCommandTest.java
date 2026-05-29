package org.lucee.lucli.cli.commands.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.deps.DependencyShortcutRegistry;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

class AddCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shortcutAddWritesProductionDependencyByDefault() throws Exception {
        Path projectDir = createProject();
        AddCommand command = new AddCommand(projectDir, DependencyShortcutRegistry.loadDefault());

        int exitCode = new CommandLine(command).execute("fw1");
        assertEquals(0, exitCode);

        JsonNode root = MAPPER.readTree(projectDir.resolve("lucee.json").toFile());
        JsonNode dep = root.path("dependencies").path("fw1");

        assertTrue(dep.isObject());
        assertEquals("forgebox", dep.path("source").asText());
        assertEquals("dependencies/fw1", dep.path("installPath").asText());
        assertEquals("/fw1", dep.path("mapping").asText());
        assertFalse(root.path("devDependencies").has("fw1"));
    }

    @Test
    void shortcutAddWithDevWritesDevDependency() throws Exception {
        Path projectDir = createProject();
        AddCommand command = new AddCommand(projectDir, DependencyShortcutRegistry.loadDefault());

        int exitCode = new CommandLine(command).execute("testbox", "--dev");
        assertEquals(0, exitCode);

        JsonNode root = MAPPER.readTree(projectDir.resolve("lucee.json").toFile());
        JsonNode dep = root.path("devDependencies").path("testbox");

        assertTrue(dep.isObject());
        assertEquals("forgebox", dep.path("source").asText());
        assertEquals("dependencies/testbox", dep.path("installPath").asText());
        assertEquals("/testbox", dep.path("mapping").asText());
        assertFalse(root.path("dependencies").has("testbox"));
    }

    @Test
    void unknownShortcutReturnsErrorAndDoesNotModifyDependencies() throws Exception {
        Path projectDir = createProject();
        AddCommand command = new AddCommand(projectDir, DependencyShortcutRegistry.loadDefault());

        int exitCode = new CommandLine(command).execute("not-a-shortcut");
        assertEquals(1, exitCode);

        JsonNode root = MAPPER.readTree(projectDir.resolve("lucee.json").toFile());
        assertFalse(root.has("dependencies"));
        assertFalse(root.has("devDependencies"));
    }

    private Path createProject() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("lucee.json"), """
            {
              "name": "shortcut-test-project"
            }
            """);
        return projectDir;
    }
}
