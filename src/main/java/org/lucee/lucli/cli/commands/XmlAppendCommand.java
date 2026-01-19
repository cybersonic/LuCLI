package org.lucee.lucli.cli.commands;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.lucee.lucli.server.XmlHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Append a child element under the first node matching a parent XPath.
 *
 * <p>This is intentionally simple and mainly used for exploring how DOM
 * mutations behave on real Tomcat configuration files.</p>
 */
@Command(name = "append", description = "Append a child element under the first node matching a parent XPath")
public class XmlAppendCommand implements Callable<Integer> {

    @Option(names = "--input", required = true,
            description = "Input XML file")
    Path input;

    @Option(names = "--output", required = true,
            description = "Output XML file (will be created or overwritten)")
    Path output;

    @Option(names = "--parent-xpath", required = true,
            description = "XPath selecting the parent element")
    String parentXPath;

    @Option(names = "--name", required = true,
            description = "Name of the child element to append")
    String name;

    @Option(names = "--text",
            description = "Optional text content for the child element")
    String text;

    @Override
    public Integer call() throws Exception {
        Document document;
        try {
            document = parse(input);
        } catch (Exception e) {
            System.err.println("Failed to parse XML: " + e.getMessage());
            return 2;
        }

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        Node parent = (Node) xpath.evaluate(parentXPath, document, XPathConstants.NODE);
        if (!(parent instanceof Element)) {
            System.err.println("Parent XPath did not resolve to an element");
            return 1;
        }

        XmlHelper.append((Element) parent, name, text);

        try {
            write(document, output);
        } catch (Exception e) {
            System.err.println("Failed to write XML: " + e.getMessage());
            return 2;
        }

        return 0;
    }

    private static Document parse(Path input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(input)) {
            return builder.parse(in);
        }
    }

    private static void write(Document document, Path output) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        try (OutputStream out = Files.newOutputStream(output)) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }
}
