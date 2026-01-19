package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

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

import static org.lucee.lucli.server.XmlHelper.append;
import static org.lucee.lucli.server.XmlHelper.exists;

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

            // Optionally strip Lucee servlets, mappings, and CFML welcome files
            // when the configuration explicitly disables Lucee. This allows
            // Tomcat to act purely as a static file server (e.g. for markpresso)
            // while still reusing the same template web.xml.
            if (config != null && !config.enableLucee) {
                disableLuceeEngine(document);
                disableRestServlet(document);
            } else if (config != null && !config.enableREST) {
                // If Lucee is enabled but REST is disabled, only disable REST
                disableRestServlet(document);
            }

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

        try {
            // Check if there is already a security-constraint protecting /lucee.json.
            boolean alreadyProtected = exists(
                document,
                "//security-constraint" +
                "[web-resource-collection/url-pattern[normalize-space(text())='/lucee.json']]"
            );
            if (alreadyProtected) {
                return;
            }
        } catch (XPathExpressionException e) {
            // If XPath evaluation fails for any reason, fail-safe by leaving
            // web.xml unchanged rather than risking duplicate or broken rules.
            return;
        }

        // Not yet protected: append a new security-constraint at the end of web-app.
        Element securityConstraint = append(root, "security-constraint", null);

        Element webResourceCollection = append(securityConstraint, "web-resource-collection", null);
        append(webResourceCollection, "web-resource-name", "LuCLI configuration");
        append(webResourceCollection, "url-pattern", "/lucee.json");

        // Empty auth-constraint means no roles are allowed => deny all.
        append(securityConstraint, "auth-constraint", null);
    }

    /**
     * Remove Lucee CFML servlets, servlet-mappings and CFML welcome files
     * so that Tomcat behaves as a plain static file server.
     * This does NOT remove the REST servlet - use disableRestServlet() for that.
     */
    private void disableLuceeEngine(Document document) {
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // 1) Remove Lucee CFML servlets (but not REST) and remember their servlet-names
        Set<String> luceeServletNames = new HashSet<>();
        NodeList servletNodes = root.getElementsByTagName("servlet");
        for (int i = 0; i < servletNodes.getLength(); i++) {
            Node node = servletNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element servlet = (Element) node;

            String servletName = getChildText(servlet, "servlet-name");
            String servletClass = getChildText(servlet, "servlet-class");

            // Only remove CFML servlet, not REST servlet
            boolean isLuceeCfmlServlet =
                (servletClass != null && servletClass.startsWith("lucee.loader.servlet")) ||
                "CFMLServlet".equals(servletName);

            if (isLuceeCfmlServlet) {
                if (servletName != null) {
                    luceeServletNames.add(servletName);
                }
                root.removeChild(servlet);
                i--; // Adjust index because NodeList is live
            }
        }

        // 2) Remove servlet-mappings for Lucee CFML servlets and CFML/admin URL patterns
        NodeList mappingNodes = root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Node node = mappingNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element mapping = (Element) node;

            String name = getChildText(mapping, "servlet-name");
            String pattern = getChildText(mapping, "url-pattern");

            boolean isCfmlPattern = pattern != null && (
                pattern.endsWith("*.cfm") ||
                pattern.endsWith("*.cfml") ||
                pattern.endsWith("*.cfc") ||
                pattern.endsWith("*.cfs") ||
                "/index.cfm/*".equals(pattern) ||
                pattern.startsWith("/lucee/")
            );

            if ((name != null && luceeServletNames.contains(name)) || isCfmlPattern) {
                root.removeChild(mapping);
                i--; // Adjust index because NodeList is live
            }
        }

        // 3) Remove CFML welcome files (index.cfm/index.cfml) so that HTML/HTM
        //    welcome files take precedence.
        NodeList welcomeLists = root.getElementsByTagName("welcome-file-list");
        for (int i = 0; i < welcomeLists.getLength(); i++) {
            Node listNode = welcomeLists.item(i);
            if (!(listNode instanceof Element)) {
                continue;
            }
            Element list = (Element) listNode;

            NodeList welcomeFiles = list.getElementsByTagName("welcome-file");
            List<Element> toRemove = new ArrayList<>();
            for (int j = 0; j < welcomeFiles.getLength(); j++) {
                Node wfNode = welcomeFiles.item(j);
                if (!(wfNode instanceof Element)) {
                    continue;
                }
                Element wf = (Element) wfNode;
                String name = wf.getTextContent() != null ? wf.getTextContent().trim() : "";
                if (name.endsWith(".cfm") || name.endsWith(".cfml")) {
                    toRemove.add(wf);
                }
            }

            for (Element wf : toRemove) {
                list.removeChild(wf);
            }
        }
    }

    /**
     * Remove REST servlet and its mappings.
     * This can be called independently of disableLuceeEngine().
     */
    private void disableRestServlet(Document document) {
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // 1) Remove REST servlet and remember its servlet-name
        Set<String> restServletNames = new HashSet<>();
        NodeList servletNodes = root.getElementsByTagName("servlet");
        for (int i = 0; i < servletNodes.getLength(); i++) {
            Node node = servletNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element servlet = (Element) node;

            String servletName = getChildText(servlet, "servlet-name");

            if ("restservlet".equals(servletName.toLowerCase())) {
                restServletNames.add(servletName);
                root.removeChild(servlet);
                i--; // Adjust index because NodeList is live
            }
        }

        // 2) Remove servlet-mappings for REST servlet and /rest/* URL patterns
        NodeList mappingNodes = root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Node node = mappingNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element mapping = (Element) node;

            String name = getChildText(mapping, "servlet-name");
            String pattern = getChildText(mapping, "url-pattern");

            boolean isRestPattern = pattern != null && pattern.startsWith("/rest/");

            if ((name != null && restServletNames.contains(name)) || isRestPattern) {
                root.removeChild(mapping);
                i--; // Adjust index because NodeList is live
            }
        }
    }

    /**
     * Helper to read the text content of the first direct child element whose
     * (local) name matches the provided name. This is tolerant of XML
     * namespaces by checking both the raw nodeName and any localName suffix.
     */
    private String getChildText(Element parent, String childLocalName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element el = (Element) node;
            String nodeName = el.getNodeName();
            if (childLocalName.equals(nodeName) || nodeName.endsWith(":" + childLocalName)) {
                return el.getTextContent();
            }
        }
        return null;
    }
}
