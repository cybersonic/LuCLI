package org.lucee.lucli.server.runtime;

import java.io.IOException;
import java.nio.file.Path;

import org.lucee.lucli.server.LuceeServerConfig;
import org.lucee.lucli.server.TomcatConfigGenerator;

/**
 * Thin wrapper around the existing Tomcat configuration generator for the
 * Lucee Express-based runtime. This class exists mainly for naming clarity so
 * that the external Tomcat runtime can evolve a separate TomcatConfigGenerator
 * implementation without conflating responsibilities.
 */
public class LuceeExpressConfigGenerator {

    private final TomcatConfigGenerator delegate = new TomcatConfigGenerator();
    
    public void generateConfiguration(Path serverInstanceDir,
                                      LuceeServerConfig.ServerConfig config,
                                      Path projectDir,
                                      Path luceeExpressDir,
                                      boolean overwriteProjectConfig) throws IOException {
        delegate.generateConfiguration(serverInstanceDir, config, projectDir, luceeExpressDir, overwriteProjectConfig);
    }
}
