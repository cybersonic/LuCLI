package org.lucee.lucli.server.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Runtime provider for the default Lucee Express runtime. This preserves the
 * existing behaviour of LuceeServerManager prior to introducing the runtime
 * abstraction while living in a dedicated runtime package.
 */
public final class LuceeExpressRuntimeProvider implements RuntimeProvider {

    @Override
    public String getType() {
        return "lucee-express";
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

        // Ensure Lucee Express is available for the requested version
        Path luceeExpressDir = manager.ensureLuceeExpress(config.version);

        // Resolve port conflicts right before starting server to avoid race conditions
        LuceeServerConfig.PortConflictResult portResult =
                LuceeServerConfig.resolvePortConflicts(config, false, manager);

        manager.checkAndReportPortConflicts(config, portResult);

        // Build port information message (unchanged behaviour)
        displayPortDetails(config, foreground);

        // Create server instance directory
        Path serverInstanceDir = manager.getServersDir().resolve(config.name);
        Files.createDirectories(serverInstanceDir);

        // Generate Lucee Express-backed Tomcat configuration
        LuceeExpressConfigGenerator configGenerator = new LuceeExpressConfigGenerator();
        // When forceReplace is true (e.g. --force), also allow overwriting project-level
        // WEB-INF config files (web.xml, urlrewrite.xml, UrlRewriteFilter JAR). Without
        // --force, we preserve any existing project configuration files.
        boolean overwriteProjectConfig = forceReplace;
        configGenerator.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir, overwriteProjectConfig);

        // Write CFConfig (.CFConfig.json) into the Lucee context if configured in lucee.json.
        // This treats lucee.json as the source of truth and .CFConfig.json as a derived file.
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, serverInstanceDir);

        // Deploy extension dependencies to lucee-server/deploy folder (always deploy)
        manager.deployExtensionsForServer(projectDir, serverInstanceDir);

        // Launch the server process
        LuceeServerManager.ServerInstance instance = manager.launchServerProcess(
                serverInstanceDir,
                config,
                projectDir,
                luceeExpressDir,
                agentOverrides,
                environment,
                foreground
        );

        // For background mode only: wait for startup and open browser
        if (!foreground) {
            // Wait for server to start
            manager.waitForServerStartup(instance, 30); // 30 second timeout

            // Open the browser if enabled
            manager.openBrowserForServer(instance, config);
        }

        return instance;
    }

    /**
     * Local copy of the port details formatter so that runtime-specific
     * messaging lives with the provider.
     */
    private void displayPortDetails(LuceeServerConfig.ServerConfig config, boolean foreground) {
        StringBuilder portInfo = new StringBuilder();
        if (foreground) {
            portInfo.append("Running server '\"").append(config.name).append("\' in foreground mode:");
        } else {
            portInfo.append("Starting server '\"").append(config.name).append("\' on:");
        }
        portInfo.append("\n  HTTP port:     ").append(config.port);
        portInfo.append("\n  Shutdown port: ").append(LuceeServerConfig.getEffectiveShutdownPort(config));
        if (config.monitoring != null && config.monitoring.enabled && config.monitoring.jmx != null) {
            portInfo.append("\n  JMX port:      ").append(config.monitoring.jmx.port);
        }
        if (LuceeServerConfig.isHttpsEnabled(config)) {
            portInfo.append("\n  HTTPS port:    ").append(LuceeServerConfig.getEffectiveHttpsPort(config));
            portInfo.append("\n  HTTPS redirect:")
                    .append(LuceeServerConfig.isHttpsRedirectEnabled(config) ? " enabled" : " disabled");
        }
        if (foreground) {
            portInfo.append("\n\nPress Ctrl+C to stop the server\n");
        }

        System.out.println(portInfo.toString());
    }
}
