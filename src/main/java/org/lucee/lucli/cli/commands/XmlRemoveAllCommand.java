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
 * Remove all elements matching an XPath expression and write the updated XML
 * to an output file.
 */
@Command(name = "removeAll", description = "Remove all elements matching an XPath expression and write updated XML")
public class XmlRemoveAllCommand implements Callable<Integer> {

    @Option(names = "--input", required = true,
            description = "Input XML file")
    Path input;

    @Option(names = "--output", required = true,
            description = "Output XML file (will be created or overwritten)")
    Path output;

    @Parameters(index = "0", paramLabel = "EXPR",
                description = "XPath expression selecting elements to remove")
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

        try {
            XmlHelper.removeAll(document, expression);
        } catch (XPathExpressionException e) {
            System.err.println("Invalid XPath expression: " + e.getMessage());
            return 2;
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
