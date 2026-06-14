package org.lucee.lucli.cli.commands;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lucee.lucli.paths.LucliPaths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link McpCommand}.
 *
 * Copies a small fixture module (src/test/resources/mcp-fixture-module) into
 * the real LuCLI modules directory, spawns `dev-lucli.sh mcp mcpfixture` as a
 * subprocess, and asserts on the JSON-RPC responses emitted on stdout.
 *
 * The fixture is removed in {@link #tearDown()}.
 *
 * <p>These tests require a Unix-like shell environment (bash) to invoke
 * {@code dev-lucli.sh}. They are skipped on Windows — macOS and Ubuntu CI
 * provide platform coverage.
 */
class McpCommandTest {

    private static final String FIXTURE_MODULE_NAME = "mcpfixture";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path fixtureInstalledPath;
    private static String lucliBin;

    @BeforeAll
    static void setUp() throws Exception {
        // Subprocess-based tests need bash + dev-lucli.sh. Windows runners
        // lack /bin/bash and ProcessBuilder doesn't resolve bash.exe via
        // PATHEXT, so skip on Windows — macOS + Ubuntu CI cover the behavior.
        Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase().contains("win"),
                "McpCommandTest uses bash subprocess — skipped on Windows");

        Path fixtureSrc = Path.of("src/test/resources/mcp-fixture-module")
                .toAbsolutePath();
        assertTrue(Files.isDirectory(fixtureSrc),
                "fixture source not found at " + fixtureSrc);

        Path modulesDir = LucliPaths.resolve().modulesDir();
        Files.createDirectories(modulesDir);
        fixtureInstalledPath = modulesDir.resolve(FIXTURE_MODULE_NAME);

        if (Files.exists(fixtureInstalledPath)) {
            deleteRecursively(fixtureInstalledPath);
        }
        copyDirectory(fixtureSrc, fixtureInstalledPath);

        File devBin = new File("dev-lucli.sh");
        assertTrue(devBin.exists(),
                "dev-lucli.sh must be run from LuCLI repo root");
        lucliBin = devBin.getAbsolutePath();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (fixtureInstalledPath != null && Files.exists(fixtureInstalledPath)) {
            deleteRecursively(fixtureInstalledPath);
        }
    }

    @Test
    void toolCallCapturesOutStreamIntoResponseBody() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{}}}"
        ));

        JsonNode callResp = responses.stream()
                .filter(n -> n.has("id") && n.get("id").asInt() == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no response for id=2. Responses: " + responses));

        assertTrue(callResp.has("result"), "expected result, got: " + callResp);
        JsonNode content = callResp.get("result").get("content");
        assertNotNull(content);
        assertEquals(1, content.size());

        String text = content.get(0).get("text").asText();
        assertTrue(text.contains("hello from echo"),
                "expected captured output in response, got: '" + text + "'");
    }

    @Test
    void toolsListExcludesBaseModuleInternals() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        ));

        JsonNode listResp = responses.stream()
                .filter(n -> n.has("id") && n.get("id").asInt() == 2)
                .findFirst()
                .orElseThrow();

        // Lucee lowercases function names in metadata — compare lowercase
        List<String> names = new ArrayList<>();
        listResp.get("result").get("tools").forEach(
                t -> names.add(t.get("name").asText().toLowerCase()));

        // BaseModule helpers must NOT leak into the tool list
        List<String> forbidden = List.of(
                "init", "out", "err", "getenv", "verbose",
                "getsecret", "getabsolutepath", "executecommand",
                "version", "showhelp", "mcphiddentools"
        );
        for (String banned : forbidden) {
            assertFalse(names.contains(banned),
                    "tool '" + banned + "' must be hidden from MCP. Got: " + names);
        }

        // Fixture tools must remain
        assertTrue(names.contains("echo"), "expected echo. Got: " + names);
        assertTrue(names.contains("boom"), "expected boom. Got: " + names);
        assertTrue(names.contains("greet"), "expected greet. Got: " + names);
    }

    @Test
    void toolsListHonorsMcpHiddenToolsDeclaration() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        ));

        JsonNode listResp = responses.stream()
                .filter(n -> n.has("id") && n.get("id").asInt() == 2)
                .findFirst()
                .orElseThrow();

        List<String> names = new ArrayList<>();
        listResp.get("result").get("tools").forEach(
                t -> names.add(t.get("name").asText().toLowerCase()));

        // Fixture module declares mcpHiddenTools() returns ["secret"]
        assertFalse(names.contains("secret"),
                "tool 'secret' should be hidden via mcpHiddenTools(). Got: " + names);

        // Other fixture tools remain visible
        assertTrue(names.contains("echo"), "expected echo. Got: " + names);
        assertTrue(names.contains("greet"), "expected greet. Got: " + names);
    }

    @Test
    void toolsListStripsHintPrefixFromDescriptions() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        ));

        JsonNode listResp = responses.stream()
                .filter(n -> n.has("id") && n.get("id").asInt() == 2)
                .findFirst()
                .orElseThrow();

        JsonNode echo = null;
        for (JsonNode t : listResp.get("result").get("tools")) {
            if (t.get("name").asText().equalsIgnoreCase("echo")) echo = t;
        }
        assertNotNull(echo, "expected echo tool");

        // The fixture documents echo as `/** hint: Emit a known string ... */`.
        // Lucee surfaces the metadata hint with the literal "hint:" key prefix;
        // tools/list descriptions must have that prefix stripped.
        String description = echo.get("description").asText();
        assertFalse(description.toLowerCase().startsWith("hint:"),
                "tool description must not retain the 'hint:' key prefix, got: '" + description + "'");
        assertTrue(description.startsWith("Emit a known string"),
                "expected the clean hint text, got: '" + description + "'");
    }

    // Send the given JSON-RPC lines to `dev-lucli.sh mcp mcpfixture` as a
    // subprocess. Returns each parsed response as a JsonNode.
    private List<JsonNode> runMcpSession(List<String> requests) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "/bin/bash", lucliBin, "mcp", FIXTURE_MODULE_NAME);
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        try (BufferedWriter stdin = new BufferedWriter(
                new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8))) {
            for (String req : requests) {
                stdin.write(req);
                stdin.newLine();
                stdin.flush();
            }
        }

        List<JsonNode> responses = new ArrayList<>();
        try (BufferedReader stdout = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    responses.add(MAPPER.readTree(line));
                } catch (Exception e) {
                    // Non-JSON line (maven progress, etc.) — skip
                }
            }
        }
        proc.waitFor(120, TimeUnit.SECONDS);
        return responses;
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> {
                try {
                    Path target = dest.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); }
                      catch (IOException e) { throw new RuntimeException(e); }
                  });
        }
    }

}
