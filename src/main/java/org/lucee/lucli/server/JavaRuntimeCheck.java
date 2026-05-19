package org.lucee.lucli.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Preflight check for the Java runtime used by the child server process.
 *
 * <p>The server is launched via Tomcat's {@code catalina.sh}/{@code startup.sh}
 * (or {@code .bat} on Windows), which requires either {@code JAVA_HOME} or
 * {@code JRE_HOME} to be set. When neither is set (or one is set to a broken
 * path), the child process exits silently before binding its port and the
 * parent reports a misleading "port conflicts detected" error.
 *
 * <p>This class runs BEFORE {@link ProcessBuilder#start()} so the user sees an
 * actionable error pointing at Adoptium instead of having to hunt through
 * {@code ~/.wheels/servers/&lt;name&gt;/server.err} to find the real cause.
 *
 * <p>Note: the LuCLI parent process itself may be running from an embedded JRE,
 * so {@link System#getProperty(String) java.home} is not a reliable signal for
 * what the child process will see. We check the effective child-process
 * environment (the populated {@link ProcessBuilder#environment()} map, which
 * merges parent shell + {@code .env} + {@code lucee.json} {@code envVars}),
 * not {@link System#getenv()}, so project-level overrides are honored.
 */
public final class JavaRuntimeCheck {

    public static final String ADOPTIUM_URL = "https://adoptium.net";

    private JavaRuntimeCheck() { /* static helper */ }

    /**
     * Verify that a Java runtime is reachable for the child server process,
     * using the effective environment the child will see (parent shell +
     * {@code .env} file + {@code lucee.json} {@code envVars}). Prints an
     * actionable error and terminates the JVM with exit code 1 on failure.
     * Returns normally when the runtime is reachable.
     *
     * <p>Callers must pass the populated {@link ProcessBuilder#environment()}
     * map — not {@link System#getenv()} — so project-level overrides of
     * {@code JAVA_HOME}/{@code JRE_HOME} are honored.
     */
    public static void verifyOrExit(Map<String, String> childEnv) {
        String error = findJavaRuntimeErrorInMap(childEnv, System.getProperty("os.name", ""));
        if (error != null) {
            System.err.println(error);
            System.exit(1);
        }
    }

    /**
     * Map-based variant of {@link #findJavaRuntimeError}. Exposed for testing.
     */
    public static String findJavaRuntimeErrorInMap(Map<String, String> env, String osName) {
        String javaHome = env != null ? env.get("JAVA_HOME") : null;
        String jreHome = env != null ? env.get("JRE_HOME") : null;
        String error = findJavaRuntimeError(javaHome, jreHome, osName);
        if (error == null) {
            return null;
        }

        // Tomcat startup scripts also accept a runtime discoverable from PATH
        // when JAVA_HOME/JRE_HOME are unset.
        if ((javaHome == null || javaHome.trim().isEmpty())
                && (jreHome == null || jreHome.trim().isEmpty())) {
            String pathValue = env != null ? env.get("PATH") : null;
            if (isJavaAvailableOnPath(pathValue, osName)) {
                return null;
            }
        }

        return error;
    }

    /**
     * Pure function that returns an actionable error message when the Java
     * runtime is not reachable, or {@code null} when it is. Exposed for
     * testing so we can exercise the detection logic without calling
     * {@link System#exit(int)}.
     *
     * @param javaHome value of the {@code JAVA_HOME} environment variable (may be null/empty)
     * @param jreHome  value of the {@code JRE_HOME} environment variable (may be null/empty)
     * @param osName   value of the {@code os.name} system property (used to pick {@code java.exe} on Windows)
     * @return error message to print, or {@code null} if the runtime is reachable
     */
    public static String findJavaRuntimeError(String javaHome, String jreHome, String osName) {
        boolean isWindows = osName != null && osName.toLowerCase().contains("win");
        String javaBinary = isWindows ? "java.exe" : "java";

        boolean javaHomeSet = javaHome != null && !javaHome.trim().isEmpty();
        boolean jreHomeSet = jreHome != null && !jreHome.trim().isEmpty();

        // If either is set, at least one must point at a valid JDK/JRE.
        if (javaHomeSet && isValidJavaHome(javaHome, javaBinary)) {
            return null;
        }
        if (jreHomeSet && isValidJavaHome(jreHome, javaBinary)) {
            return null;
        }

        // Broken-path case: JAVA_HOME (or JRE_HOME) is set but points nowhere useful.
        if (javaHomeSet) {
            return brokenPathMessage("JAVA_HOME", javaHome, javaBinary);
        }
        if (jreHomeSet) {
            return brokenPathMessage("JRE_HOME", jreHome, javaBinary);
        }

        // Unset case.
        return unsetMessage();
    }

    private static boolean isValidJavaHome(String home, String javaBinary) {
        try {
            Path javaExe = Paths.get(home, "bin", javaBinary);
            return Files.isExecutable(javaExe) || Files.exists(javaExe);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isJavaAvailableOnPath(String pathValue, String osName) {
        if (pathValue == null || pathValue.trim().isEmpty()) {
            return false;
        }
        boolean isWindows = osName != null && osName.toLowerCase().contains("win");
        String pathSeparator = isWindows ? ";" : ":";
        String javaBinary = isWindows ? "java.exe" : "java";
        for (String rawEntry : pathValue.split(Pattern.quote(pathSeparator))) {
            if (rawEntry == null) {
                continue;
            }
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                Path candidate = Paths.get(entry, javaBinary);
                if (Files.isExecutable(candidate) || Files.exists(candidate)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed PATH entries
            }
        }
        return false;
    }

    private static String unsetMessage() {
        return "❌ Error: JAVA_HOME is not set.\n"
                + "Wheels needs Java 21 or newer to start the server. Install from " + ADOPTIUM_URL + "\n"
                + "Then export JAVA_HOME in your shell profile, e.g.:\n"
                + "  export JAVA_HOME=/path/to/jdk-21";
    }

    private static String brokenPathMessage(String varName, String value, String javaBinary) {
        return "❌ Error: " + varName + " points at '" + value + "' but bin/" + javaBinary
                + " is missing or not executable.\n"
                + "Install Java 21+ from " + ADOPTIUM_URL + " or point " + varName
                + " at a valid JDK.";
    }
}
