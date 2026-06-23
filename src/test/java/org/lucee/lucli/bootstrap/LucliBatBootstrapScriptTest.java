package org.lucee.lucli.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class LucliBatBootstrapScriptTest {

    private static final Path LUCLI_BAT_PATH = Paths.get("src", "bin", "lucli.bat");

    @Test
    void windowsWrapperSuppressesUnsafeWarningOnJava24PlusWithoutOverridingUserFlags() throws IOException {
        String script = Files.readString(LUCLI_BAT_PATH, StandardCharsets.UTF_8);

        assertTrue(script.contains("if %JAVA_VERSION_MAJOR_NUM% GEQ 24 ("),
                "lucli.bat should gate Unsafe warning suppression to Java 24+");
        assertTrue(script.contains("LUCLI_UNSAFE_MEMORY_ACCESS_ARG=--sun-misc-unsafe-memory-access=allow"),
                "lucli.bat should add the allow mode to suppress terminal deprecation warning noise");
        assertTrue(script.contains("if defined LUCLI_JAVA_ARGS ("),
                "lucli.bat should respect explicit user flags in LUCLI_JAVA_ARGS");
        assertTrue(script.contains("if defined JDK_JAVA_OPTIONS ("),
                "lucli.bat should respect explicit user flags in JDK_JAVA_OPTIONS");
        assertTrue(script.contains("if defined JAVA_TOOL_OPTIONS ("),
                "lucli.bat should respect explicit user flags in JAVA_TOOL_OPTIONS");
    }
}
