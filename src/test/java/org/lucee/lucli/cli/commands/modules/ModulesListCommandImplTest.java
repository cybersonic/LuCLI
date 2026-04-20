package org.lucee.lucli.cli.commands.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.LuCLI;
import org.lucee.lucli.StringOutput;
import org.lucee.lucli.profile.CliProfile;
import org.lucee.lucli.profile.DefaultProfile;
import org.lucee.lucli.profile.WheelsProfile;

/**
 * Regression tests: {@link ModulesListCommandImpl} must honour the active
 * {@link CliProfile} when deciding which modules directory to list, instead
 * of hard-coding {@code ~/.lucli/modules}.
 */
class ModulesListCommandImplTest {

    @TempDir
    Path fakeHome;

    private CliProfile savedProfile;
    private String savedLucliHome;
    private PrintStream savedOut;
    private PrintStream savedStringOutputStream;

    @BeforeEach
    void setUp() {
        savedProfile = LuCLI.getActiveProfile();
        // Point lucli.home at a temp directory so test runs don't touch real $HOME.
        savedLucliHome = System.getProperty("lucli.home");
        System.clearProperty("lucli.home");
        savedOut = System.out;
        // StringOutput is a singleton that caches System.out at construction,
        // so redirecting System.out alone won't capture table output.
        savedStringOutputStream = StringOutput.getInstance().getOutputStream();
    }

    @AfterEach
    void tearDown() {
        LuCLI.setActiveProfile(savedProfile);
        if (savedLucliHome != null) {
            System.setProperty("lucli.home", savedLucliHome);
        } else {
            System.clearProperty("lucli.home");
        }
        System.setOut(savedOut);
        StringOutput.getInstance().setOutputStream(savedStringOutputStream);
    }

    @Test
    void listsModulesFromDefaultProfileHome() throws Exception {
        LuCLI.setActiveProfile(new DefaultProfile());
        Path home = fakeHome.resolve(".lucli");
        Path modulesDir = home.resolve("modules");
        Files.createDirectories(modulesDir.resolve("installed-default"));
        System.setProperty("lucli.home", home.toString());

        String output = runListAndCapture();

        // Heading reflects default branding and reports the correct directory
        assertTrue(output.contains("LuCLI Modules"),
            "expected default profile heading, got:\n" + output);
        assertTrue(output.contains(modulesDir.toString()),
            "expected modules directory '" + modulesDir + "' in output, got:\n" + output);
        assertTrue(output.contains("installed-default"),
            "expected installed module to be listed, got:\n" + output);
    }

    @Test
    void listsModulesFromWheelsProfileHome() throws Exception {
        LuCLI.setActiveProfile(new WheelsProfile());
        Path home = fakeHome.resolve(".wheels");
        Path modulesDir = home.resolve("modules");
        Files.createDirectories(modulesDir.resolve("installed-wheels"));
        System.setProperty("lucli.home", home.toString());

        String output = runListAndCapture();

        // Heading reflects Wheels branding (regression: previously always "LuCLI Modules")
        assertTrue(output.contains("Wheels Modules"),
            "expected Wheels profile heading, got:\n" + output);
        // Regression: previously always reported ~/.lucli/modules
        assertTrue(output.contains(modulesDir.toString()),
            "expected wheels modules directory '" + modulesDir + "' in output, got:\n" + output);
        assertTrue(output.contains("installed-wheels"),
            "expected installed module to be listed, got:\n" + output);
        // Negative assertion: the wrong (default-profile) directory should not appear
        assertFalse(output.contains(fakeHome.resolve(".lucli").resolve("modules").toString()),
            "unexpected default-profile modules directory in output:\n" + output);
    }

    private String runListAndCapture() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream captured = new PrintStream(buf, true, "UTF-8");
        // Capture both: direct System.out writes AND StringOutput-routed writes.
        System.setOut(captured);
        StringOutput.getInstance().setOutputStream(captured);
        int exitCode = new ModulesListCommandImpl().call();
        captured.flush();
        String output = buf.toString("UTF-8");
        assertTrue(exitCode == 0, "expected exit 0, got " + exitCode + "; output:\n" + output);
        return output;
    }
}
