package org.lucee.lucli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LucliScriptLucliPrefixTest {

    @TempDir
    Path tempDir;

    private Map<String, String> originalScriptEnvironment;

    @BeforeEach
    void setup() {
        originalScriptEnvironment = new HashMap<>(LuCLI.scriptEnvironment);
        LuCLI.scriptEnvironment = new HashMap<>(System.getenv());
        LuCLI.currentEnvironment = null;
        LuCLI.envFilePath = null;
        LuCLI.clearRuntimeCwd();
    }

    @AfterEach
    void cleanup() {
        LuCLI.scriptEnvironment = new HashMap<>(originalScriptEnvironment);
        LuCLI.currentEnvironment = null;
        LuCLI.envFilePath = null;
        LuCLI.clearRuntimeCwd();
    }

    @Test
    void leadingLucliPrefixKeepsQuotedRunArgumentIntact() throws Exception {
        Path cfsFile = tempDir.resolve("arg_count.cfs");
        Path lucliScript = tempDir.resolve("script.lucli");

        Files.writeString(
            cfsFile,
            "if (arrayLen(__arguments) NEQ 1) {"
                + " throw(type=\"LuCLI.TestFailure\", message=\"Expected exactly one argument, got #arrayLen(__arguments)#\");"
                + "} "
                + "if (__arguments[1] NEQ \"Mark Drew\") {"
                + " throw(type=\"LuCLI.TestFailure\", message=\"Expected quoted argument to stay intact, got '#__arguments[1]#'\");"
                + "}",
            StandardCharsets.UTF_8
        );
        Files.write(
            lucliScript,
            List.of(
                "cd \"" + tempDir.toAbsolutePath() + "\"",
                "lucli run \"arg_count.cfs\" \"Mark Drew\""
            ),
            StandardCharsets.UTF_8
        );
        int exitCode = LuCLI.executeLucliScript(lucliScript.toString());
        assertEquals(0, exitCode);
    }
}
