package org.lucee.lucli.server;

import java.io.IOException;
import java.nio.file.Path;

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