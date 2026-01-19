package org.lucee.lucli.cli.commands;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.lucee.lucli.server.XmlHelper;
import org.w3c.dom.Document;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Set or replace an attribute value on all elements matched by an XPath expression.
 *
 * <p>This is particularly useful for experimenting with port changes on Connector
 * elements, or toggling flags, without going through the full server patch flow.</p>
 */
@Command(name = "set-attr", description = "Set or replace an attribute on elements matching an XPath expression")
public class XmlSetAttributeCommand implements Callable<Integer> {

    @Option(names = "--input", required = true,
            description = "Input XML file")
    Path input;

    @Option(names = "--output", required = true,
            description = "Output XML file (will be created or overwritten)")
    Path output;

    @Option(names = "--name", required = true,
            description = "Attribute name to set or replace")
    String attributeName;

    @Option(names = "--value", required = true,
            description = "Attribute value to set")
    String value;

    @Parameters(index = "0", paramLabel = "EXPR",
                description = "XPath expression selecting the elements to update")
    String expression;

    @Override
    public Integer call() throws Exception {
        Document document;
        try {
            document = parse(input);
        } catch (Exception e) {
            System.err.println("Failed to parse XML: " + e.getMessage());
            return 2;
        }

        int updated;
        try {
            updated = XmlHelper.setAttribute(document, expression, attributeName, value);
        } catch (XPathExpressionException e) {
            System.err.println("Invalid XPath expression: " + e.getMessage());
            return 2;
        }

        if (updated == 0) {
            System.err.println("Warning: no elements matched expression; no attributes updated");
        } else {
            System.err.println("Updated attribute '" + attributeName + "' on " + updated + " element(s)");
        }

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
