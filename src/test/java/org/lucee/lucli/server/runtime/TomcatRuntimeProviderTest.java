package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.server.LuceeServerConfig;

/**
 * Unit tests for TomcatRuntimeProvider.
 *
 * Tests the validation logic that prevents misconfigured servers from
 * starting: Tomcat installation checks, Lucee/Tomcat version
 * compatibility, and Lucee JAR deployment.
 *
 * Private methods are accessed via reflection to test them in isolation.
 */
public class TomcatRuntimeProviderTest {

    @TempDir
    Path tempDir;

    private TomcatRuntimeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TomcatRuntimeProvider();
    }

    // ── getType ──────────────────────────────────────────────────────────

    @Test
    void getType_returnsTomcat() {
        assertEquals("tomcat", provider.getType());
    }

    // ── validateTomcatInstallation ───────────────────────────────────────

    @Test
    void validateTomcatInstallation_acceptsValidInstallation() throws Exception {
        Path catalinaHome = createValidTomcatHome("valid-tomcat");

        // Should not throw
        assertDoesNotThrow(() -> invokeValidateTomcatInstallation(catalinaHome));
    }

    @Test
    void validateTomcatInstallation_rejectsNonexistentPath() {
        Path catalinaHome = tempDir.resolve("nonexistent");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateTomcatInstallation(catalinaHome));
        assertTrue(ex.getMessage().contains("does not exist"),
                "Error should mention path doesn't exist");
    }

    @Test
    void validateTomcatInstallation_rejectsMissingBinDir() throws Exception {
        Path catalinaHome = tempDir.resolve("no-bin");
        Files.createDirectories(catalinaHome.resolve("lib"));
        Files.createDirectories(catalinaHome.resolve("conf"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateTomcatInstallation(catalinaHome));
        assertTrue(ex.getMessage().contains("bin"),
                "Error should mention missing bin directory");
    }

    @Test
    void validateTomcatInstallation_rejectsMissingLibDir() throws Exception {
        Path catalinaHome = tempDir.resolve("no-lib");
        Files.createDirectories(catalinaHome.resolve("bin"));
        Files.createDirectories(catalinaHome.resolve("conf"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateTomcatInstallation(catalinaHome));
        assertTrue(ex.getMessage().contains("lib"),
                "Error should mention missing lib directory");
    }

    @Test
    void validateTomcatInstallation_rejectsMissingStartupScript() throws Exception {
        Path catalinaHome = tempDir.resolve("no-script");
        Files.createDirectories(catalinaHome.resolve("bin"));
        Files.createDirectories(catalinaHome.resolve("lib"));
        Files.createDirectories(catalinaHome.resolve("conf"));
        // bin/ exists but catalina.sh is missing

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateTomcatInstallation(catalinaHome));
        assertTrue(ex.getMessage().contains("startup script") || ex.getMessage().contains("catalina"),
                "Error should mention missing startup script");
    }

    // ── validateLuceeTomcatCompatibility ─────────────────────────────────

    @Test
    void compatibility_lucee6OnTomcat9_isValid() {
        // Lucee 6.x + Tomcat 9 = OK (both use javax.servlet)
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("6.2.2.91", 9));
    }

    @Test
    void compatibility_lucee7OnTomcat10_isValid() {
        // Lucee 7.x + Tomcat 10 = OK (both use jakarta.servlet)
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("7.0.1.100-RC", 10));
    }

    @Test
    void compatibility_lucee7OnTomcat11_isValid() {
        // Lucee 7.x + Tomcat 11 = OK
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("7.0.0.242-RC", 11));
    }

    @Test
    void compatibility_lucee5OnTomcat9_isValid() {
        // Lucee 5.x + Tomcat 9 = OK (both use javax.servlet)
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("5.4.6.9", 9));
    }

    @Test
    void compatibility_lucee6OnTomcat10_isInvalid() {
        // Lucee 6.x + Tomcat 10 = INCOMPATIBLE (javax vs jakarta)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateCompatibility("6.2.2.91", 10));
        assertTrue(ex.getMessage().contains("not compatible"),
                "Should explain incompatibility");
        assertTrue(ex.getMessage().contains("jakarta"),
                "Should mention jakarta.servlet");
    }

    @Test
    void compatibility_lucee6OnTomcat11_isInvalid() {
        // Lucee 6.x + Tomcat 11 = INCOMPATIBLE
        assertThrows(IllegalStateException.class, () ->
                invokeValidateCompatibility("6.2.2.91", 11));
    }

    @Test
    void compatibility_lucee7OnTomcat9_isInvalid() {
        // Lucee 7.x + Tomcat 9 = INCOMPATIBLE (jakarta vs javax)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                invokeValidateCompatibility("7.0.1.100-RC", 9));
        assertTrue(ex.getMessage().contains("not compatible"),
                "Should explain incompatibility");
    }

    @Test
    void compatibility_unknownTomcatVersion_skipsValidation() {
        // tomcatMajorVersion=0 means detection failed — should not throw
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("6.2.2.91", 0));
    }

    @Test
    void compatibility_nullLuceeVersion_failsSafeWithTomcat10() {
        // Null Lucee version defaults to major version 0, which is < 7,
        // so it correctly fails against Tomcat 10+ (fail-safe behavior)
        assertThrows(IllegalStateException.class, () ->
                invokeValidateCompatibility(null, 10));
    }

    @Test
    void compatibility_emptyLuceeVersion_failsSafeWithTomcat10() {
        // Empty string can't be parsed, so version defaults to 0 (< 7)
        // and correctly fails against Tomcat 10+ (fail-safe)
        assertThrows(IllegalStateException.class, () ->
                invokeValidateCompatibility("", 10));
    }

    @Test
    void compatibility_nullLuceeVersion_passesWithTomcat9() {
        // Null version with Tomcat 9 — version 0 is < 7, which is fine for javax.servlet
        assertDoesNotThrow(() ->
                invokeValidateCompatibility(null, 9));
    }

    @Test
    void compatibility_emptyLuceeVersion_passesWithTomcat9() {
        assertDoesNotThrow(() ->
                invokeValidateCompatibility("", 9));
    }

    // ── resolveCatalinaHome ─────────────────────────────────────────────

    @Test
    void resolveCatalinaHome_returnsConfiguredPath() throws Exception {
        LuceeServerConfig.RuntimeConfig rt = new LuceeServerConfig.RuntimeConfig();
        rt.catalinaHome = "/opt/tomcat";

        Path result = invokeResolveCatalinaHome(rt);

        assertEquals(Path.of("/opt/tomcat"), result);
    }

    @Test
    void resolveCatalinaHome_returnsNullWhenNotConfigured() throws Exception {
        LuceeServerConfig.RuntimeConfig rt = new LuceeServerConfig.RuntimeConfig();
        rt.catalinaHome = null;

        // When no env var CATALINA_HOME is set, should return null
        // (We can't control env vars in tests, so we just verify it doesn't throw)
        // If CATALINA_HOME is set in the test env, it returns that; otherwise null
        Path result = invokeResolveCatalinaHome(rt);
        // Accept either null or a path from env — both are valid behaviors
        if (System.getenv("CATALINA_HOME") == null) {
            assertNull(result, "Should return null when catalinaHome is not configured");
        }
    }

    @Test
    void resolveCatalinaHome_returnsNullForEmptyString() throws Exception {
        LuceeServerConfig.RuntimeConfig rt = new LuceeServerConfig.RuntimeConfig();
        rt.catalinaHome = "   ";

        Path result = invokeResolveCatalinaHome(rt);
        if (System.getenv("CATALINA_HOME") == null) {
            assertNull(result, "Should return null for blank catalinaHome");
        }
    }

    // ── deployLuceeJarToServerInstance ───────────────────────────────────

    @Test
    void deployLuceeJar_copiesJarToLib() throws Exception {
        Path sourceJar = tempDir.resolve("lucee-6.2.2.91.jar");
        Files.writeString(sourceJar, "fake-jar-content");
        Path serverDir = tempDir.resolve("server-instance");
        Files.createDirectories(serverDir);

        invokeDeployLuceeJar(sourceJar, serverDir, "6.2.2.91", "standard");

        Path expectedJar = serverDir.resolve("lib/lucee-6.2.2.91.jar");
        assertTrue(Files.exists(expectedJar), "Lucee JAR should be deployed to lib/");
    }

    @Test
    void deployLuceeJar_skipsWhenAlreadyDeployed() throws Exception {
        Path sourceJar = tempDir.resolve("lucee-6.2.2.91.jar");
        Files.writeString(sourceJar, "fake-jar-content");
        Path serverDir = tempDir.resolve("server-instance-2");
        Files.createDirectories(serverDir.resolve("lib"));

        // Pre-create the target JAR
        Path existingJar = serverDir.resolve("lib/lucee-6.2.2.91.jar");
        Files.writeString(existingJar, "already-deployed");

        invokeDeployLuceeJar(sourceJar, serverDir, "6.2.2.91", "standard");

        // Content should NOT be overwritten
        String content = Files.readString(existingJar);
        assertEquals("already-deployed", content,
                "Existing JAR should not be overwritten");
    }

    @Test
    void deployLuceeJar_usesVariantInFilename() throws Exception {
        Path sourceJar = tempDir.resolve("lucee-light-7.0.0.jar");
        Files.writeString(sourceJar, "light-jar");
        Path serverDir = tempDir.resolve("server-instance-3");
        Files.createDirectories(serverDir);

        invokeDeployLuceeJar(sourceJar, serverDir, "7.0.0", "light");

        Path expectedJar = serverDir.resolve("lib/lucee-light-7.0.0.jar");
        assertTrue(Files.exists(expectedJar),
                "Light variant should include variant in filename");
    }

    @Test
    void deployLuceeJar_createsLibDirectory() throws Exception {
        Path sourceJar = tempDir.resolve("lucee-6.0.0.jar");
        Files.writeString(sourceJar, "jar");
        Path serverDir = tempDir.resolve("server-instance-4");
        Files.createDirectories(serverDir);
        // lib/ does not exist yet

        invokeDeployLuceeJar(sourceJar, serverDir, "6.0.0", "standard");

        assertTrue(Files.isDirectory(serverDir.resolve("lib")),
                "lib directory should be created");
    }

    // ── Reflection helpers ──────────────────────────────────────────────

    private void invokeValidateTomcatInstallation(Path catalinaHome) throws Exception {
        Method method = TomcatRuntimeProvider.class.getDeclaredMethod(
                "validateTomcatInstallation", Path.class);
        method.setAccessible(true);
        try {
            method.invoke(provider, catalinaHome);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException ise) {
                throw ise;
            }
            throw e;
        }
    }

    private void invokeValidateCompatibility(String luceeVersion, int tomcatMajor) throws Exception {
        Method method = TomcatRuntimeProvider.class.getDeclaredMethod(
                "validateLuceeTomcatCompatibility", String.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(provider, luceeVersion, tomcatMajor);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException ise) {
                throw ise;
            }
            throw e;
        }
    }

    private Path invokeResolveCatalinaHome(LuceeServerConfig.RuntimeConfig rt) throws Exception {
        Method method = TomcatRuntimeProvider.class.getDeclaredMethod(
                "resolveCatalinaHome", LuceeServerConfig.RuntimeConfig.class);
        method.setAccessible(true);
        try {
            return (Path) method.invoke(provider, rt);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private void invokeDeployLuceeJar(Path luceeJar, Path serverDir, String version, String variant) throws Exception {
        Method method = TomcatRuntimeProvider.class.getDeclaredMethod(
                "deployLuceeJarToServerInstance", Path.class, Path.class, String.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(provider, luceeJar, serverDir, version, variant);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /**
     * Create a minimal valid Tomcat installation for validation tests.
     */
    private Path createValidTomcatHome(String name) throws IOException {
        Path home = tempDir.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib"));
        Files.createDirectories(home.resolve("conf"));

        // Create the startup script based on OS
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String scriptName = isWindows ? "catalina.bat" : "catalina.sh";
        Files.writeString(home.resolve("bin/" + scriptName), "#!/bin/sh\necho ok");

        return home;
    }
}
