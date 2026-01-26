package org.lucee.lucli.server.runtime;

import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.LuceeServerManager;

/**
 * Abstraction for different runtime backends (lucee-express, tomcat, docker, ...).
 *
 * Implementations are responsible for generating any runtime-specific
 * configuration and starting the server process.
 */
public interface RuntimeProvider {

    /**
     * Runtime type identifier (e.g. "lucee-express", "tomcat").
     */
    String getType();

    /**
     * Start a server instance using the given configuration and context.
     *
     * Implementations may call back into {@link LuceeServerManager} for shared
     * helpers such as downloading runtimes, deploying extensions, and launching
     * the server process.
     */
    LuceeServerManager.ServerInstance start(
            LuceeServerManager manager,
            LuceeServerConfig.ServerConfig config,
            Path projectDir,
            String environment,
            LuceeServerManager.AgentOverrides agentOverrides,
            boolean foreground,
            boolean forceReplace
    ) throws Exception;
}
