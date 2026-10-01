package org.lucee.lucli.server.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.server.LuceeServerConfig;

public class JettyBaseConfigGeneratorTest {

    @TempDir
    Path tempDir;

    private String httpIniFor(String bindAddress) throws Exception {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.name = "jetty-bind-test";
        config.port = 8181;
        config.bindAddress = bindAddress;
        // Only [A-Za-z0-9] in the directory name: "*" and ":" are invalid in Windows paths.
        Path jettyBase = tempDir.resolve("base-" + (bindAddress == null ? "default" : bindAddress.replaceAll("[^A-Za-z0-9]", "_")) + "-" + System.nanoTime());
        Files.createDirectories(jettyBase.resolve("start.d"));

        Method m = JettyBaseConfigGenerator.class.getDeclaredMethod("generateStartIniFiles",
                Path.class, LuceeServerConfig.ServerConfig.class, int.class, int.class, String.class);
        m.setAccessible(true);
        m.invoke(new JettyBaseConfigGenerator(), jettyBase, config, 12, 9181, "stop-key");
        return Files.readString(jettyBase.resolve("start.d").resolve("http.ini"));
    }

    @Test
    void httpIni_listensOnLoopbackByDefault() throws Exception {
        assertTrue(httpIniFor(null).contains("jetty.http.host=127.0.0.1\n"));
    }

    @Test
    void httpIni_usesConfiguredBindAddress() throws Exception {
        assertTrue(httpIniFor("0.0.0.0").contains("jetty.http.host=0.0.0.0\n"));
        assertTrue(httpIniFor("*").contains("jetty.http.host=0.0.0.0\n"));
    }
}
