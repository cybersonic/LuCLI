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
    void falseHelpArgumentsDoNotHijackMcpToolCalls() throws Exception {
        List<String> requests = new ArrayList<>();
        requests.add("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}");
        String[] arguments = {"{\"help\":false}", "{\"h\":false}",
            "{\"help\":\"false\"}", "{\"h\":0}", "{\"help\":false,\"h\":false}"};
        for (int i = 0; i < arguments.length; i++) {
            requests.add("{\"jsonrpc\":\"2.0\",\"id\":" + (i + 1)
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":" + arguments[i] + "}}");
        }
        List<JsonNode> responses = runMcpSession(requests);
        for (int i = 1; i <= arguments.length; i++) {
            final int id = i;
            JsonNode response = responses.stream().filter(n -> n.path("id").asInt() == id)
                .findFirst().orElseThrow();
            JsonNode result = response.path("result");
            assertFalse(result.path("isError").asBoolean(), response.toString());
            assertTrue(result.path("content").get(0).path("text").asText().contains("hello from echo"), response.toString());
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
    void toolsListUsesModuleDeclaredInputSchema() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        ));

        JsonNode listResp = responses.stream()
                .filter(n -> n.has("id") && n.get("id").asInt() == 2)
                .findFirst()
                .orElseThrow();

        JsonNode tools = listResp.get("result").get("tools");

        JsonNode greet = null;
        JsonNode echo = null;
        List<String> names = new ArrayList<>();
        for (JsonNode t : tools) {
            String n = t.get("name").asText().toLowerCase();
            names.add(n);
            if (n.equals("greet")) greet = t;
            if (n.equals("echo")) echo = t;
        }

        // The registry function itself must never surface as a tool.
        assertFalse(names.contains("mcptoolspecs"),
                "mcpToolSpecs must not be advertised as a tool. Got: " + names);

        // greet has a declared entry in mcpToolSpecs() — the declared schema
        // must win over the signature-derived one. The declared description
        // deliberately differs from the @subject doc hint so this assertion
        // can only pass via the declared path.
        assertNotNull(greet, "expected greet tool. Got: " + names);
        JsonNode schema = greet.get("inputSchema");
        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.get("properties").has("subject"),
                "declared schema must expose 'subject'. Got: " + schema);
        assertEquals("string",
                schema.get("properties").get("subject").get("type").asText());
        assertEquals("Greeting target (declared via mcpToolSpecs)",
                schema.get("properties").get("subject").get("description").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());

        // echo has no declared entry — it keeps the signature-derived schema
        // (no formal params → empty properties).
        assertNotNull(echo, "expected echo tool. Got: " + names);
        assertEquals(0, echo.get("inputSchema").get("properties").size(),
                "echo must fall back to the signature-derived schema");
    }

    @Test
    void failedToolPreservesReportAndRestoresCaptureForNextCall() throws Exception {
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"failreport\",\"arguments\":{}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"failreport\",\"arguments\":{\"printReport\":false}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{}}}"
        ));
        JsonNode failed = responses.stream().filter(n -> n.path("id").asInt() == 1)
                .findFirst().orElseThrow().path("result");
        assertTrue(failed.path("isError").asBoolean(), failed.toString());
        String text = failed.path("content").get(0).path("text").asText();
        assertTrue(text.contains("report: three failures"), text);
        assertTrue(text.contains("stderr: failure details"), text);
        assertTrue(text.contains("unicode output: café"), text);
        assertTrue(text.indexOf("fixture failure sentinel") > text.indexOf("unicode output: café"), text);

        JsonNode quiet = responses.stream().filter(n -> n.path("id").asInt() == 2)
                .findFirst().orElseThrow().path("result");
        assertTrue(quiet.path("isError").asBoolean());
        assertTrue(quiet.path("content").get(0).path("text").asText().contains("fixture failure sentinel"));
        assertFalse(quiet.path("content").get(0).path("text").asText().contains("report: three failures"));

        JsonNode success = responses.stream().filter(n -> n.path("id").asInt() == 3)
                .findFirst().orElseThrow().path("result");
        assertFalse(success.path("isError").asBoolean());
        assertTrue(success.path("content").get(0).path("text").asText().contains("hello from echo"));
        assertFalse(success.path("content").get(0).path("text").asText().contains("fixture failure sentinel"));
    }

    @Test
    void toolsCallCarriesARuntimeOwnedMcpMarker() throws Exception {
        String[] arguments = {
            "{}",
            "{\"__lucliMcpCall\":\"false\"}",
            "{\"__LUCLIMCPCALL\":false}",
            "{\"--__lucliMcpCall\":\"nope\"}"
        };
        List<String> requests = new ArrayList<>();
        requests.add("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}");
        for (int i = 0; i < arguments.length; i++) {
            requests.add("{\"jsonrpc\":\"2.0\",\"id\":" + (i + 1)
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"whocalled\",\"arguments\":" + arguments[i] + "}}");
        }
        List<JsonNode> responses = runMcpSession(requests);
        for (int i = 1; i <= arguments.length; i++) {
            final int id = i;
            JsonNode result = responses.stream().filter(n -> n.path("id").asInt() == id)
                .findFirst().orElseThrow(() -> new AssertionError("no response for id=" + id + ": " + responses))
                .path("result");
            assertFalse(result.path("isError").asBoolean(), result.toString());
            String text = result.path("content").get(0).path("text").asText();
            // Exactly one marker, set by the runtime: a client can neither forge nor suppress it.
            assertTrue(text.contains("mcpCall=true markerKeys=1"), "arguments " + arguments[id - 1] + ": " + text);
        }
    }

    @Test
    void terminalInvocationNeverCarriesTheMcpMarker() throws Exception {
        String[][] invocations = {
            {"whocalled"},
            {"whocalled", "__lucliMcpCall=true"},
            {"whocalled", "--__lucliMcpCall=true"},
            {"whocalled", "--__LUCLIMCPCALL"}
        };
        for (String[] invocation : invocations) {
            List<String> cmd = new ArrayList<>(List.of("/bin/bash", lucliBin, FIXTURE_MODULE_NAME));
            cmd.addAll(List.of(invocation));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor(120, TimeUnit.SECONDS);
            assertTrue(output.contains("mcpCall=absent markerKeys=0"), String.join(" ", invocation) + ": " + output);
        }
    }

    @Test
    void declaredMarkerParameterIsNeverBoundFromATerminalPositional() throws Exception {
        // The fixture declares `__LuCliMcpCall` (a case variant): a positional must not fill it.
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", lucliBin, FIXTURE_MODULE_NAME, "declaredmarker", "yes");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor(120, TimeUnit.SECONDS);
        assertTrue(output.contains("declared=absent"), output);
        assertFalse(output.contains("declared=yes"), output);

        // Over MCP the runtime still delivers it to the declared parameter.
        List<JsonNode> responses = runMcpSession(List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"declaredmarker\",\"arguments\":{\"__LuCliMcpCall\":\"false\"}}}"
        ));
        String text = responses.stream().filter(n -> n.path("id").asInt() == 1).findFirst().orElseThrow()
            .path("result").path("content").get(0).path("text").asText();
        assertTrue(text.contains("declared=true"), text);
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
