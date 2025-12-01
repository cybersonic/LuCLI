package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * XML-based patching of Tomcat's server.xml.
 *
 * <p>This class starts from the vendor-provided server.xml from the Lucee
 * Express distribution and applies minimal, config-driven mutations based on
 * {@link LuceeServerConfig.ServerConfig}. The initial implementation focuses on
 * updating the HTTP connector port as a concrete example.</p>
 */
public class TomcatServerXmlPatcher {

    /**
     * Apply configuration-driven patches to the given server.xml file.
     *
     * @param serverXmlPath     path to the server.xml file to patch
     * @param config            loaded Lucee server configuration
     * @param projectDir        project directory used to resolve webroot
     * @param serverInstanceDir server instance directory (CATALINA_BASE)
     * @throws IOException if reading or writing the file fails in a future implementation
     */
    public void patch(Path serverXmlPath,
                      LuceeServerConfig.ServerConfig config,
                      Path projectDir,
                      Path serverInstanceDir) throws IOException {
        if (serverXmlPath == null || !Files.exists(serverXmlPath)) {
            return;
        }

        try (InputStream in = Files.newInputStream(serverXmlPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);



            // Apply minimal config-driven mutations.
            // 1) Set the HTTP connector port to match the LuceeServerConfig
            //    port for the primary HTTP/1.1 connector.
            applyHttpConnectorPort(document, config);
            // 2) Set the <Server> shutdown port to the effective shutdown port
            //    from LuceeServerConfig (explicit shutdownPort or HTTP+1000).
            applyShutdownPort(document, config);

            // Ensure there is a ROOT Context whose docBase matches the
            // resolved webroot for this server configuration.
            applyRootContext(document, config, projectDir, serverInstanceDir);








            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            try (OutputStream out = Files.newOutputStream(serverXmlPath)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        } catch (ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException("Failed to process server.xml: " + e.getMessage(), e);
        }
    }

/**
     * Set the port on the primary HTTP connector to match the Lucee
     * ServerConfig port.
     *
     * <p>This looks for the first &lt;Connector&gt; element that appears to be
     * the main HTTP connector (protocol="HTTP/1.1" or an empty protocol) and
     * updates its {@code port} attribute.</p>
     */
    private void applyHttpConnectorPort(Document document, LuceeServerConfig.ServerConfig config) {
        if (document == null || config == null) {
            return;
        }

        NodeList connectors = document.getElementsByTagName("Connector");
        if (connectors == null) {
            return;
        }

        for (int i = 0; i < connectors.getLength(); i++) {
            if (!(connectors.item(i) instanceof Element)) {
                continue;
            }

            Element connector = (Element) connectors.item(i);
            String protocol = connector.getAttribute("protocol");

            boolean isHttpConnector =
                "HTTP/1.1".equals(protocol) ||
                protocol == null || protocol.isEmpty() ||
                "org.apache.coyote.http11.Http11NioProtocol".equals(protocol);

            if (!isHttpConnector) {
                continue;
            }

            if (connector.hasAttribute("port")) {
                connector.setAttribute("port", String.valueOf(config.port));
                // Only update the first matching HTTP connector for now.
                break;
            }
        }
    }

    /**
     * Update the top-level <Server> shutdown port to match the effective
     * shutdown port from LuceeServerConfig (explicit shutdownPort or
     * HTTP+1000 when unset).
     */
    private void applyShutdownPort(Document document, LuceeServerConfig.ServerConfig config) {
        if (document == null || config == null) {
            return;
        }

        NodeList servers = document.getElementsByTagName("Server");
        if (servers == null || servers.getLength() == 0) {
            return;
        }

        for (int i = 0; i < servers.getLength(); i++) {
            if (!(servers.item(i) instanceof Element)) {
                continue;
            }
            Element server = (Element) servers.item(i);
            if (server.hasAttribute("port")) {
                int shutdownPort = LuceeServerConfig.getEffectiveShutdownPort(config);
                server.setAttribute("port", String.valueOf(shutdownPort));
                break; // only patch the first <Server> element
            }
        }
    }

    /**
     * Ensure there is a ROOT Context with docBase pointing at the resolved
     * webroot for this server configuration.
     */
    private void applyRootContext(Document document,
                                  LuceeServerConfig.ServerConfig config,
                                  Path projectDir,
                                  Path serverInstanceDir) {
        if (document == null || config == null || projectDir == null) {
            return;
        }

        // Resolve the effective webroot in the same way as the rest of the
        // server configuration logic.
        Path webroot;
        try {
            webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        } catch (RuntimeException e) {
            // If webroot resolution fails, leave server.xml unchanged.
            return;
        }

        String docBase = webroot.toAbsolutePath().toString();

        // Find the primary Engine element.
        NodeList engines = document.getElementsByTagName("Engine");
        if (engines == null || engines.getLength() == 0) {
            return;
        }

        Element engine = null;
        for (int i = 0; i < engines.getLength(); i++) {
            if (engines.item(i) instanceof Element) {
                engine = (Element) engines.item(i);
                break;
            }
        }
        if (engine == null) {
            return;
        }

        String defaultHostName = engine.getAttribute("defaultHost");

        // Find the Host to attach the Context to, preferring the defaultHost
        // if available.
        NodeList hosts = engine.getElementsByTagName("Host");
        if (hosts == null || hosts.getLength() == 0) {
            return;
        }

        Element targetHost = null;
        for (int i = 0; i < hosts.getLength(); i++) {
            if (!(hosts.item(i) instanceof Element)) {
                continue;
            }
            Element host = (Element) hosts.item(i);
            String name = host.getAttribute("name");
            if (defaultHostName != null && !defaultHostName.isEmpty() && defaultHostName.equals(name)) {
                targetHost = host;
                break;
            }
            if (targetHost == null) {
                targetHost = host; // fallback to first host
            }
        }
        if (targetHost == null) {
            return;
        }

        // Look for an existing root Context (path="" or "/").
        NodeList contexts = targetHost.getElementsByTagName("Context");
        Element rootContext = null;
        if (contexts != null) {
            for (int i = 0; i < contexts.getLength(); i++) {
                if (!(contexts.item(i) instanceof Element)) {
                    continue;
                }
                Element ctx = (Element) contexts.item(i);
                String path = ctx.getAttribute("path");
                if ("".equals(path) || "/".equals(path)) {
                    rootContext = ctx;
                    break;
                }
            }
        }

        if (rootContext == null) {
            // Create a new root Context.
            rootContext = document.createElement("Context");
            rootContext.setAttribute("path", "");
            rootContext.setAttribute("docBase", docBase);
            targetHost.appendChild(rootContext);
        } else {
            // Update the existing root Context's docBase.
            rootContext.setAttribute("docBase", docBase);
            if (!rootContext.hasAttribute("path")) {
                rootContext.setAttribute("path", "");
            }
        }
    }
}
