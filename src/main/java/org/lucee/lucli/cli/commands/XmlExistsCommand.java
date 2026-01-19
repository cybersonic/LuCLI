package org.lucee.lucli.cli.commands;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;

import org.lucee.lucli.server.XmlHelper;
import org.w3c.dom.Document;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Check whether an XPath expression matches any node in an XML document.
 *
 * <p>Exit code is 0 if the expression matches at least one node, 1 otherwise,
 * and 2 for an invalid XPath expression or parse error.</p>
 */
@Command(name = "exists", description = "Check whether an XPath expression matches any node in an XML document")
public class XmlExistsCommand implements Callable<Integer> {

    @Option(names = "--input", required = true,
            description = "Input XML file (e.g. server.xml or web.xml)")
    Path input;

    @Option(names = "--quiet",
            description = "Do not print true/false, only use exit code")
    boolean quiet;

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

        boolean result;
        try {
            result = XmlHelper.exists(document, expression);
        } catch (XPathExpressionException e) {
            System.err.println("Invalid XPath expression: " + e.getMessage());
            return 2;
        }

        if (!quiet) {
            System.out.println(result);
        }

        // 0 when expression matches, 1 when it does not.
        return result ? 0 : 1;
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
