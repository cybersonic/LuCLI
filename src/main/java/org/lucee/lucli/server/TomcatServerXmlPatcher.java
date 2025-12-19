package org.lucee.lucli.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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

        String content = Files.readString(serverXmlPath, StandardCharsets.UTF_8);
        String patched = patchContent(content, config, projectDir, serverInstanceDir, true);
        Files.writeString(serverXmlPath, patched, StandardCharsets.UTF_8);
    }

    /**
     * Patch server.xml content in-memory.
     *
     * @param writeFiles when true, allows the patcher to create/update derived files
     *                   under the server instance directory (keystore, rewrite.config).
     *                   When false, this method is side-effect-free and suitable for --dry-run.
     */
    public String patchContent(String serverXmlContent,
                               LuceeServerConfig.ServerConfig config,
                               Path projectDir,
                               Path serverInstanceDir,
                               boolean writeFiles) throws IOException {
        if (serverXmlContent == null) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new java.io.ByteArrayInputStream(serverXmlContent.getBytes(StandardCharsets.UTF_8)));

            // Apply config-driven mutations.
            applyHttpConnectorPort(document, config);
            applyShutdownPort(document, config);
            applyRootContext(document, config, projectDir, serverInstanceDir);
            applyHttpsConfiguration(document, config, serverInstanceDir, writeFiles);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toString(StandardCharsets.UTF_8);
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

    private static class KeystoreInfo {
        final Path keystorePath;
        final String password;

        KeystoreInfo(Path keystorePath, String password) {
            this.keystorePath = keystorePath;
            this.password = password;
        }
    }

    private void applyHttpsConfiguration(Document document,
                                         LuceeServerConfig.ServerConfig config,
                                         Path serverInstanceDir,
                                         boolean writeFiles) throws IOException {
        if (document == null || config == null) {
            return;
        }

        if (!LuceeServerConfig.isHttpsEnabled(config)) {
            return;
        }

        // Ensure keystore exists (per server instance) when writing files.
        KeystoreInfo keystore = ensurePerServerKeystore(serverInstanceDir, config, writeFiles);

        // Add/update HTTPS connector.
        applyHttpsConnector(document, config, keystore);

        // Optional redirect HTTP -> HTTPS.
        if (LuceeServerConfig.isHttpsRedirectEnabled(config)) {
            applyHttpToHttpsRedirect(document, config, serverInstanceDir, writeFiles);
        }
    }

    private KeystoreInfo ensurePerServerKeystore(Path serverInstanceDir,
                                                  LuceeServerConfig.ServerConfig config,
                                                  boolean writeFiles) throws IOException {
        // In dry-run/preview mode, never touch the filesystem. Just describe what would be used.
        if (!writeFiles || serverInstanceDir == null) {
            Path plannedKeystore = (serverInstanceDir != null)
                ? serverInstanceDir.resolve("certs").resolve("keystore.p12")
                : Path.of("certs").resolve("keystore.p12");
            return new KeystoreInfo(plannedKeystore, "<stored in certs/keystore.pass>");
        }

        Path certsDir = serverInstanceDir.resolve("certs");
        Files.createDirectories(certsDir);

        Path passFile = certsDir.resolve("keystore.pass");
        Path keystoreFile = certsDir.resolve("keystore.p12");

        String password;
        if (Files.exists(passFile)) {
            password = Files.readString(passFile, StandardCharsets.UTF_8).trim();
        } else {
            password = generatePassword();
            Files.writeString(passFile, password + "\n", StandardCharsets.UTF_8);
            tightenPermissions(passFile);
        }

        if (!Files.exists(keystoreFile)) {
            generateSelfSignedKeystore(keystoreFile, password, config);
            tightenPermissions(keystoreFile);
        }

        return new KeystoreInfo(keystoreFile, password);
    }

    private String generatePassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        // URL-safe base64 without padding keeps it easy to store/handle.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void tightenPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            // Non-POSIX FS or permissions not supported; best-effort only.
        }
    }

    private void generateSelfSignedKeystore(Path keystorePath, String password, LuceeServerConfig.ServerConfig config) throws IOException {
        String host = (config.host == null || config.host.trim().isEmpty()) ? "localhost" : config.host.trim();
        String alias = "lucli";
        String dname = "CN=" + host;

        // Include SANs for localhost and the configured host.
        String san = "dns:localhost";
        if (!"localhost".equalsIgnoreCase(host)) {
            san += ",dns:" + host;
        }
        san += ",ip:127.0.0.1";

        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("keytool");
        cmd.add("-genkeypair");
        cmd.add("-alias");
        cmd.add(alias);
        cmd.add("-keyalg");
        cmd.add("RSA");
        cmd.add("-keysize");
        cmd.add("2048");
        cmd.add("-validity");
        cmd.add("825");
        cmd.add("-storetype");
        cmd.add("PKCS12");
        cmd.add("-keystore");
        cmd.add(keystorePath.toAbsolutePath().toString());
        cmd.add("-storepass");
        cmd.add(password);
        cmd.add("-dname");
        cmd.add(dname);
        cmd.add("-ext");
        cmd.add("SAN=" + san);

        try {
            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("keytool failed (exit " + exit + "): " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("keytool interrupted while generating keystore", e);
        } catch (IOException e) {
            // Provide a slightly more actionable error message.
            throw new IOException("Failed to generate self-signed HTTPS certificate. Ensure 'keytool' is available on PATH. " + e.getMessage(), e);
        }
    }

    private void applyHttpsConnector(Document document, LuceeServerConfig.ServerConfig config, KeystoreInfo keystore) {
        if (document == null || config == null || keystore == null) {
            return;
        }

        int httpsPort = LuceeServerConfig.getEffectiveHttpsPort(config);

        // Find the first <Service> element (usually name="Catalina").
        NodeList services = document.getElementsByTagName("Service");
        if (services == null || services.getLength() == 0) {
            return;
        }

        Element service = null;
        for (int i = 0; i < services.getLength(); i++) {
            if (services.item(i) instanceof Element) {
                service = (Element) services.item(i);
                break;
            }
        }
        if (service == null) {
            return;
        }

        // Try to find an existing HTTPS connector.
        NodeList connectors = service.getElementsByTagName("Connector");
        Element httpsConnector = null;
        if (connectors != null) {
            for (int i = 0; i < connectors.getLength(); i++) {
                if (!(connectors.item(i) instanceof Element)) {
                    continue;
                }
                Element c = (Element) connectors.item(i);
                String scheme = c.getAttribute("scheme");
                String sslEnabled = c.getAttribute("SSLEnabled");
                if ("https".equalsIgnoreCase(scheme) || "true".equalsIgnoreCase(sslEnabled)) {
                    httpsConnector = c;
                    break;
                }
            }
        }

        if (httpsConnector == null) {
            httpsConnector = document.createElement("Connector");
            
            // Insert after the last existing Connector element
            // (connectors must come before the Engine in server.xml)
            Element lastConnector = null;
            if (connectors != null && connectors.getLength() > 0) {
                // Find the last Connector element
                for (int i = connectors.getLength() - 1; i >= 0; i--) {
                    if (connectors.item(i) instanceof Element) {
                        lastConnector = (Element) connectors.item(i);
                        break;
                    }
                }
            }
            
            if (lastConnector != null) {
                // Insert after the last connector
                Node nextSibling = lastConnector.getNextSibling();
                if (nextSibling != null) {
                    service.insertBefore(httpsConnector, nextSibling);
                } else {
                    // No next sibling, append to service
                    service.appendChild(httpsConnector);
                }
            } else {
                // No existing connectors, find Engine and insert before it
                NodeList engines = service.getElementsByTagName("Engine");
                Element engine = null;
                if (engines != null && engines.getLength() > 0) {
                    for (int i = 0; i < engines.getLength(); i++) {
                        if (engines.item(i) instanceof Element) {
                            engine = (Element) engines.item(i);
                            break;
                        }
                    }
                }
                if (engine != null) {
                    service.insertBefore(httpsConnector, engine);
                } else {
                    // Last resort: append to service
                    service.appendChild(httpsConnector);
                }
            }
        }

        httpsConnector.setAttribute("protocol", "org.apache.coyote.http11.Http11NioProtocol");
        httpsConnector.setAttribute("port", String.valueOf(httpsPort));
        httpsConnector.setAttribute("scheme", "https");
        httpsConnector.setAttribute("secure", "true");
        httpsConnector.setAttribute("SSLEnabled", "true");

        // Modern Tomcat requires SSLHostConfig nested element
        // Remove any existing SSLHostConfig elements first
        NodeList existingSSLConfigs = httpsConnector.getElementsByTagName("SSLHostConfig");
        for (int i = existingSSLConfigs.getLength() - 1; i >= 0; i--) {
            httpsConnector.removeChild(existingSSLConfigs.item(i));
        }

        // Create SSLHostConfig element
        Element sslHostConfig = document.createElement("SSLHostConfig");
        sslHostConfig.setAttribute("hostName", "_default_");
        sslHostConfig.setAttribute("protocols", "TLSv1.2,TLSv1.3");

        // Create Certificate nested element
        Element certificate = document.createElement("Certificate");
        certificate.setAttribute("certificateKeystoreFile", keystore.keystorePath.toAbsolutePath().toString());
        certificate.setAttribute("certificateKeystorePassword", keystore.password);
        certificate.setAttribute("certificateKeystoreType", "PKCS12");
        certificate.setAttribute("certificateKeyAlias", "lucli");
        certificate.setAttribute("type", "RSA");

        sslHostConfig.appendChild(certificate);
        httpsConnector.appendChild(sslHostConfig);
    }

    private void applyHttpToHttpsRedirect(Document document,
                                          LuceeServerConfig.ServerConfig config,
                                          Path serverInstanceDir,
                                          boolean writeFiles) throws IOException {
        if (document == null || config == null) {
            return;
        }

        // Reuse the same host selection logic as applyRootContext.
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
                targetHost = host;
            }
        }

        if (targetHost == null) {
            return;
        }

        // Add RewriteValve if missing.
        boolean hasRewriteValve = false;
        NodeList valves = targetHost.getElementsByTagName("Valve");
        if (valves != null) {
            for (int i = 0; i < valves.getLength(); i++) {
                if (!(valves.item(i) instanceof Element)) {
                    continue;
                }
                Element valve = (Element) valves.item(i);
                String className = valve.getAttribute("className");
                if ("org.apache.catalina.valves.rewrite.RewriteValve".equals(className)) {
                    hasRewriteValve = true;
                    break;
                }
            }
        }

        if (!hasRewriteValve) {
            Element valve = document.createElement("Valve");
            valve.setAttribute("className", "org.apache.catalina.valves.rewrite.RewriteValve");
            targetHost.appendChild(valve);
        }

        // Write rewrite.config to Tomcat's expected location:
        // conf/Catalina/<hostName>/rewrite.config
        String hostName = targetHost.getAttribute("name");
        if (hostName == null || hostName.trim().isEmpty()) {
            hostName = (defaultHostName != null && !defaultHostName.isEmpty()) ? defaultHostName : "localhost";
        }

        if (!writeFiles || serverInstanceDir == null) {
            // Dry-run: only ensure the RewriteValve exists in server.xml.
            return;
        }

        Path rewriteDir = serverInstanceDir.resolve("conf").resolve("Catalina").resolve(hostName);
        Files.createDirectories(rewriteDir);

        String redirectHost = LuceeServerConfig.getEffectiveHost(config);
        int httpsPort = LuceeServerConfig.getEffectiveHttpsPort(config);

        String rules = "RewriteCond %{HTTPS} !=on\n" +
                       "RewriteRule ^/(.*)$ https://" + redirectHost + ":" + httpsPort + "/$1 [R=302,L]\n";

        Files.writeString(rewriteDir.resolve("rewrite.config"), rules, StandardCharsets.UTF_8);
    }
}
