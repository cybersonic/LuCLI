package org.lucee.lucli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** REPL history follows the active LuCLI home (upstream cybersonic/LuCLI#140). */
class ReplCommandHistoryTest {

    @TempDir
    Path tempDir;

    @Test
    void historyFileIsInTheLucliHome() {
        String previous = System.getProperty("lucli.home");
        System.setProperty("lucli.home", tempDir.toString());
        try {
            assertEquals(tempDir.resolve("repl_history"), ReplCommand.historyFile());
        } finally {
            if (previous == null) System.clearProperty("lucli.home"); else System.setProperty("lucli.home", previous);
        }
    }
}
