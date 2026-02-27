package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.TomcatConfigSupport;

/**
 * Runtime provider for the "jetty" runtime type.
 *
 * This provider uses an external Jetty installation (specified via runtime.jettyHome
 * or the JETTY_HOME environment variable) combined with the Lucee JAR downloaded
 * separately. It follows the JETTY_HOME / JETTY_BASE separation:
 *
 * 1. JETTY_HOME points to an existing, read-only Jetty distribution
 * 2. JETTY_BASE is created under ~/.lucli/servers/<name> for per-server configuration
 * 3. Downloads lucee-{version}.jar to JETTY_BASE/lib/ext/ (per-server)
 * 4. Generates start.d/*.ini, context XML, and web.xml in JETTY_BASE
 *
 * Configuration in lucee.json:
 * <pre>
 * {
 *   "runtime": {
 *     "type": "jetty",
 *     "jettyHome": "/path/to/jetty-home-12.x"
 *   }
 * }
 * </pre>
 */
public final class JettyRuntimeProvider implements RuntimeProvider {

    @Override
    public String getType() {
        return "jetty";
    }

    @Override
    public LuceeServerManager.ServerInstance start(
            LuceeServerManager manager,
            LuceeServerConfig.ServerConfig config,
            Path projectDir,
            String environment,
            LuceeServerManager.AgentOverrides agentOverrides,
            boolean foreground,
            boolean forceReplace
    ) throws Exception {
        LuceeServerConfig.RuntimeConfig rt = LuceeServerConfig.getEffectiveRuntime(config);

        System.out.println("Using runtime.type=\"jetty\"");

        // Validate jettyHome is specified
        Path jettyHome = resolveJettyHome(rt);
        if (jettyHome == null) {
            throw new IllegalStateException(
                    "runtime.jettyHome is required for jetty runtime.\n" +
                    "Please specify the path to your Jetty installation in lucee.json:\n" +
                    "  \"runtime\": {\n" +
                    "    \"type\": \"jetty\",\n" +
                    "    \"jettyHome\": \"/path/to/jetty-home\"\n" +
                    "  }\n" +
                    "Or set the JETTY_HOME environment variable.");
        }

        // Validate jettyHome exists and looks like a Jetty installation
        validateJettyInstallation(jettyHome);

        // Detect Jetty version and validate Lucee compatibility
        int jettyMajorVersion = detectJettyMajorVersion(jettyHome);
        String luceeVersion = LuceeServerConfig.getLuceeVersion(config);
        validateLuceeJettyCompatibility(luceeVersion, jettyMajorVersion);

        // Resolve port conflicts
        LuceeServerConfig.PortConflictResult portResult =
                LuceeServerConfig.resolvePortConflicts(config, false, manager);
        manager.checkAndReportPortConflicts(config, portResult);
        config = portResult.updatedConfig;

        // Display port details
        TomcatConfigSupport.displayPortDetails(config, foreground, "Jetty");

        // Ensure Lucee JAR is available (cached in ~/.lucli/jars/)
        String variant = LuceeServerConfig.getLuceeVariant(config);
        Path luceeJar = manager.ensureLuceeJar(luceeVersion, variant);

        // Create JETTY_BASE (server instance directory)
        Path jettyBase = manager.getServersDir().resolve(config.name);
        if (Files.exists(jettyBase) && forceReplace) {
            TomcatConfigSupport.deleteDirectoryRecursively(jettyBase);
        }
        Files.createDirectories(jettyBase);

        // Generate stop port and key for graceful shutdown
        int stopPort = LuceeServerConfig.getEffectiveShutdownPort(config);
        String stopKey = "lucli-" + UUID.randomUUID().toString().substring(0, 8);

        // Warn if URL rewriting is enabled (not supported for Jetty runtime)
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            System.out.println("\n\u26a0\ufe0f  URL Rewriting via RewriteValve is not supported with the Jetty runtime.");
            System.out.println("   Jetty uses its own RewriteHandler \u2014 manual configuration required.");
            System.out.println("   Skipping URL rewrite configuration.\n");
        }

        // Generate Jetty configuration for JETTY_BASE
        JettyBaseConfigGenerator configGenerator = new JettyBaseConfigGenerator();
        configGenerator.generateConfiguration(jettyBase, config, projectDir, jettyHome,
                jettyMajorVersion, stopPort, stopKey);

        // Deploy Lucee JAR to JETTY_BASE/lib/ext/
        configGenerator.deployLuceeJar(luceeJar, jettyBase, luceeVersion, variant);

        // Write CFConfig if present
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, jettyBase);

        // Deploy extension dependencies
        manager.deployExtensionsForServer(projectDir, jettyBase);

        // Launch the Jetty server process
        LuceeServerManager.ServerInstance instance = manager.launchJettyProcess(
                jettyHome, jettyBase, config, projectDir,
                agentOverrides, environment, foreground,
                stopPort, stopKey);

        // For background mode: wait for startup and open browser
        if (!foreground && instance != null) {
            manager.waitForServerStartup(instance, 30);
            manager.openBrowserForServer(instance, config);
        }

        return instance;
    }

    /**
     * Resolve jettyHome from RuntimeConfig, supporting environment variables.
     */
    private Path resolveJettyHome(LuceeServerConfig.RuntimeConfig rt) {
        if (rt.jettyHome != null && !rt.jettyHome.trim().isEmpty()) {
            return Paths.get(rt.jettyHome);
        }
        // Check for JETTY_HOME environment variable as fallback
        String envJettyHome = System.getenv("JETTY_HOME");
        if (envJettyHome != null && !envJettyHome.trim().isEmpty()) {
            return Paths.get(envJettyHome);
        }
        return null;
    }

    /**
     * Validate that the given path looks like a Jetty installation.
     */
    private void validateJettyInstallation(Path jettyHome) throws IllegalStateException {
        if (!Files.exists(jettyHome)) {
            throw new IllegalStateException(
                    "JETTY_HOME does not exist: " + jettyHome + "\n" +
                    "Please ensure runtime.jettyHome points to a valid Jetty installation.");
        }

        // Check for start.jar — the essential Jetty launch mechanism
        Path startJar = jettyHome.resolve("start.jar");
        if (!Files.exists(startJar)) {
            throw new IllegalStateException(
                    "start.jar not found in JETTY_HOME: " + startJar + "\n" +
                    "This doesn't appear to be a valid Jetty installation.");
        }

        // Check for lib/ directory
        Path libDir = jettyHome.resolve("lib");
        if (!Files.isDirectory(libDir)) {
            throw new IllegalStateException(
                    "JETTY_HOME/lib not found: " + libDir + "\n" +
                    "This doesn't appear to be a valid Jetty installation.");
        }

        // Check for modules/ directory
        Path modulesDir = jettyHome.resolve("modules");
        if (!Files.isDirectory(modulesDir)) {
            throw new IllegalStateException(
                    "JETTY_HOME/modules not found: " + modulesDir + "\n" +
                    "This doesn't appear to be a valid Jetty installation.");
        }
    }

    /**
     * Detect the major version of Jetty by running start.jar --version.
     *
     * @return The major version number (e.g., 11, 12), or 0 if detection fails
     */
    private int detectJettyMajorVersion(Path jettyHome) {
        Path startJar = jettyHome.resolve("start.jar");

        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", startJar.toString(), "--version");
            pb.environment().put("JETTY_HOME", jettyHome.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("⚠️  Warning: Could not detect Jetty version (exit code " + exitCode + ")");
                return 0;
            }

            // Parse version from output — look for patterns like:
            // "Jetty Server Classpath:" header followed by version info, or
            // "jetty-server-12.0.14.jar" in the classpath listing
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "jetty-server-(\\d+)\\.\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                int majorVersion = Integer.parseInt(matcher.group(1));
                System.out.println("Detected Jetty version: " + majorVersion + ".x");
                return majorVersion;
            }

            // Alternative pattern: "Jetty :: Server :: 12.x.y"
            java.util.regex.Pattern altPattern = java.util.regex.Pattern.compile(
                    "(?:Jetty|jetty)[^\\d]*(\\d+)\\.\\d+\\.\\d+");
            java.util.regex.Matcher altMatcher = altPattern.matcher(output);
            if (altMatcher.find()) {
                int majorVersion = Integer.parseInt(altMatcher.group(1));
                System.out.println("Detected Jetty version: " + majorVersion + ".x");
                return majorVersion;
            }

            System.out.println("⚠️  Warning: Could not parse Jetty version from output");
            return 0;
        } catch (Exception e) {
            System.out.println("⚠️  Warning: Could not detect Jetty version: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Validate that the Lucee version is compatible with the Jetty version.
     *
     * Jetty 12 uses jakarta.servlet (Jakarta EE 10), requiring Lucee 7.x.
     * Jetty 11 and below use javax.servlet (Java EE), requiring Lucee 5.x or 6.x.
     *
     * @throws IllegalStateException if versions are incompatible
     */
    private void validateLuceeJettyCompatibility(String luceeVersion, int jettyMajorVersion) {
        if (jettyMajorVersion == 0) {
            System.out.println("⚠️  Skipping Lucee/Jetty compatibility check (version unknown)");
            return;
        }

        int luceeMajorVersion = 0;
        if (luceeVersion != null && !luceeVersion.isEmpty()) {
            try {
                luceeMajorVersion = Integer.parseInt(luceeVersion.split("\\.")[0]);
            } catch (NumberFormatException e) {
                return;
            }
        }

        // Jetty 12+ requires Lucee 7.x (jakarta.servlet)
        if (jettyMajorVersion >= 12 && luceeMajorVersion < 7) {
            throw new IllegalStateException(
                    "❌ Lucee " + luceeVersion + " is not compatible with Jetty " + jettyMajorVersion + "\n\n" +
                    "Jetty 12+ uses jakarta.servlet (Jakarta EE), but Lucee " + luceeMajorVersion + ".x uses javax.servlet (Java EE).\n\n" +
                    "Solutions:\n" +
                    "  1. Use Lucee 7.x with Jetty " + jettyMajorVersion + " (recommended)\n" +
                    "     Update lucee.json: \"lucee\": { \"version\": \"7.0.1.100-RC\" }\n\n" +
                    "  2. Use Jetty 11.x with Lucee " + luceeVersion + "\n" +
                    "     Update lucee.json: \"runtime\": { \"jettyHome\": \"/path/to/jetty11\" }");
        }

        // Jetty 11 and below requires Lucee 5.x or 6.x (javax.servlet)
        if (jettyMajorVersion > 0 && jettyMajorVersion < 12 && luceeMajorVersion >= 7) {
            throw new IllegalStateException(
                    "❌ Lucee " + luceeVersion + " is not compatible with Jetty " + jettyMajorVersion + "\n\n" +
                    "Jetty 11 and below use javax.servlet (Java EE), but Lucee 7.x uses jakarta.servlet (Jakarta EE).\n\n" +
                    "Solutions:\n" +
                    "  1. Use Jetty 12+ with Lucee 7.x (recommended)\n" +
                    "     Update lucee.json: \"runtime\": { \"jettyHome\": \"/path/to/jetty12\" }\n\n" +
                    "  2. Use Lucee 6.x with Jetty " + jettyMajorVersion + "\n" +
                    "     Update lucee.json: \"lucee\": { \"version\": \"6.2.2.91\" }");
        }

        System.out.println("✓ Lucee " + luceeMajorVersion + ".x is compatible with Jetty " + jettyMajorVersion + ".x");
    }

    /**
     * Check whether a Jetty server can be stopped using STOP.PORT / STOP.KEY.
     * Returns true if the stop command was issued successfully.
     */
    public static boolean stopJettyServer(Path jettyHome, int stopPort, String stopKey) {
        if (jettyHome == null || !Files.exists(jettyHome.resolve("start.jar"))) {
            return false;
        }

        try {
            Path startJar = jettyHome.resolve("start.jar");
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", startJar.toString(),
                    "--stop",
                    "STOP.PORT=" + stopPort,
                    "STOP.KEY=" + stopKey);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
