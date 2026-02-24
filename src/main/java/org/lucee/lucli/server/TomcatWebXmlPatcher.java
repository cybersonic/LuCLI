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
     * Ensure Lucee servlets are present in the web.xml.
     * This adds CFMLServlet and optionally RESTServlet if they don't already exist.
     *
     * @param webXmlPath        path to the web.xml file to patch
     * @param config            loaded Lucee server configuration
     * @param serverInstanceDir server instance directory (for lucee-server-root and lucee-web-root paths)
     */
    public void ensureLuceeServlets(Path webXmlPath,
                                    LuceeServerConfig.ServerConfig config,
                                    Path serverInstanceDir) throws IOException {
        if (webXmlPath == null || !Files.exists(webXmlPath)) {
            return;
        }

        if (config != null && !config.enableLucee) {
            // Lucee disabled, don't add servlets
            return;
        }

        Path luceeServerRoot = serverInstanceDir.resolve(""); //Removing the extra "lucee-server" as that is where it's always created , it stop s the 'lucee-server/lucee-server/context' issue
        Path luceeWebRoot = serverInstanceDir.resolve("lucee-web");

        try (InputStream in = Files.newInputStream(webXmlPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);

            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            // Check if CFMLServlet already exists
            boolean hasCfmlServlet = false;
            boolean hasRestServlet = false;
            NodeList servletNodes = root.getElementsByTagName("servlet");
            for (int i = 0; i < servletNodes.getLength(); i++) {
                Node node = servletNodes.item(i);
                if (node instanceof Element) {
                    String servletName = getChildText((Element) node, "servlet-name");
                    if ("CFMLServlet".equals(servletName)) {
                        hasCfmlServlet = true;
                    } else if ("RESTServlet".equalsIgnoreCase(servletName)) {
                        hasRestServlet = true;
                    }
                }
            }

            // Add CFMLServlet if not present
            if (!hasCfmlServlet) {
                addCfmlServlet(root, luceeServerRoot, luceeWebRoot);
                addCfmlServletMappings(root);
                addCfmlWelcomeFiles(root);
                System.out.println("Added Lucee CFMLServlet to web.xml");
            }

            // Add RESTServlet if enabled and not present
            if (config != null && config.enableREST && !hasRestServlet) {
                addRestServlet(root);
                addRestServletMapping(root);
                System.out.println("Added Lucee RESTServlet to web.xml");
            }

            // Remove JSP servlets - not needed for CFML apps and reduces attack surface
            removeJspServlets(root);

            // Also ensure lucee.json is protected
            ensureLuceeJsonProtected(document);

            // Write back
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
     * Remove JSP-related servlets and mappings from web.xml.
     * JSP servlets are not needed for CFML applications and removing them
     * reduces the attack surface and improves startup time.
     */
    private void removeJspServlets(Element root) {
        if (root == null) {
            return;
        }

        // JSP-related servlet names to remove
        Set<String> jspServletNames = new HashSet<>();
        jspServletNames.add("jsp");
        jspServletNames.add("jspx");

        // JSP-related URL patterns to remove
        Set<String> jspPatterns = new HashSet<>();
        jspPatterns.add("*.jsp");
        jspPatterns.add("*.jspx");

        boolean removedServlet = false;
        boolean removedMapping = false;

        // 1) Remove JSP servlets
        NodeList servletNodes = root.getElementsByTagName("servlet");
        for (int i = 0; i < servletNodes.getLength(); i++) {
            Node node = servletNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element servlet = (Element) node;
            String servletName = getChildText(servlet, "servlet-name");
            String servletClass = getChildText(servlet, "servlet-class");

            // Match by name or by class (JspServlet)
            boolean isJspServlet = (servletName != null && jspServletNames.contains(servletName.toLowerCase()))
                    || (servletClass != null && servletClass.toLowerCase().contains("jspservlet"));

            if (isJspServlet) {
                if (servletName != null) {
                    jspServletNames.add(servletName); // Track actual name for mapping removal
                }
                root.removeChild(servlet);
                removedServlet = true;
                i--; // Adjust index because NodeList is live
            }
        }

        // 2) Remove JSP servlet mappings
        NodeList mappingNodes = root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Node node = mappingNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element mapping = (Element) node;
            String name = getChildText(mapping, "servlet-name");
            String pattern = getChildText(mapping, "url-pattern");

            boolean isJspMapping = (name != null && jspServletNames.contains(name.toLowerCase()))
                    || (pattern != null && jspPatterns.contains(pattern.toLowerCase()));

            if (isJspMapping) {
                root.removeChild(mapping);
                removedMapping = true;
                i--; // Adjust index because NodeList is live
            }
        }

        if (removedServlet || removedMapping) {
            System.out.println("Removed JSP servlets from web.xml (not needed for CFML)");
        }
    }

    /**
     * Add CFMLServlet definition to web.xml.
     */
    private void addCfmlServlet(Element root, Path luceeServerRoot, Path luceeWebRoot) {
        Element servlet = append(root, "servlet", null);
        append(servlet, "servlet-name", "CFMLServlet");
        append(servlet, "servlet-class", "lucee.loader.servlet.jakarta.CFMLServlet");

        Element initParam1 = append(servlet, "init-param", null);
        append(initParam1, "param-name", "lucee-server-root");
        append(initParam1, "param-value", luceeServerRoot.toAbsolutePath().toString());

        Element initParam2 = append(servlet, "init-param", null);
        append(initParam2, "param-name", "lucee-web-root");
        append(initParam2, "param-value", luceeWebRoot.toAbsolutePath().toString());

        append(servlet, "load-on-startup", "1");
    }

    /**
     * Add CFML servlet mappings.
     */
    private void addCfmlServletMappings(Element root) {
        String[] patterns = {"*.cfm", "*.cfml", "*.cfc", "*.cfs", "/index.cfm/*", "/lucee/*"};
        for (String pattern : patterns) {
            Element mapping = append(root, "servlet-mapping", null);
            append(mapping, "servlet-name", "CFMLServlet");
            append(mapping, "url-pattern", pattern);
        }
    }

    /**
     * Add CFML welcome files.
     */
    private void addCfmlWelcomeFiles(Element root) {
        // Find existing welcome-file-list or create one
        NodeList welcomeLists = root.getElementsByTagName("welcome-file-list");
        Element welcomeList;
        if (welcomeLists.getLength() > 0) {
            welcomeList = (Element) welcomeLists.item(0);
        } else {
            welcomeList = append(root, "welcome-file-list", null);
        }

        // Check existing welcome files
        Set<String> existingFiles = new HashSet<>();
        NodeList files = welcomeList.getElementsByTagName("welcome-file");
        for (int i = 0; i < files.getLength(); i++) {
            Node node = files.item(i);
            if (node instanceof Element) {
                String text = node.getTextContent();
                if (text != null) {
                    existingFiles.add(text.trim());
                }
            }
        }

        // Add CFML welcome files at the beginning if not present
        String[] cfmlFiles = {"index.cfm", "index.cfml"};
        Node firstChild = welcomeList.getFirstChild();
        for (String file : cfmlFiles) {
            if (!existingFiles.contains(file)) {
                Element welcomeFile = root.getOwnerDocument().createElement("welcome-file");
                welcomeFile.setTextContent(file);
                if (firstChild != null) {
                    welcomeList.insertBefore(welcomeFile, firstChild);
                } else {
                    welcomeList.appendChild(welcomeFile);
                }
            }
        }
    }

    /**
     * Add RESTServlet definition.
     */
    private void addRestServlet(Element root) {
        Element servlet = append(root, "servlet", null);
        append(servlet, "servlet-name", "RESTServlet");
        append(servlet, "servlet-class", "lucee.loader.servlet.jakarta.RestServlet");
        append(servlet, "load-on-startup", "2");
    }

    /**
     * Add REST servlet mapping.
     */
    private void addRestServletMapping(Element root) {
        Element mapping = append(root, "servlet-mapping", null);
        append(mapping, "servlet-name", "RESTServlet");
        append(mapping, "url-pattern", "/rest/*");
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

    /**
     * Ensure UrlRewriteFilter is present in the web.xml.
     *
     * @param webXmlPath        path to the web.xml file to patch
     * @param serverInstanceDir server instance directory (CATALINA_BASE) where urlrewrite.xml is located
     */
    public void ensureUrlRewriteFilter(Path webXmlPath, Path serverInstanceDir) throws IOException {
        if (webXmlPath == null || !Files.exists(webXmlPath)) {
            return;
        }

        Path urlRewriteConfig = serverInstanceDir.resolve("conf/urlrewrite.xml");

        try (InputStream in = Files.newInputStream(webXmlPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);

            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            // Check if UrlRewriteFilter already exists
            boolean hasUrlRewriteFilter = false;
            NodeList filterNodes = root.getElementsByTagName("filter");
            for (int i = 0; i < filterNodes.getLength(); i++) {
                Node node = filterNodes.item(i);
                if (node instanceof Element) {
                    String filterName = getChildText((Element) node, "filter-name");
                    if ("UrlRewriteFilter".equals(filterName)) {
                        hasUrlRewriteFilter = true;
                        break;
                    }
                }
            }

            if (hasUrlRewriteFilter) {
                return; // Already configured
            }

            // Add UrlRewriteFilter
            addUrlRewriteFilter(root, urlRewriteConfig);
            addUrlRewriteFilterMapping(root);
            System.out.println("Added UrlRewriteFilter to web.xml");

            // Write back
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
     * Add UrlRewriteFilter definition to web.xml.
     */
    private void addUrlRewriteFilter(Element root, Path urlRewriteConfig) {
        // Find first servlet element to insert filter before it
        NodeList servlets = root.getElementsByTagName("servlet");
        Node insertBefore = servlets.getLength() > 0 ? servlets.item(0) : null;

        Element filter = root.getOwnerDocument().createElement("filter");
        append(filter, "filter-name", "UrlRewriteFilter");
        append(filter, "filter-class", "org.tuckey.web.filters.urlrewrite.UrlRewriteFilter");

        // Configure the path to urlrewrite.xml
        Element initParam = append(filter, "init-param", null);
        append(initParam, "param-name", "confPath");
        append(initParam, "param-value", urlRewriteConfig.toAbsolutePath().toString());

        // Optional: reload config every N seconds (useful for development)
        Element reloadParam = append(filter, "init-param", null);
        append(reloadParam, "param-name", "confReloadCheckInterval");
        append(reloadParam, "param-value", "0"); // 0 = check on every request (dev mode), -1 = never

        if (insertBefore != null) {
            root.insertBefore(filter, insertBefore);
        } else {
            root.appendChild(filter);
        }
    }

    /**
     * Add UrlRewriteFilter mapping.
     */
    private void addUrlRewriteFilterMapping(Element root) {
        // Find first servlet-mapping element to insert filter-mapping before it
        NodeList servletMappings = root.getElementsByTagName("servlet-mapping");
        Node insertBefore = servletMappings.getLength() > 0 ? servletMappings.item(0) : null;

        Element filterMapping = root.getOwnerDocument().createElement("filter-mapping");
        append(filterMapping, "filter-name", "UrlRewriteFilter");
        append(filterMapping, "url-pattern", "/*");
        append(filterMapping, "dispatcher", "REQUEST");
        append(filterMapping, "dispatcher", "FORWARD");

        if (insertBefore != null) {
            root.insertBefore(filterMapping, insertBefore);
        } else {
            root.appendChild(filterMapping);
        }
    }
}
