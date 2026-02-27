package org.lucee.lucli.server.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;
import org.lucee.lucli.server.TomcatConfigSupport;

/**
 * Runtime provider for the default Lucee Express runtime.
 *
 * The cached Lucee Express distribution ({@code ~/.lucli/express/{version}/})
 * is treated as a read-only CATALINA_HOME. A per-server CATALINA_BASE is
 * created under {@code ~/.lucli/servers/{name}/} with patched configuration,
 * using the same structure as the vendor-Tomcat provider.
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

        // Ensure Lucee Express is available — this is our read-only CATALINA_HOME
        Path catalinaHome = manager.ensureLuceeExpress(LuceeServerConfig.getLuceeVersion(config));

        // Resolve port conflicts right before starting server to avoid race conditions
        LuceeServerConfig.PortConflictResult portResult =
                LuceeServerConfig.resolvePortConflicts(config, false, manager);
        manager.checkAndReportPortConflicts(config, portResult);

        TomcatConfigSupport.displayPortDetails(config, foreground, null);

        // Create CATALINA_BASE (server instance directory)
        Path catalinaBase = manager.getServersDir().resolve(config.name);
        if (Files.exists(catalinaBase) && forceReplace) {
            TomcatConfigSupport.deleteDirectoryRecursively(catalinaBase);
        }
        Files.createDirectories(catalinaBase);

        // Generate CATALINA_BASE config from the Express CATALINA_HOME.
        // tomcatMajorVersion=0 because Express bundles an opaque Tomcat version.
        CatalinaBaseConfigGenerator configGenerator = new CatalinaBaseConfigGenerator();
        configGenerator.generateConfiguration(catalinaBase, config, projectDir, catalinaHome, 0, forceReplace);

        // Write CFConfig (.CFConfig.json) into the Lucee context if configured.
        LuceeServerConfig.writeCfConfigIfPresent(config, projectDir, catalinaBase);

        // Deploy extension dependencies to lucee-server/deploy folder
        manager.deployExtensionsForServer(projectDir, catalinaBase);

        // Launch using unified method (CATALINA_HOME != CATALINA_BASE)
        LuceeServerManager.ServerInstance instance = manager.launchTomcatProcess(
                catalinaHome, catalinaBase, config, projectDir,
                agentOverrides, environment, foreground, "lucee-express");

        // For background mode only: wait for startup and open browser
        if (!foreground && instance != null) {
            manager.waitForServerStartup(instance, 30);
            manager.openBrowserForServer(instance, config);
        }

        return instance;
    }
}
