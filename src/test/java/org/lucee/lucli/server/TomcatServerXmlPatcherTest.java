package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for TomcatServerXmlPatcher.patchContent.
 *
 * These are intentionally simple "smoke" tests whose main goal is to let you
 * step through the patching logic in an IDE (VS Code) while still providing
 * some basic assertions so changes are safe to make.
 */
public class TomcatServerXmlPatcherTest {

    @Test
    void patchContent_updatesHttpAndShutdownPorts() throws Exception {
        String serverXml = """
            <Server port=\"8005\" shutdown=\"SHUTDOWN\">
              <Service name=\"Catalina\">
                <Connector port=\"8080\" protocol=\"HTTP/1.1\" />
                <Connector protocol=\"AJP/1.3\" port=\"8009\" secretRequired=\"false\" redirectPort=\"8443\" />
              </Service>
            </Server>
            """;

        // Minimal fake config
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.port = 9000;          // desired HTTP port
        config.shutdownPort = 9100;  // desired shutdown port
        config.webroot = "./";      // keep default webroot
        // config.ajp.enabled = true; // keep AJP enabled

        Path projectDir = Paths.get(".").toAbsolutePath().normalize();
        Path serverInstanceDir = Paths.get("build/test-server-instance").toAbsolutePath().normalize();

        TomcatServerXmlPatcher patcher = new TomcatServerXmlPatcher();

        // writeFiles = false keeps this side-effect free (no keystore/rewrite files written)
        String result = patcher.patchContent(serverXml, config, projectDir, serverInstanceDir, false);

        // System.err.println("Patched server.xml:\n" + result);
        assertNotNull(result, "Patched XML should not be null");

        // HTTP connector should be updated to 9000
        assertTrue(result.contains("<Connector port=\"9000\""),
                "HTTP connector port should be updated to 9000");

        // AJP connector should keep its original port (8009)
        // assertTrue(!result.contains("protocol=\"AJP/1.3\""),
        //         "AJP connector should have be present");
        // assertTrue(
        //         result.contains("protocol=\"AJP/1.3\" port=\"8009\"") ||
        //         result.contains("port=\"8009\" protocol=\"AJP/1.3\""),
        //         "AJP connector port should remain 8009 and not be changed to 9000");

        // Shutdown port should track the effective shutdown port
        assertTrue(result.contains("Server port=\"9100\""), "Server shutdown port should be updated to 9100");
    }
}
