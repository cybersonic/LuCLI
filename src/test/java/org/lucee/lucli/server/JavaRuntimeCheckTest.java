package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link JavaRuntimeCheck}.
 *
 * <p>The tests exercise the pure {@link JavaRuntimeCheck#findJavaRuntimeError}
 * helper rather than {@code verifyOrExit()}, so we don't terminate the JVM
 * during unit runs.
 */
class JavaRuntimeCheckTest {

    private static final String LINUX = "Linux";
    private static final String WINDOWS = "Windows 10";

    @Test
    void returnsErrorWhenJavaHomeUnsetAndJreHomeUnset() {
        String err = JavaRuntimeCheck.findJavaRuntimeError(null, null, LINUX);
        assertNotNull(err, "Expected error when both JAVA_HOME and JRE_HOME are unset");
        assertTrue(err.contains("JAVA_HOME is not set"),
                "Error should explicitly name JAVA_HOME: " + err);
        assertTrue(err.contains("Java 21 or newer"),
                "Error should mention Java 21 minimum: " + err);
        assertTrue(err.contains(JavaRuntimeCheck.ADOPTIUM_URL),
                "Error should point at adoptium.net: " + err);
        assertTrue(err.contains("export JAVA_HOME="),
                "Error should show the export command pattern: " + err);
    }

    @Test
    void returnsErrorWhenJavaHomeIsEmptyString() {
        String err = JavaRuntimeCheck.findJavaRuntimeError("   ", "", LINUX);
        assertNotNull(err);
        assertTrue(err.contains("JAVA_HOME is not set"));
    }

    @Test
    void returnsBrokenPathErrorWhenJavaHomePointsNowhere() {
        String err = JavaRuntimeCheck.findJavaRuntimeError(
                "/definitely/not/a/real/jdk", null, LINUX);
        assertNotNull(err);
        assertTrue(err.contains("JAVA_HOME points at"),
                "Error should call out JAVA_HOME explicitly: " + err);
        assertTrue(err.contains("/definitely/not/a/real/jdk"),
                "Error should echo the broken path so the user knows what to fix: " + err);
        assertTrue(err.contains("bin/java"),
                "Error should explain what bin/ entry was missing: " + err);
        assertTrue(err.contains(JavaRuntimeCheck.ADOPTIUM_URL));
    }

    @Test
    void returnsBrokenPathErrorForJreHomeWhenJavaHomeUnset() {
        String err = JavaRuntimeCheck.findJavaRuntimeError(
                null, "/also/not/real", LINUX);
        assertNotNull(err);
        assertTrue(err.contains("JRE_HOME points at"), err);
        assertTrue(err.contains("/also/not/real"), err);
    }

    @Test
    void expectsJavaExeOnWindows(@TempDir Path tmp) throws IOException {
        // Simulate a "JDK" on Windows that has bin/java (Unix) but not bin/java.exe
        Path jdkBin = Files.createDirectories(tmp.resolve("bin"));
        Files.createFile(jdkBin.resolve("java"));

        String err = JavaRuntimeCheck.findJavaRuntimeError(
                tmp.toString(), null, WINDOWS);
        assertNotNull(err, "Windows should reject a JAVA_HOME without java.exe");
        assertTrue(err.contains("bin/java.exe"),
                "Error should mention java.exe on Windows: " + err);
    }

    @Test
    void returnsNullWhenJavaHomePointsAtValidJdk(@TempDir Path tmp) throws IOException {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path javaBin = bin.resolve("java");
        Files.createFile(javaBin);
        // Best-effort executable bit; filesystem may not support it (Windows)
        try {
            Files.setPosixFilePermissions(javaBin, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem; the check also accepts Files.exists as a fallback
        }

        String err = JavaRuntimeCheck.findJavaRuntimeError(
                tmp.toString(), null, LINUX);
        assertNull(err, "Valid JAVA_HOME should produce no error, got: " + err);
    }

    @Test
    void returnsNullWhenJreHomePointsAtValidJreAndJavaHomeUnset(@TempDir Path tmp) throws IOException {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Files.createFile(bin.resolve("java"));

        String err = JavaRuntimeCheck.findJavaRuntimeError(
                null, tmp.toString(), LINUX);
        assertNull(err, "Valid JRE_HOME should satisfy the preflight when JAVA_HOME is unset");
    }

    @Test
    void prefersValidJreHomeWhenJavaHomeIsBroken(@TempDir Path tmp) throws IOException {
        // JAVA_HOME is broken, but JRE_HOME is valid → catalina.sh will use JRE_HOME.
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Files.createFile(bin.resolve("java"));

        String err = JavaRuntimeCheck.findJavaRuntimeError(
                "/broken/jdk", tmp.toString(), LINUX);
        assertNull(err, "Valid JRE_HOME should rescue a broken JAVA_HOME");
    }

    @Test
    void findJavaRuntimeErrorInMapResolvesFromChildEnv(@TempDir Path tmp) throws IOException {
        // Regression: JAVA_HOME may be absent from the parent shell but
        // present in the effective child-process env (built from .env +
        // lucee.json envVars). The map-based preflight must honor that
        // source so we don't false-positive and block a startup that
        // would otherwise succeed.
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Files.createFile(bin.resolve("java"));

        Map<String, String> childEnv = new HashMap<>();
        childEnv.put("JAVA_HOME", tmp.toString());

        assertNull(JavaRuntimeCheck.findJavaRuntimeErrorInMap(childEnv, LINUX),
                "JAVA_HOME from env map should satisfy the preflight");
    }

    @Test
    void findJavaRuntimeErrorInMapReportsMissingWhenMapHasNothing() {
        assertNotNull(JavaRuntimeCheck.findJavaRuntimeErrorInMap(new HashMap<>(), LINUX));
    }

    @Test
    void findJavaRuntimeErrorInMapAllowsJavaFromPathWhenHomesUnset(@TempDir Path tmp) throws IOException {
        String osName = System.getProperty("os.name", "");
        boolean isWindows = osName.toLowerCase().contains("win");
        String javaBinary = isWindows ? "java.exe" : "java";
        Path javaOnPathDir = Files.createDirectories(tmp.resolve("bin-on-path"));
        Files.createFile(javaOnPathDir.resolve(javaBinary));

        Map<String, String> childEnv = new HashMap<>();
        childEnv.put("PATH", javaOnPathDir.toString());

        assertNull(JavaRuntimeCheck.findJavaRuntimeErrorInMap(childEnv, osName),
                "java on PATH should satisfy runtime preflight when JAVA_HOME/JRE_HOME are unset");
    }

    @Test
    void findJavaRuntimeErrorInMapFailsWhenHomesUnsetAndPathHasNoJava(@TempDir Path tmp) throws IOException {
        String osName = System.getProperty("os.name", "");
        Path emptyPathDir = Files.createDirectories(tmp.resolve("empty-path"));

        Map<String, String> childEnv = new HashMap<>();
        childEnv.put("PATH", emptyPathDir.toString());

        assertNotNull(JavaRuntimeCheck.findJavaRuntimeErrorInMap(childEnv, osName),
                "Missing JAVA_HOME/JRE_HOME with no java on PATH should still error");
    }

    @Test
    void errorMessageUsesActionableFormat() {
        // Smoke-test that the exact format hasn't regressed on users who may
        // have scripted around the error (e.g. grep in CI logs).
        String err = JavaRuntimeCheck.findJavaRuntimeError(null, null, LINUX);
        assertTrue(err.startsWith("❌ Error:"),
                "Error should start with the LuCLI error emoji/prefix: " + err);
        assertFalse(err.contains("port conflicts"),
                "Error should not mention the misleading port-conflict message: " + err);
    }
}
