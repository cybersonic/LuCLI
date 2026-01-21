package org.lucee.lucli.server;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Shared helpers for Tomcat-related configuration that are independent of
 * whether we are targeting the embedded Lucee Express Tomcat layout or an
 * external Tomcat installation.
 *
 * This starts small; as the external Tomcat runtime evolves we can move
 * additional cross-cutting logic (XML patching, UrlRewrite deployment,
 * setenv.sh generation, etc.) into this class.
 */
final class TomcatConfigSupport {

    private TomcatConfigSupport() {
    }

    /**
     * Convenience wrapper to write setenv scripts for a given CATALINA_BASE
     * using the same JVM option assembly logic as LuceeServerManager.
     */
    static void writeSetenvScripts(Path catalinaBase,
                                   LuceeServerConfig.ServerConfig config,
                                   Path projectDir) throws IOException {
        TomcatConfigGenerator.writeSetenvScripts(catalinaBase, config, projectDir);
    }
}