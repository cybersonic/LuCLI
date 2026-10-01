package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        assertEquals("9000", connector(result, "HTTP/1.1").getAttribute("port"),
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

    private static final String SERVER_XML = """
        <Server port="8005" shutdown="SHUTDOWN">
          <Service name="Catalina">
            <Connector port="8080" protocol="HTTP/1.1" />
            <Connector protocol="AJP/1.3" port="8009" secretRequired="false" redirectPort="8443" />
            <Engine name="Catalina" defaultHost="localhost" />
          </Service>
        </Server>
        """;

    private static String patch(String serverXml, LuceeServerConfig.ServerConfig config) throws Exception {
        Path projectDir = Paths.get(".").toAbsolutePath().normalize();
        Path serverInstanceDir = Paths.get("build/test-server-instance").toAbsolutePath().normalize();
        return new TomcatServerXmlPatcher().patchContent(serverXml, config, projectDir, serverInstanceDir, false);
    }

    private static LuceeServerConfig.ServerConfig baseConfig() {
        LuceeServerConfig.ServerConfig config = new LuceeServerConfig.ServerConfig();
        config.port = 9000;
        config.webroot = "./";
        return config;
    }

    /** First Connector whose protocol attribute contains the given text. */
    private static Element connector(String xml, String protocolContains) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        NodeList list = doc.getElementsByTagName("Connector");
        for (int i = 0; i < list.getLength(); i++) {
            Element c = (Element) list.item(i);
            if (c.getAttribute("protocol").contains(protocolContains)) {
                return c;
            }
        }
        throw new AssertionError("no Connector with protocol containing " + protocolContains + " in:\n" + xml);
    }

    private static Element httpsConnector(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        NodeList list = doc.getElementsByTagName("Connector");
        for (int i = 0; i < list.getLength(); i++) {
            Element c = (Element) list.item(i);
            if ("https".equals(c.getAttribute("scheme"))) {
                return c;
            }
        }
        throw new AssertionError("no HTTPS Connector in:\n" + xml);
    }

    @Test
    void httpConnector_listensOnLoopbackByDefault() throws Exception {
        String result = patch(SERVER_XML, baseConfig());
        assertEquals("127.0.0.1", connector(result, "HTTP/1.1").getAttribute("address"));
    }

    @Test
    void httpConnector_usesConfiguredBindAddress() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.bindAddress = "0.0.0.0";
        assertEquals("0.0.0.0", connector(patch(SERVER_XML, config), "HTTP/1.1").getAttribute("address"));
    }

    @Test
    void httpConnector_mapsWildcardToAllIpv4Interfaces() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.bindAddress = "*";
        assertEquals("0.0.0.0", connector(patch(SERVER_XML, config), "HTTP/1.1").getAttribute("address"));
    }

    @Test
    void httpsConnector_listensOnTheSameAddress() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.https = new LuceeServerConfig.HttpsConfig();
        config.https.enabled = true;
        config.https.port = 9443;
        String result = patch(SERVER_XML, config);
        assertEquals("127.0.0.1", httpsConnector(result).getAttribute("address"));
        config.bindAddress = "0.0.0.0";
        assertEquals("0.0.0.0", httpsConnector(patch(SERVER_XML, config)).getAttribute("address"));
    }

    @Test
    void enabledAjpConnector_getsTheBindAddressUnlessItSetsItsOwn() throws Exception {
        LuceeServerConfig.ServerConfig config = baseConfig();
        config.ajp.enabled = true;
        assertEquals("127.0.0.1", connector(patch(SERVER_XML, config), "AJP").getAttribute("address"));

        String withOwnAddress = SERVER_XML.replace("protocol=\"AJP/1.3\"", "protocol=\"AJP/1.3\" address=\"::1\"");
        assertEquals("::1", connector(patch(withOwnAddress, config), "AJP").getAttribute("address"));
    }

    @Test
    void anyOtherConnectorWithoutAnAddress_listensOnTheBindAddress() throws Exception {
        String nio2 = SERVER_XML.replace(
                "<Connector port=\"8080\" protocol=\"HTTP/1.1\" />",
                "<Connector port=\"8080\" protocol=\"org.apache.coyote.http11.Http11Nio2Protocol\" />"
                        + "<Connector port=\"8090\" protocol=\"org.apache.coyote.http11.Http11AprProtocol\" address=\"10.0.0.5\" />");
        String result = patch(nio2, baseConfig());
        assertEquals("127.0.0.1", connector(result, "Nio2").getAttribute("address"),
                "a connector the protocol allow-list doesn't name must not stay on all interfaces");
        assertEquals("10.0.0.5", connector(result, "Apr").getAttribute("address"),
                "an explicit address in server.xml is kept");
    }
}
