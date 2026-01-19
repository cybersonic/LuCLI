package org.lucee.lucli.server;

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Small collection of DOM/XPath helpers used by the XML patchers and
 * debugging commands.
 *
 * <p>These utilities are intentionally minimal and do not attempt to provide a
 * full XML abstraction layer; they just wrap the most common patterns used
 * when patching configuration files so that the patcher classes remain
 * focused on the <em>rules</em> rather than DOM boilerplate.</p>
 */
public final class XmlHelper {

    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    private XmlHelper() {
        // Utility class
    }

    /**
     * Returns {@code true} if the given XPath expression selects at least one
     * node from the provided document.
     */
    public static boolean exists(Document document, String xpathExpression) throws XPathExpressionException {
        if (document == null || xpathExpression == null || xpathExpression.isEmpty()) {
            return false;
        }
        XPath xpath = XPATH_FACTORY.newXPath();
        Node node = (Node) xpath.evaluate(xpathExpression, document, XPathConstants.NODE);
        return node != null;
    }

    /**
     * Returns all {@link Element} nodes selected by the given XPath expression.
     */
    public static List<Element> find(Document document, String xpathExpression) throws XPathExpressionException {
        List<Element> result = new ArrayList<>();
        if (document == null || xpathExpression == null || xpathExpression.isEmpty()) {
            return result;
        }
        XPath xpath = XPATH_FACTORY.newXPath();
        NodeList nodes = (NodeList) xpath.evaluate(xpathExpression, document, XPathConstants.NODESET);
        if (nodes == null) {
            return result;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /**
     * Removes all elements selected by the given XPath expression from their
     * parent nodes.
     */
    public static void removeAll(Document document, String xpathExpression) throws XPathExpressionException {
        for (Element element : find(document, xpathExpression)) {
            Node parent = element.getParentNode();
            if (parent != null) {
                parent.removeChild(element);
            }
        }
    }

    /**
     * Set or replace an attribute on all elements selected by the given XPath
     * expression.
     *
     * @param document        the source document
     * @param xpathExpression XPath selecting the target elements
     * @param attributeName   attribute to set
     * @param value           value to set
     * @return number of elements that were updated
     */
    public static int setAttribute(Document document,
                                   String xpathExpression,
                                   String attributeName,
                                   String value) throws XPathExpressionException {
        if (document == null || attributeName == null || attributeName.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Element element : find(document, xpathExpression)) {
            element.setAttribute(attributeName, value);
            count++;
        }
        return count;
    }

    /**
     * Convenience for creating and appending a child element with optional
     * text content.
     *
     * @param parent      parent element (must not be {@code null})
     * @param name        element name
     * @param textContent optional text content (may be {@code null})
     * @return the created child element, or {@code null} if the parent is null
     */
    public static Element append(Element parent, String name, String textContent) {
        if (parent == null || name == null || name.isEmpty()) {
            return null;
        }
        Document document = parent.getOwnerDocument();
        Element element = document.createElement(name);
        if (textContent != null) {
            element.setTextContent(textContent);
        }
        parent.appendChild(element);
        return element;
    }
}
