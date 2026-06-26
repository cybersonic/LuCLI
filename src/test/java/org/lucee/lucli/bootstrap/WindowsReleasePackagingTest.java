package org.lucee.lucli.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class WindowsReleasePackagingTest {

    private static final Path RELEASE_WORKFLOW_PATH = Paths.get(".github", "workflows", "release.yml");
    private static final Path CI_WORKFLOW_PATH = Paths.get(".github", "workflows", "ci.yml");
    private static final Path INSTALLER_PATH = Paths.get("content", "assets", "install.ps1");

    @Test
    void releaseWorkflowBuildsWindowsExecutableInsteadOfConcatenatedBatchPayload() throws IOException {
        String workflow = Files.readString(RELEASE_WORKFLOW_PATH, StandardCharsets.UTF_8);

        assertTrue(workflow.contains("mvn -DskipTests=true clean package -Pwindows-exe"),
                "Release workflow should build Launch4j Windows executable artifacts via the windows-exe profile");
        assertTrue(workflow.contains("zip -j target/lucli-windows.zip target/lucli.exe"),
                "Release workflow should package lucli.exe into the Windows zip artifact");
        assertTrue(workflow.contains("cp target/lucli.exe \"target/lucli-$VERSION.exe\""),
                "Release workflow should publish versioned Windows executable artifacts");
        assertFalse(workflow.contains("cat src/bin/lucli.bat target/lucli.jar > target/lucli.bat"),
                "Release workflow should not concatenate lucli.bat and lucli.jar");
        assertFalse(workflow.contains("zip -j target/lucli-windows.zip target/lucli.bat"),
                "Release workflow should not package batch payload artifacts as the Windows distribution");
    }

    @Test
    void powershellInstallerPrefersExeAndKeepsLegacyBatchFallback() throws IOException {
        String installer = Files.readString(INSTALLER_PATH, StandardCharsets.UTF_8);

        assertTrue(installer.contains("$asset = \"lucli-$versionOnly.exe\""),
                "Installer should request Windows executable assets by default");
        assertTrue(installer.contains("$targetFileName = \"lucli.exe\""),
                "Installer should install executable launcher names for new releases");
        assertTrue(installer.contains("$asset = \"lucli-$versionOnly.bat\""),
                "Installer should retain legacy batch fallback for older release tags");
    }

    @Test
    void ciWorkflowRunsWindowsExecutableSmokeCheck() throws IOException {
        String workflow = Files.readString(CI_WORKFLOW_PATH, StandardCharsets.UTF_8);

        assertTrue(workflow.contains("mvn package -DskipTests -Pwindows-exe"),
                "CI should build the Launch4j executable on Windows runners");
        assertTrue(workflow.contains(".\\target\\lucli.exe --version"),
                "CI should smoke test the generated Windows executable");
    }
}
