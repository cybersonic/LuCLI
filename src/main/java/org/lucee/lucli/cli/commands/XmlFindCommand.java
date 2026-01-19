package org.lucee.lucli.cli.commands;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import org.lucee.lucli.server.XmlHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Evaluate an XPath expression and print basic information about matched elements.
 *
 * <p>This is mainly intended for debugging and exploring vendor server.xml/web.xml
 * structures while developing patch rules.</p>
 */
@Command(name = "find", description = "Find elements matching an XPath expression and print basic info")
public class XmlFindCommand implements Callable<Integer> {

    @Option(names = "--input", required = true,
            description = "Input XML file")
    Path input;

    @Parameters(index = "0", paramLabel = "EXPR",
                description = "XPath expression to evaluate")
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

        List<Element> elements;
        try {
            elements = XmlHelper.find(document, expression);
        } catch (XPathExpressionException e) {
            System.err.println("Invalid XPath expression: " + e.getMessage());
            return 2;
        }

        if (elements.isEmpty()) {
            System.err.println("Matched 0 element(s)");
            return 1;
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        int index = 0;
        for (Element el : elements) {
            if (index > 0) {
                System.out.println();
                System.out.println("---");
                System.out.println();
            }
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(el), new StreamResult(writer));
            System.out.println(writer.toString().trim());
            index++;
        }

        System.err.printf("Matched %d element(s)%n", elements.size());
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
}
