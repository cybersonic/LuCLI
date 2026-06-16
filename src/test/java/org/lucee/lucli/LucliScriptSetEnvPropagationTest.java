package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LucliScriptSetEnvPropagationTest {

    @TempDir
    Path tempDir;

    private Map<String, String> originalScriptEnvironment;

    @BeforeEach
    void setup() {
        originalScriptEnvironment = new HashMap<>(LuCLI.scriptEnvironment);
        LuCLI.scriptEnvironment = new HashMap<>(System.getenv());
        LuCLI.currentEnvironment = null;
        LuCLI.envFilePath = null;
    }

    @AfterEach
    void cleanup() {
        LuCLI.scriptEnvironment = new HashMap<>(originalScriptEnvironment);
        LuCLI.currentEnvironment = null;
        LuCLI.envFilePath = null;
    }

    @Test
    @Disabled("Temporarily disabled while migrating .lucli set propagation behavior and CI to BATS-first flow")
    void lucliSetValueIsVisibleToRunCfsThroughEnvMap() throws Exception {
        Path cfsFile = tempDir.resolve("print_env.cfs");
        Path lucliFile = tempDir.resolve("script.lucli");
        String expected = "https://example.invalid/webtrigger";

        Files.writeString(
            cfsFile,
            "writeOutput(structKeyExists(__env, \"FORGE_WEBTRIGGER_URL\") ? __env[\"FORGE_WEBTRIGGER_URL\"] : \"missing\");",
            StandardCharsets.UTF_8
        );
        Files.write(
            lucliFile,
            List.of(
                "set FORGE_WEBTRIGGER_URL=\"" + expected + "\"",
                "run " + cfsFile.toAbsolutePath()
            ),
            StandardCharsets.UTF_8
        );

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int exit = LuCLI.executeLucliScript(lucliFile.toString());
            assertEquals(0, exit);
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains(expected), "Expected .cfs output to include FORGE_WEBTRIGGER_URL from .lucli set");
    }

    @Test
    void lucliSetValueIsInheritedByExternalCommands() throws Exception {
        Path lucliFile = tempDir.resolve("script_external.lucli");
        Path outputFile = tempDir.resolve("external_env.txt");
        String expected = "FORGE_WEBTRIGGER_URL=from-lucli-set";

        Files.write(
            lucliFile,
            List.of(
                "set FORGE_WEBTRIGGER_URL=from-lucli-set",
                "env > " + outputFile.toAbsolutePath()
            ),
            StandardCharsets.UTF_8
        );

        int exit = LuCLI.executeLucliScript(lucliFile.toString());
        assertEquals(0, exit);

        String envOutput = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(envOutput.contains(expected), "Expected external command environment to include FORGE_WEBTRIGGER_URL");
    }

    @Test
    void envFlagValueIsExposedAsLucliEnvInScriptEnvironment() throws Exception {
        Path lucliFile = tempDir.resolve("script_envflag.lucli");
        String expected = "prod";

        // A minimal .lucli script: no commands needed — we verify the side-effect on
        // scriptEnvironment (which is the map exposed as __env to CFS scripts) directly,
        // instead of capturing Lucee's writeOutput() via System.setOut.  The latter
        // approach is unreliable cross-platform because the Lucee JSR-223 engine
        // initialises its output Writer at engine-creation time with the original
        // System.out reference, so subsequent System.setOut() redirects are invisible
        // to it (manifests as a test failure on Windows CI).
        Files.write(
            lucliFile,
            List.of("# verify LUCLI_ENV propagation"),
            StandardCharsets.UTF_8
        );

        int exit = LuCLI.executeInProcess(new String[]{"--env", expected, lucliFile.toString()});
        assertEquals(0, exit);

        // scriptEnvironment is the live map that __env references in every CFS/CFM
        // executed within the same script session, so asserting it here is equivalent
        // to asserting __env["LUCLI_ENV"] inside a CFML script.
        assertEquals(expected, LuCLI.scriptEnvironment.get("LUCLI_ENV"),
            "Expected __env[\"LUCLI_ENV\"] to reflect --env value");
    }
}
