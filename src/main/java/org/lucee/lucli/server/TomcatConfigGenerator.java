package org.lucee.lucli.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Tomcat configuration files for Lucee server instances.
 */
public class TomcatConfigGenerator {
    
    private static final String URLREWRITE_VERSION = "5.1.3";
    private static final String URLREWRITE_MAVEN_URL =
        "https://repo1.maven.org/maven2/org/tuckey/urlrewritefilter/" + URLREWRITE_VERSION +
        "/urlrewritefilter-" + URLREWRITE_VERSION + ".jar";
    
    
    private final TomcatServerXmlPatcher serverXmlPatcher = new TomcatServerXmlPatcher();
    private final TomcatWebXmlPatcher webXmlPatcher = new TomcatWebXmlPatcher();
    
    /**
     * Generate complete server instance by copying Lucee Express and applying templates.
     *
     * @param overwriteProjectConfig when true, project-level config files under WEB-INF
     *                               (web.xml, urlrewrite.xml, UrlRewriteFilter JAR) will be
     *                               overwritten even if they already exist. When false, any
     *                               existing project files are preserved.
     */
    public void generateConfiguration(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                    Path projectDir, Path luceeExpressDir,
                                    boolean overwriteProjectConfig) throws IOException {
        
        // Copy entire Lucee Express distribution to server instance directory
        copyLuceeExpressDistribution(luceeExpressDir, serverInstanceDir);
        
        // Apply template configurations
        applyConfigurationTemplates(serverInstanceDir, config, projectDir, overwriteProjectConfig);

        // Generate Tomcat setenv scripts that persist JVM configuration derived
        // from lucee.json into a Tomcat-consumable format (setenv.sh / setenv.bat).
        writeSetenvScripts(serverInstanceDir, config, projectDir);
    }
    
    /**
     * Apply configuration templates by processing template files and replacing placeholders
     */
    private void applyConfigurationTemplates(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                           Path projectDir, boolean overwriteProjectConfig) throws IOException {
        
        // Create placeholder replacement map
        Map<String, String> placeholders = createPlaceholderMap(serverInstanceDir, config, projectDir);
        
        // Create the lucee-web directory in the server instance
        Path luceeWebDir = serverInstanceDir.resolve("lucee-web");
        Files.createDirectories(luceeWebDir);
        
        // Ensure the global patches directory exists
        Path patchesDir = getLucliHome().resolve("patches");
        Files.createDirectories(patchesDir);
        
        // Use the vendor-provided server.xml copied from Lucee Express and let
        // the XML patcher handle any modifications. For now, the patcher only
        // parses and writes the XML back out without semantic changes.
        Path serverXmlPath = serverInstanceDir.resolve("conf/server.xml");
        serverXmlPatcher.patch(serverXmlPath, config, projectDir, serverInstanceDir);
        
        // Apply logging.properties template (still template-based for now)
        applyTemplate("tomcat_template/conf/logging.properties", serverInstanceDir.resolve("conf/logging.properties"), placeholders);
        
        // For web.xml, start from the vendor-provided ROOT web.xml inside the
        // copied Lucee Express distribution, then copy it into the project
        // WEB-INF if appropriate and let the XML patcher handle any
        // modifications (currently parse+write only).
        Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        Path webrootWebInf = webroot.resolve("WEB-INF");
        Files.createDirectories(webrootWebInf);
        Path projectWebXml = webrootWebInf.resolve("web.xml");

        // Determine the vendor web.xml location. Prefer the traditional
        // ROOT/WEB-INF/web.xml, but fall back to conf/web.xml for Lucee
        // Express layouts that place the global web.xml there.
        Path vendorRootWebXml = serverInstanceDir.resolve("webapps/ROOT/WEB-INF/web.xml");
        if (!Files.exists(vendorRootWebXml)) {
            Path confWebXml = serverInstanceDir.resolve("conf/web.xml");
            if (Files.exists(confWebXml)) {
                vendorRootWebXml = confWebXml;
            }
        }

        if (Files.exists(vendorRootWebXml) && (overwriteProjectConfig || !Files.exists(projectWebXml))) {
            Files.copy(vendorRootWebXml, projectWebXml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else if (!Files.exists(projectWebXml)) {
            System.out.println("No vendor web.xml found at " + vendorRootWebXml.toAbsolutePath() +
                " and no existing project web.xml to preserve.");
        } else {
            System.out.println("Preserving existing project web.xml at " + projectWebXml.toAbsolutePath());
        }

        // Let the XML patcher parse and re-write the project web.xml. This is
        // currently a no-op in terms of configuration changes but exercises
        // the DOM load/save pipeline.
        webXmlPatcher.patch(projectWebXml, config, projectDir, serverInstanceDir);
        
        // Apply urlrewrite.xml template to PROJECT directory only if URL rewrite is enabled
        // and the Lucee engine is enabled (static servers do not need CFML URL rewriting).
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            Path projectUrlRewriteXml = webrootWebInf.resolve("urlrewrite.xml");
            if (overwriteProjectConfig || !Files.exists(projectUrlRewriteXml)) {
                applyTemplate("tomcat_template/webapps/ROOT/WEB-INF/urlrewrite.xml", projectUrlRewriteXml, placeholders);
            } else {
                System.out.println("Preserving existing project urlrewrite.xml at " + projectUrlRewriteXml.toAbsolutePath());
            }
        }
        
        // Deploy UrlRewriteFilter JAR to PROJECT directory's WEB-INF/lib
        // CRITICAL: When docBase points to the project directory, Tomcat's classloader
        // loads JARs from docBase/WEB-INF/lib, NOT from CATALINA_BASE/webapps/ROOT/WEB-INF/lib
        // Only deploy when Lucee and URL rewrite are both enabled.
        if (config.enableLucee && config.urlRewrite != null && config.urlRewrite.enabled) {
            try {
                Path projectWebInfLib = webrootWebInf.resolve("lib");
                Files.createDirectories(projectWebInfLib);
                
                Path urlRewriteJar = ensureUrlRewriteFilter();
                Path targetJar = projectWebInfLib.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
                if (overwriteProjectConfig || !Files.exists(targetJar)) {
                    Files.copy(urlRewriteJar, targetJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    System.out.println("Preserving existing UrlRewriteFilter JAR at " + targetJar.toAbsolutePath());
                }
                
                System.out.println("Deployed UrlRewriteFilter to project docBase:");
                System.out.println("  JAR: " + targetJar.toAbsolutePath());
                System.out.println("  web.xml: " + projectWebXml.toAbsolutePath());
                System.out.println("  urlrewrite.xml: " + webrootWebInf.resolve("urlrewrite.xml").toAbsolutePath());
            } catch (Exception e) {
                throw new IOException("Failed to deploy UrlRewriteFilter JAR: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Create a map of placeholder values for template processing
     */
    private Map<String, String> createPlaceholderMap(Path serverInstanceDir, LuceeServerConfig.ServerConfig config,
                                                   Path projectDir) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Calculate paths
        Path webroot = LuceeServerConfig.resolveWebroot(config, projectDir);
        Path luceeServerPath = serverInstanceDir.resolve("lucee-server");
        Path luceeWebPath = serverInstanceDir.resolve("lucee-web");
        Path luceePatchesPath = getLucliHome().resolve("patches");
        int shutdownPort = LuceeServerConfig.getEffectiveShutdownPort(config);
        
        // Add all placeholder values
        placeholders.put("${httpPort}", String.valueOf(config.port));
        placeholders.put("${shutdownPort}", String.valueOf(shutdownPort));
        placeholders.put("${jmxPort}", String.valueOf(config.monitoring.jmx.port));
        placeholders.put("${webroot}", webroot.toAbsolutePath().toString());
        placeholders.put("${luceeServerPath}", luceeServerPath.toAbsolutePath().toString());
        placeholders.put("${luceeWebPath}", luceeWebPath.toAbsolutePath().toString());
        placeholders.put("${luceePatches}", luceePatchesPath.toAbsolutePath().toString());
        placeholders.put("${jvmRoute}", config.name);
        placeholders.put("${logLevel}", "INFO");
        
        // Add URL rewrite configuration placeholders
        String routerFile = (config.urlRewrite != null && config.urlRewrite.routerFile != null)
            ? config.urlRewrite.routerFile
            : "index.cfm";
        placeholders.put("${routerFile}", routerFile);
        
        return placeholders;
    }
    
    /**
     * Apply a template file by loading it from resources and replacing placeholders
     */
    private void applyTemplate(String templateResourcePath, Path outputPath, Map<String, String> placeholders) throws IOException {
        // Load template from resources
        String templateContent;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + templateResourcePath);
            }
            templateContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        // Replace placeholders
        String processedContent = templateContent;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processedContent = processedContent.replace(entry.getKey(), entry.getValue());
        }
        
        // Ensure output directory exists
        Files.createDirectories(outputPath.getParent());
        
        // Write processed content to output file
        Files.writeString(outputPath, processedContent, StandardCharsets.UTF_8);
    }
    
    /**
     * Generate web.xml content as a string without writing to disk.
     * Used for dry-run preview. Loads from Lucee Express distribution and applies XML patching.
     */
    public String generateWebXmlContent(LuceeServerConfig.ServerConfig config, Path projectDir, 
                                       Path serverInstanceDir, Path luceeExpressDir) throws IOException {
        // Find web.xml from Lucee Express distribution
        // Try webapps/ROOT/WEB-INF/web.xml first, then conf/web.xml
        Path vendorWebXml = luceeExpressDir.resolve("webapps/ROOT/WEB-INF/web.xml");
        if (!Files.exists(vendorWebXml)) {
            vendorWebXml = luceeExpressDir.resolve("conf/web.xml");
        }
        if (!Files.exists(vendorWebXml)) {
            throw new IOException("web.xml not found in Lucee Express distribution at " + luceeExpressDir);
        }
        
        // Load web.xml from Lucee Express
        String webXmlContent = Files.readString(vendorWebXml, StandardCharsets.UTF_8);
        
        // Apply XML patching using TomcatWebXmlPatcher logic
        webXmlContent = applyWebXmlPatching(webXmlContent, config, projectDir, serverInstanceDir);
        
        return webXmlContent;
    }
    
    /**
     * Apply web.xml patching via XML DOM manipulation.
     * Uses the same logic as TomcatWebXmlPatcher.
     */
    private String applyWebXmlPatching(String content, LuceeServerConfig.ServerConfig config, 
                                       Path projectDir, Path serverInstanceDir) throws IOException {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            
            // Apply patching logic
            if (!config.enableLucee) {
                disableLuceeEngineInDocument(document);
                disableRestServletFromDocument(document);
            } else if (!config.enableREST) {
                disableRestServletFromDocument(document);
            }
            
            // Convert back to string
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
            
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            transformer.transform(new javax.xml.transform.dom.DOMSource(document), 
                                new javax.xml.transform.stream.StreamResult(output));
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Log error if XML patching fails
            System.err.println("Warning: Failed to patch web.xml in preview: " + e.getMessage());
            e.printStackTrace();
            return content;
        }
    }
    
    /**
     * Disable Lucee CFML engine from the DOM document.
     * Mirrors the logic in TomcatWebXmlPatcher.disableLuceeEngine().
     */
    private void disableLuceeEngineInDocument(org.w3c.dom.Document document) {
        if (document == null) {
            return;
        }
        
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        
        String namespace = root.getNamespaceURI();
        
        // 1) Remove Lucee CFML servlets (but not REST) and remember their servlet-names
        java.util.Set<String> luceeServletNames = new java.util.HashSet<>();
        org.w3c.dom.NodeList servletNodes = (namespace != null) ? 
            root.getElementsByTagNameNS(namespace, "servlet") : 
            root.getElementsByTagName("servlet");
        for (int i = 0; i < servletNodes.getLength(); i++) {
            org.w3c.dom.Node node = servletNodes.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element servlet = (org.w3c.dom.Element) node;
            
            String servletName = getChildTextFromDocument(servlet, "servlet-name", namespace);
            String servletClass = getChildTextFromDocument(servlet, "servlet-class", namespace);
            
            // Only remove CFML servlet, not REST servlet
            boolean isLuceeCfmlServlet = 
                (servletClass != null && servletClass.startsWith("lucee.loader.servlet")) || 
                "CFMLServlet".equals(servletName);
            
            if (isLuceeCfmlServlet) {
                if (servletName != null) {
                    luceeServletNames.add(servletName);
                }
                root.removeChild(servlet);
                i--;
            }
        }
        
        // 2) Remove servlet-mappings for Lucee CFML servlets and CFML/admin URL patterns
        org.w3c.dom.NodeList mappingNodes = (namespace != null) ?
            root.getElementsByTagNameNS(namespace, "servlet-mapping") :
            root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            org.w3c.dom.Node node = mappingNodes.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element mapping = (org.w3c.dom.Element) node;
            
            String name = getChildTextFromDocument(mapping, "servlet-name", namespace);
            String pattern = getChildTextFromDocument(mapping, "url-pattern", namespace);
            
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
                i--;
            }
        }
        
        // 3) Remove CFML welcome files
        org.w3c.dom.NodeList welcomeLists = (namespace != null) ?
            root.getElementsByTagNameNS(namespace, "welcome-file-list") :
            root.getElementsByTagName("welcome-file-list");
        for (int i = 0; i < welcomeLists.getLength(); i++) {
            org.w3c.dom.Node listNode = welcomeLists.item(i);
            if (!(listNode instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element list = (org.w3c.dom.Element) listNode;
            
            org.w3c.dom.NodeList welcomeFiles = (namespace != null) ?
                list.getElementsByTagNameNS(namespace, "welcome-file") :
                list.getElementsByTagName("welcome-file");
            java.util.List<org.w3c.dom.Element> toRemove = new java.util.ArrayList<>();
            for (int j = 0; j < welcomeFiles.getLength(); j++) {
                org.w3c.dom.Node wfNode = welcomeFiles.item(j);
                if (!(wfNode instanceof org.w3c.dom.Element)) {
                    continue;
                }
                org.w3c.dom.Element wf = (org.w3c.dom.Element) wfNode;
                String name = wf.getTextContent() != null ? wf.getTextContent().trim() : "";
                if (name.endsWith(".cfm") || name.endsWith(".cfml")) {
                    toRemove.add(wf);
                }
            }
            
            for (org.w3c.dom.Element wf : toRemove) {
                list.removeChild(wf);
            }
        }
    }
    
    /**
     * Disable REST servlet and its mappings from the DOM document.
     * Mirrors the logic in TomcatWebXmlPatcher.disableRestServlet().
     */
    private void disableRestServletFromDocument(org.w3c.dom.Document document) {
        if (document == null) {
            return;
        }
        
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        
        // Get the namespace (could be null or a specific namespace like jakarta.ee)
        String namespace = root.getNamespaceURI();
        
        // 1) Remove REST servlet and remember its servlet-name
        java.util.Set<String> restServletNames = new java.util.HashSet<>();
        org.w3c.dom.NodeList servletNodes = (namespace != null) ? 
            root.getElementsByTagNameNS(namespace, "servlet") : 
            root.getElementsByTagName("servlet");
        for (int i = 0; i < servletNodes.getLength(); i++) {
            org.w3c.dom.Node node = servletNodes.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element servlet = (org.w3c.dom.Element) node;
            
            String servletName = getChildTextFromDocument(servlet, "servlet-name", namespace);
            
            if ("restservlet".equals(servletName != null ? servletName.toLowerCase() : "")) {
                restServletNames.add(servletName);
                root.removeChild(servlet);
                i--;
            }
        }
        
        // 2) Remove servlet-mappings for REST servlet and /rest/* URL patterns
        org.w3c.dom.NodeList mappingNodes = (namespace != null) ?
            root.getElementsByTagNameNS(namespace, "servlet-mapping") :
            root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            org.w3c.dom.Node node = mappingNodes.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element mapping = (org.w3c.dom.Element) node;
            
            String name = getChildTextFromDocument(mapping, "servlet-name", namespace);
            String pattern = getChildTextFromDocument(mapping, "url-pattern", namespace);
            
            boolean isRestPattern = pattern != null && pattern.startsWith("/rest/");
            
            if ((name != null && restServletNames.contains(name)) || isRestPattern) {
                root.removeChild(mapping);
                i--;
            }
        }
    }
    
    /**
     * Helper to read text content of first direct child element with namespace support.
     */
    private String getChildTextFromDocument(org.w3c.dom.Element parent, String childLocalName, String namespace) {
        if (parent == null) {
            return null;
        }
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (!(node instanceof org.w3c.dom.Element)) {
                continue;
            }
            org.w3c.dom.Element el = (org.w3c.dom.Element) node;
            
            // Check both with and without namespace
            if (namespace != null) {
                if (childLocalName.equals(el.getLocalName())) {
                    return el.getTextContent();
                }
            } else {
                String nodeName = el.getNodeName();
                if (childLocalName.equals(nodeName) || nodeName.endsWith(":" + childLocalName)) {
                    return el.getTextContent();
                }
            }
        }
        return null;
    }
    
    /**
     * Helper to read text content of first direct child element (no namespace).
     */
    private String getChildTextFromDocument(org.w3c.dom.Element parent, String childLocalName) {
        return getChildTextFromDocument(parent, childLocalName, null);
    }
    
    /**
     * Generate server.xml content as a string without writing to disk.
     * Used for dry-run preview. Loads from Lucee Express distribution.
     */
    public String generateServerXmlContent(LuceeServerConfig.ServerConfig config, 
                                          Path serverInstanceDir, Path luceeExpressDir) throws IOException {
        // Find server.xml from Lucee Express distribution
        Path vendorServerXml = luceeExpressDir.resolve("conf/server.xml");
        if (!Files.exists(vendorServerXml)) {
            throw new IOException("server.xml not found in Lucee Express distribution at " + luceeExpressDir);
        }
        
        // Load server.xml from Lucee Express
        return Files.readString(vendorServerXml, StandardCharsets.UTF_8);
    }

    /**
     * Generate the patched server.xml content (what LuCLI would write into the
     * server instance), without any filesystem side effects.
     *
     * This is intended for `lucli server start --dry-run --include-tomcat-server`.
     */
    public String generatePatchedServerXmlContent(LuceeServerConfig.ServerConfig config,
                                                 Path projectDir,
                                                 Path serverInstanceDir,
                                                 Path luceeExpressDir) throws IOException {
        String vendor = generateServerXmlContent(config, serverInstanceDir, luceeExpressDir);
        // Side-effect-free patching: do not create keystores or rewrite.config in dry-run.
        return serverXmlPatcher.patchContent(vendor, config, projectDir, serverInstanceDir, false);
    }
    
    /**
     * Copy the entire Lucee Express distribution to the server instance directory
     */
    private void copyLuceeExpressDistribution(Path luceeExpressDir, Path serverInstanceDir) throws IOException {
        // Recursively copy entire Lucee Express directory to server instance
        copyDirectoryRecursively(luceeExpressDir, serverInstanceDir);
    }
    
    /**
     * Recursively copy a directory and all its contents
     */
    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath + " to " + target, e);
            }
        });
    }
    
    /**
     * Download and deploy the UrlRewriteFilter JAR (now handled in applyConfigurationTemplates)
     */
    private void deployUrlRewriteFilter(Path serverInstanceDir) throws Exception {
        // This method is now deprecated - JAR deployment moved to applyConfigurationTemplates
        // Kept for backward compatibility but does nothing
    }
    
    /**
     * Ensure the UrlRewriteFilter JAR is available, downloading if necessary
     */
    private Path ensureUrlRewriteFilter() throws Exception {
        // Get LuCLI home directory
        Path lucliHome = getLucliHome();
        Path dependenciesDir = lucliHome.resolve("dependencies");
        Files.createDirectories(dependenciesDir);
        
        // Check if JAR already exists
        Path jarFile = dependenciesDir.resolve("urlrewritefilter-" + URLREWRITE_VERSION + ".jar");
        if (Files.exists(jarFile)) {
            return jarFile;
        }
        
        // Download the JAR
        System.out.println("Downloading UrlRewriteFilter " + URLREWRITE_VERSION + "...");
        downloadFile(URLREWRITE_MAVEN_URL, jarFile);
        System.out.println("UrlRewriteFilter downloaded successfully.");
        
        return jarFile;
    }
    
    /**
     * Get the LuCLI home directory
     */
    private static Path getLucliHome() {
        String lucliHomeStr = System.getProperty("lucli.home");
        if (lucliHomeStr == null) {
            lucliHomeStr = System.getenv("LUCLI_HOME");
        }
        if (lucliHomeStr == null) {
            String userHome = System.getProperty("user.home");
            lucliHomeStr = Paths.get(userHome, ".lucli").toString();
        }
        return Paths.get(lucliHomeStr);
    }
    
    /**
     * Download a file from URL
     */
    private void downloadFile(String urlString, Path destinationFile) throws IOException {
        Files.createDirectories(destinationFile.getParent());
        
        URL url = new URL(urlString);
        try (InputStream in = url.openStream();
             OutputStream out = Files.newOutputStream(destinationFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Write Tomcat setenv scripts (setenv.sh / setenv.bat) into the server
     * instance's bin directory so that JVM options derived from lucee.json are
     * available when the vendor startup scripts are invoked directly (e.g. in
     * ZIP/Docker packaging).
     *
     * The scripts are defensive: they only define CATALINA_OPTS when it is not
     * already set, so that LuCLI can continue to override CATALINA_OPTS via the
     * ProcessBuilder environment when starting servers programmatically.
     */
    static void writeSetenvScripts(Path serverInstanceDir,
                                   LuceeServerConfig.ServerConfig config,
                                   Path projectDir) throws IOException {
        Path binDir = serverInstanceDir.resolve("bin");
        if (!Files.exists(binDir)) {
            // Unexpected, but don't fail server creation if bin does not exist.
            return;
        }

        // Reuse LuceeServerManager's JVM option assembly logic to ensure the
        // persisted options exactly match what LuCLI would use at runtime when
        // no AgentOverrides are applied.
        List<String> opts;
        try {
            LuceeServerManager manager = new LuceeServerManager();
            opts = manager.buildCatalinaOpts(config, null, projectDir);
        } catch (Exception e) {
            throw new IOException("Failed to build JVM options for setenv scripts: " + e.getMessage(), e);
        }

        if (opts == null || opts.isEmpty()) {
            return; // Nothing to persist
        }

        String joinedOpts = String.join(" ", opts);

        // --- setenv.sh ---
        Path setenvSh = binDir.resolve("setenv.sh");
        String escapedShOpts = joinedOpts.replace("\"", "\\\"");
        StringBuilder sh = new StringBuilder();
        sh.append("#!/bin/sh\n");
        sh.append("# Auto-generated by LuCLI from lucee.json. Manual changes may be overwritten.\n");
        sh.append("BASE_CATALINA_OPTS=\"").append(escapedShOpts).append("\"\n");
        sh.append("if [ -z \"$CATALINA_OPTS\" ]; then\n");
        sh.append("  CATALINA_OPTS=\"$BASE_CATALINA_OPTS\"\n");
        sh.append("fi\n");
        Files.writeString(setenvSh, sh.toString(), StandardCharsets.UTF_8);
        // Best-effort: mark the script as executable on POSIX systems.
        setenvSh.toFile().setExecutable(true);

        // --- setenv.bat ---
        Path setenvBat = binDir.resolve("setenv.bat");
        String escapedBatOpts = joinedOpts.replace("\"", "\\\"");
        StringBuilder bat = new StringBuilder();
        bat.append("@echo off\r\n");
        bat.append("REM Auto-generated by LuCLI from lucee.json. Manual changes may be overwritten.\r\n");
        bat.append("IF NOT DEFINED CATALINA_OPTS (\r\n");
        bat.append("  set \"CATALINA_OPTS=").append(escapedBatOpts).append("\"\r\n");
        bat.append(")\r\n");
        Files.writeString(setenvBat, bat.toString(), StandardCharsets.UTF_8);
    }
    
}
