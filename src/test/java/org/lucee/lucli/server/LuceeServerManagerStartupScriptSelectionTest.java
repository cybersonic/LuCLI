package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceeServerManagerStartupScriptSelectionTest {

    @TempDir
    Path tempDir;

    @Test
    void isWindowsOsName_detectsWindowsVariants() {
        assertTrue(LuceeServerManager.isWindowsOsName("Windows 11"));
        assertTrue(LuceeServerManager.isWindowsOsName("windows"));
        assertFalse(LuceeServerManager.isWindowsOsName("Mac OS X"));
        assertFalse(LuceeServerManager.isWindowsOsName("Linux"));
    }

    @Test
    void tomcatLaunchScriptName_selectsExpectedScriptPerOsAndMode() {
        assertEquals("startup.sh", LuceeServerManager.tomcatLaunchScriptName(false, false));
        assertEquals("catalina.sh", LuceeServerManager.tomcatLaunchScriptName(true, false));
        assertEquals("startup.bat", LuceeServerManager.tomcatLaunchScriptName(false, true));
        assertEquals("catalina.bat", LuceeServerManager.tomcatLaunchScriptName(true, true));
    }

    @Test
    void resolveTomcatLaunchScript_backgroundUnix_prefersBinStartupScript() throws Exception {
        Path catalinaHome = tempDir.resolve("unix-home");
        Path binDir = catalinaHome.resolve("bin");
        Files.createDirectories(binDir);
        Path binScript = binDir.resolve("startup.sh");
        Files.writeString(binScript, "#!/bin/sh\n");
        Files.writeString(catalinaHome.resolve("startup.sh"), "#!/bin/sh\n");

        Path resolved = LuceeServerManager.resolveTomcatLaunchScript(catalinaHome, false, false);
        assertEquals(binScript, resolved);
    }

    @Test
    void resolveTomcatLaunchScript_backgroundUnix_fallsBackToRuntimeRootForExpress() throws Exception {
        Path catalinaHome = tempDir.resolve("express-unix-home");
        Files.createDirectories(catalinaHome.resolve("bin"));
        Path rootScript = catalinaHome.resolve("startup.sh");
        Files.writeString(rootScript, "#!/bin/sh\n");

        Path resolved = LuceeServerManager.resolveTomcatLaunchScript(catalinaHome, false, false);
        assertEquals(rootScript, resolved);
    }

    @Test
    void resolveTomcatLaunchScript_backgroundWindows_fallsBackToRuntimeRootForExpress() throws Exception {
        Path catalinaHome = tempDir.resolve("express-win-home");
        Files.createDirectories(catalinaHome.resolve("bin"));
        Path rootScript = catalinaHome.resolve("startup.bat");
        Files.writeString(rootScript, "@echo off\r\n");

        Path resolved = LuceeServerManager.resolveTomcatLaunchScript(catalinaHome, false, true);
        assertEquals(rootScript, resolved);
    }

    @Test
    void resolveTomcatLaunchScript_foregroundWindows_usesBinCatalinaBat() throws Exception {
        Path catalinaHome = tempDir.resolve("tomcat-win-home");
        Path binDir = catalinaHome.resolve("bin");
        Files.createDirectories(binDir);
        Path catalinaBat = binDir.resolve("catalina.bat");
        Files.writeString(catalinaBat, "@echo off\r\n");

        Path resolved = LuceeServerManager.resolveTomcatLaunchScript(catalinaHome, true, true);
        assertEquals(catalinaBat, resolved);
    }

    @Test
    void buildTomcatLaunchCommand_windowsBackground_usesCmdWrapper() {
        Path scriptPath = Path.of("C:/lucee/bin/startup.bat");

        List<String> command = LuceeServerManager.buildTomcatLaunchCommand(scriptPath, false, true);
        assertEquals(List.of("cmd", "/c", scriptPath.toString()), command);
    }

    @Test
    void buildTomcatLaunchCommand_windowsForeground_usesCmdWrapperWithRun() {
        Path scriptPath = Path.of("C:/lucee/bin/catalina.bat");

        List<String> command = LuceeServerManager.buildTomcatLaunchCommand(scriptPath, true, true);
        assertEquals(List.of("cmd", "/c", scriptPath.toString(), "run"), command);
    }

    @Test
    void buildTomcatLaunchCommand_unixForeground_executesScriptDirectlyWithRun() {
        Path scriptPath = Path.of("/opt/lucee/bin/catalina.sh");

        List<String> command = LuceeServerManager.buildTomcatLaunchCommand(scriptPath, true, false);
        assertEquals(List.of(scriptPath.toString(), "run"), command);
    }
}
