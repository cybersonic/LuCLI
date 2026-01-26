package org.lucee.lucli.server.runtime;

import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Runtime provider for the "tomcat" runtime type.
 *
 * For now this behaves as a placeholder and clearly indicates that the tomcat
 * runtime is not yet implemented. This mirrors the previous behaviour in
 * LuceeServerManager while separating responsibilities.
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
        // Resolve effective runtime so that any future tomcat-specific
        // fields (catalinaHome, patchMode, etc.) are available.
        LuceeServerConfig.getEffectiveRuntime(config);

        System.out.println("Using runtime.type=\"tomcat\"");

        // Explicitly signal that the tomcat runtime path is not yet implemented.
        throw new UnsupportedOperationException("tomcat runtime is not yet implemented");
    }
}
