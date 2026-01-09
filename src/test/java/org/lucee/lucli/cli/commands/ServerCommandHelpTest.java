package org.lucee.lucli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * Integration-style tests to verify that `--help` on server subcommands
 * prints usage and does NOT execute business logic.
 */
public class ServerCommandHelpTest {

    @Test
    void serverStartHelpPrintsUsageAndDoesNotError() {
ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);

        CommandLine cmd = new CommandLine(new ServerCommand());
        cmd.setOut(pw);
        cmd.setErr(pw);

        int exitCode = cmd.execute("start", "--help");
        String output = baos.toString();
        
        // TODO: Fix 'server already running' error - ensure server is stopped after test run

        // Picocli (plus our early-return) should prevent business logic from running
        assertEquals(0, exitCode, "--help on server start should exit with code 0");
        assertTrue(output.contains("Usage:"), "help output should contain a Usage: section");
        assertTrue(output.contains("Usage:.*server start"), "help output should mention 'server start' in the usage line");
        // Ensure we did not print the tart easter egg or the normal start banner
        assertTrue(!output.contains("also starts a server"), "Business logic (tart banner) should not run on --help");
    }

    @Test
    void serverRootHelpDoesNotError() {
ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);

        CommandLine cmd = new CommandLine(new ServerCommand());
        cmd.setOut(pw);
        cmd.setErr(pw);

        int exitCode = cmd.execute("--help");
        String output = baos.toString();

        assertEquals(0, exitCode, "--help on server root should exit with code 0");
        assertTrue(output.contains("Usage:"), "root help output should contain a Usage: section");
        assertTrue(output.contains("server"), "root help output should mention 'server'");
    }
}
