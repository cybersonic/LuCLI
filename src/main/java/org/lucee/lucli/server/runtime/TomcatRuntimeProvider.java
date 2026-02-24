package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.TomcatConfigSupport;

/**
 * Runtime provider for the "tomcat" runtime type.
 *
 * This provider uses an external Tomcat installation (specified via runtime.catalinaHome)
 * combined with the Lucee JAR downloaded separately. Unlike Lucee Express, which bundles
 * Tomcat and Lucee together, this provider:
 *
 * 1. Uses CATALINA_HOME pointing to an existing Tomcat installation
 * 2. Creates CATALINA_BASE under ~/.lucli/servers/<name> for per-server configuration
 * 3. Downloads lucee-{version}.jar to CATALINA_HOME/lib (shared across servers)
 * 4. Generates server.xml, context.xml, and web.xml in CATALINA_BASE
 */
public final class TomcatRuntimeProvider implements RuntimeProvider {

    @Override
    public String getType() {
        return "tomcat";
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

        System.out.println("Using runtime.type=\"tomcat\"");

        // Validate catalinaHome is specified
        Path catalinaHome = resolveCatalinaHome(rt);
        if (catalinaHome == null) {
            throw new IllegalStateException(
                    "runtime.catalinaHome is required for tomcat runtime.\n" +
                    "Please specify the path to your Tomcat installation in lucee.json:\n" +
                    "  \"runtime\": {\n" +
                    "    \"type\": \"tomcat\",\n" +
                    "    \"catalinaHome\": \"/path/to/tomcat\"\n" +
                    "  }");
        }

        // Validate catalinaHome exists and looks like a Tomcat installation
        validateTomcatInstallation(catalinaHome);

        // Detect Tomcat version and validate Lucee compatibility
        int tomcatMajorVersion = detectTomcatMajorVersion(catalinaHome);
        validateLuceeTomcatCompatibility(config.version, tomcatMajorVersion);

        // Resolve port conflicts
        LuceeServerConfig.PortConflictResult portResult =
                LuceeServerConfig.resolvePortConflicts(config, false, manager);
        manager.checkAndReportPortConflicts(config, portResult);
        config = portResult.updatedConfig;

        // Display port details
        TomcatConfigSupport.displayPortDetails(config, foreground, "external Tomcat");

        // Ensure Lucee JAR is available (cached in ~/.lucli/jars/)
        String variant = (rt.variant != null && !rt.variant.isEmpty()) ? rt.variant : "standard";
        Path luceeJar = manager.ensureLuceeJar(config.version, variant);

        // Create CATALINA_BASE (server instance directory)
        Path serverInstanceDir = manager.getServersDir().resolve(config.name);
        if (Files.exists(serverInstanceDir) && forceReplace) {
            TomcatConfigSupport.deleteDirectoryRecursively(serverInstanceDir);
        }
        Files.createDirectories(serverInstanceDir);

        // Deploy Lucee JAR to CATALINA_BASE/lib (not CATALINA_HOME - we don't touch external Tomcat)
        deployLuceeJarToServerInstance(luceeJar, serverInstanceDir, config.version, variant);

        // Generate Tomcat configuration for CATALINA_BASE
        CatalinaBaseConfigGenerator configGenerator = new CatalinaBaseConfigGenerator();
        boolean overwriteProjectConfig = forceReplace;
        configGenerator.generateConfiguration(serverInstanceDir, config, projectDir, catalinaHome, tomcatMajorVersion, overwriteProjectConfig);

        // Write CFConfig if present
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, serverInstanceDir);

        // Deploy extension dependencies
        manager.deployExtensionsForServer(projectDir, serverInstanceDir);

        // Launch the server process using unified launch method
        LuceeServerManager.ServerInstance instance = manager.launchTomcatProcess(
                catalinaHome, serverInstanceDir, config, projectDir,
                agentOverrides, environment, foreground, "tomcat");

        // For background mode: wait for startup and open browser
        if (!foreground && instance != null) {
            manager.waitForServerStartup(instance, 30);
            manager.openBrowserForServer(instance, config);
        }

        return instance;
    }

    /**
     * Resolve catalinaHome from RuntimeConfig, supporting environment variables.
     */
    private Path resolveCatalinaHome(LuceeServerConfig.RuntimeConfig rt) {
        if (rt.catalinaHome == null || rt.catalinaHome.trim().isEmpty()) {
            // Check for CATALINA_HOME environment variable as fallback
            String envCatalinaHome = System.getenv("CATALINA_HOME");
            if (envCatalinaHome != null && !envCatalinaHome.trim().isEmpty()) {
                return Paths.get(envCatalinaHome);
            }
            return null;
        }
        return Paths.get(rt.catalinaHome);
    }

    /**
     * Validate that the given path looks like a Tomcat installation.
     */
    private void validateTomcatInstallation(Path catalinaHome) throws IllegalStateException {
        if (!Files.exists(catalinaHome)) {
            throw new IllegalStateException(
                    "CATALINA_HOME does not exist: " + catalinaHome + "\n" +
                    "Please ensure runtime.catalinaHome points to a valid Tomcat installation.");
        }

        // Check for essential Tomcat directories/files
        Path binDir = catalinaHome.resolve("bin");
        Path libDir = catalinaHome.resolve("lib");
        Path confDir = catalinaHome.resolve("conf");

        if (!Files.isDirectory(binDir)) {
            throw new IllegalStateException(
                    "CATALINA_HOME/bin not found: " + binDir + "\n" +
                    "This doesn't appear to be a valid Tomcat installation.");
        }

        if (!Files.isDirectory(libDir)) {
            throw new IllegalStateException(
                    "CATALINA_HOME/lib not found: " + libDir + "\n" +
                    "This doesn't appear to be a valid Tomcat installation.");
        }

        // Check for catalina.sh or catalina.bat
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String catalinaScript = isWindows ? "catalina.bat" : "catalina.sh";
        Path scriptPath = binDir.resolve(catalinaScript);
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException(
                    "Tomcat startup script not found: " + scriptPath + "\n" +
                    "This doesn't appear to be a valid Tomcat installation.");
        }
    }

    /**
     * Detect the major version of Tomcat by running catalina.sh version.
     *
     * @return The major version number (e.g., 9, 10, 11), or 0 if detection fails
     */
    private int detectTomcatMajorVersion(Path catalinaHome) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String scriptName = isWindows ? "catalina.bat" : "catalina.sh";
        Path scriptPath = catalinaHome.resolve("bin").resolve(scriptName);

        try {
            ProcessBuilder pb = new ProcessBuilder(scriptPath.toString(), "version");
            pb.environment().put("CATALINA_HOME", catalinaHome.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("⚠️  Warning: Could not detect Tomcat version (exit code " + exitCode + ")");
                return 0;
            }

            // Parse version from output like "Server version: Apache Tomcat/11.0.2"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "Server version:\\s*Apache Tomcat/(\\d+)\\.\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                int majorVersion = Integer.parseInt(matcher.group(1));
                System.out.println("Detected Tomcat version: " + majorVersion + ".x");
                return majorVersion;
            }

            System.out.println("⚠️  Warning: Could not parse Tomcat version from output");
            return 0;
        } catch (Exception e) {
            System.out.println("⚠️  Warning: Could not detect Tomcat version: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Validate that the Lucee version is compatible with the Tomcat version.
     *
     * Tomcat 10+ uses jakarta.servlet (Jakarta EE 9+), requiring Lucee 7.x.
     * Tomcat 9 and below use javax.servlet (Java EE), requiring Lucee 5.x or 6.x.
     *
     * @throws IllegalStateException if versions are incompatible
     */
    private void validateLuceeTomcatCompatibility(String luceeVersion, int tomcatMajorVersion) {
        if (tomcatMajorVersion == 0) {
            // Could not detect version, skip validation but warn
            System.out.println("⚠️  Skipping Lucee/Tomcat compatibility check (version unknown)");
            return;
        }

        // Determine Lucee major version
        int luceeMajorVersion = 0;
        if (luceeVersion != null && !luceeVersion.isEmpty()) {
            try {
                luceeMajorVersion = Integer.parseInt(luceeVersion.split("\\.")[0]);
            } catch (NumberFormatException e) {
                // Can't parse, skip validation
                return;
            }
        }

        // Tomcat 10+ requires Lucee 7.x (jakarta.servlet)
        if (tomcatMajorVersion >= 10 && luceeMajorVersion < 7) {
            throw new IllegalStateException(
                    "❌ Lucee " + luceeVersion + " is not compatible with Tomcat " + tomcatMajorVersion + "\n\n" +
                    "Tomcat 10+ uses jakarta.servlet (Jakarta EE), but Lucee " + luceeMajorVersion + ".x uses javax.servlet (Java EE).\n\n" +
                    "Solutions:\n" +
                    "  1. Use Lucee 7.x with Tomcat " + tomcatMajorVersion + " (recommended)\n" +
                    "     Update lucee.json: \"version\": \"7.0.1.100-RC\"\n\n" +
                    "  2. Use Tomcat 9.x with Lucee " + luceeVersion + "\n" +
                    "     Update lucee.json: \"runtime\": { \"catalinaHome\": \"/path/to/tomcat9\" }");
        }

        // Tomcat 9 and below requires Lucee 5.x or 6.x (javax.servlet)
        if (tomcatMajorVersion > 0 && tomcatMajorVersion < 10 && luceeMajorVersion >= 7) {
            throw new IllegalStateException(
                    "❌ Lucee " + luceeVersion + " is not compatible with Tomcat " + tomcatMajorVersion + "\n\n" +
                    "Tomcat 9 and below use javax.servlet (Java EE), but Lucee 7.x uses jakarta.servlet (Jakarta EE).\n\n" +
                    "Solutions:\n" +
                    "  1. Use Tomcat 10+ with Lucee 7.x (recommended)\n" +
                    "     Update lucee.json: \"runtime\": { \"catalinaHome\": \"/path/to/tomcat10\" }\n\n" +
                    "  2. Use Lucee 6.x with Tomcat " + tomcatMajorVersion + "\n" +
                    "     Update lucee.json: \"version\": \"6.2.2.91\"");
        }

        System.out.println("✓ Lucee " + luceeMajorVersion + ".x is compatible with Tomcat " + tomcatMajorVersion + ".x");
    }

    /**
     * Deploy the Lucee JAR to the server instance's lib directory (CATALINA_BASE/lib).
     * This keeps CATALINA_HOME pristine and allows per-server Lucee versions.
     */
    private void deployLuceeJarToServerInstance(Path luceeJar, Path serverInstanceDir, String version, String variant)
            throws IOException {
        Path libDir = serverInstanceDir.resolve("lib");
        Files.createDirectories(libDir);

        String jarName = variant.equals("standard")
                ? "lucee-" + version + ".jar"
                : "lucee-" + variant + "-" + version + ".jar";
        Path targetJar = libDir.resolve(jarName);

        if (Files.exists(targetJar)) {
            System.out.println("Lucee JAR already deployed: " + targetJar);
            return;
        }

        // Check for other Lucee JAR versions in this server instance and warn
        try (var stream = Files.list(libDir)) {
            var existingLuceeJars = stream
                    .filter(p -> p.getFileName().toString().matches("lucee(-light|-zero)?-.*\\.jar"))
                    .toList();
            if (!existingLuceeJars.isEmpty()) {
                System.out.println("⚠️  Warning: Other Lucee JARs found in " + libDir + ":");
                for (Path jar : existingLuceeJars) {
                    System.out.println("    - " + jar.getFileName());
                }
                System.out.println("    Consider removing old versions to avoid conflicts.");
            }
        }

        System.out.println("Deploying Lucee JAR to server instance: " + targetJar);
        Files.copy(luceeJar, targetJar);
    }
}

