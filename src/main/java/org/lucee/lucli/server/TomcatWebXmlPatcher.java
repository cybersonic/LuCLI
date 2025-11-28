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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * XML-based patching of Tomcat/Lucee web.xml.
 *
 * <p>This class starts from the vendor-provided web.xml and applies
 * LuCLI-specific behavior (e.g. admin enable/disable, UrlRewrite
 * configuration, and protection of LuCLI configuration files) based on
 * {@link LuceeServerConfig.ServerConfig}.</p>
 */
public class TomcatWebXmlPatcher {

    /**
     * Apply configuration-driven patches to the given web.xml file.
     *
     * @param webXmlPath        path to the web.xml file to patch
     * @param config            loaded Lucee server configuration
     * @param projectDir        project directory used to resolve webroot
     * @param serverInstanceDir server instance directory (CATALINA_BASE)
     * @throws IOException if reading or writing the file fails in a future implementation
     */
    public void patch(Path webXmlPath,
                      LuceeServerConfig.ServerConfig config,
                      Path projectDir,
                      Path serverInstanceDir) throws IOException {
        if (webXmlPath == null || !Files.exists(webXmlPath)) {
            return;
        }

        try (InputStream in = Files.newInputStream(webXmlPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);

            // Ensure that lucee.json cannot be served by the web application.
            ensureLuceeJsonProtected(document);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            try (OutputStream out = Files.newOutputStream(webXmlPath)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        } catch (ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException("Failed to process web.xml: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure that the lucee.json configuration file is not accessible via HTTP.
     *
     * <p>Adds a security-constraint that denies all access to /lucee.json if
     * no such rule already exists.</p>
     */
    private void ensureLuceeJsonProtected(Document document) {
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Check if there is already a security-constraint protecting /lucee.json.
        NodeList constraints = root.getElementsByTagName("security-constraint");
        if (constraints != null) {
            for (int i = 0; i < constraints.getLength(); i++) {
                Node node = constraints.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element constraint = (Element) node;
                NodeList collections = constraint.getElementsByTagName("web-resource-collection");
                for (int j = 0; j < collections.getLength(); j++) {
                    Node cNode = collections.item(j);
                    if (!(cNode instanceof Element)) {
                        continue;
                    }
                    Element collection = (Element) cNode;
                    NodeList urlPatterns = collection.getElementsByTagName("url-pattern");
                    for (int k = 0; k < urlPatterns.getLength(); k++) {
                        Node pNode = urlPatterns.item(k);
                        if (!(pNode instanceof Element)) {
                            continue;
                        }
                        String pattern = pNode.getTextContent();
                        if (pattern != null && pattern.trim().equals("/lucee.json")) {
                            // Already protected.
                            return;
                        }
                    }
                }
            }
        }

        // Not yet protected: append a new security-constraint at the end of web-app.
        Element securityConstraint = document.createElement("security-constraint");

        Element webResourceCollection = document.createElement("web-resource-collection");
        Element webResourceName = document.createElement("web-resource-name");
        webResourceName.setTextContent("LuCLI configuration");
        Element urlPattern = document.createElement("url-pattern");
        urlPattern.setTextContent("/lucee.json");
        webResourceCollection.appendChild(webResourceName);
        webResourceCollection.appendChild(urlPattern);

        Element authConstraint = document.createElement("auth-constraint");
        // Empty auth-constraint means no roles are allowed => deny all.

        securityConstraint.appendChild(webResourceCollection);
        securityConstraint.appendChild(authConstraint);

        root.appendChild(securityConstraint);
    }
}
