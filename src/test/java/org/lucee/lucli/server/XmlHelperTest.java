package org.lucee.lucli.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlHelper class.
 * Tests XML manipulation utilities.
 */
class XmlHelperTest {

    private DocumentBuilder documentBuilder;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        documentBuilder = factory.newDocumentBuilder();
    }

    private Document parseXml(String xml) throws Exception {
        return documentBuilder.parse(new InputSource(new StringReader(xml)));
    }

    // ============================================
    // exists() Tests
    // ============================================

    @Test
    void testExistsTrue() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        assertTrue(XmlHelper.exists(doc, "//child"));
    }

    @Test
    void testExistsFalse() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        assertFalse(XmlHelper.exists(doc, "//nonexistent"));
    }

    @Test
    void testExistsWithAttribute() throws Exception {
        Document doc = parseXml("<root><child id='123'>content</child></root>");
        assertTrue(XmlHelper.exists(doc, "//child[@id='123']"));
        assertFalse(XmlHelper.exists(doc, "//child[@id='456']"));
    }

    @Test
    void testExistsNullDocument() throws Exception {
        assertFalse(XmlHelper.exists(null, "//child"));
    }

    @Test
    void testExistsNullExpression() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        assertFalse(XmlHelper.exists(doc, null));
    }

    @Test
    void testExistsEmptyExpression() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        assertFalse(XmlHelper.exists(doc, ""));
    }

    // ============================================
    // find() Tests
    // ============================================

    @Test
    void testFindSingleElement() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        List<Element> elements = XmlHelper.find(doc, "//child");
        
        assertEquals(1, elements.size());
        assertEquals("child", elements.get(0).getTagName());
        assertEquals("content", elements.get(0).getTextContent());
    }

    @Test
    void testFindMultipleElements() throws Exception {
        Document doc = parseXml("<root><item>one</item><item>two</item><item>three</item></root>");
        List<Element> elements = XmlHelper.find(doc, "//item");
        
        assertEquals(3, elements.size());
        assertEquals("one", elements.get(0).getTextContent());
        assertEquals("two", elements.get(1).getTextContent());
        assertEquals("three", elements.get(2).getTextContent());
    }

    @Test
    void testFindNoMatch() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        List<Element> elements = XmlHelper.find(doc, "//nonexistent");
        
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    @Test
    void testFindWithPredicate() throws Exception {
        Document doc = parseXml("<root><item type='a'>one</item><item type='b'>two</item></root>");
        List<Element> elements = XmlHelper.find(doc, "//item[@type='a']");
        
        assertEquals(1, elements.size());
        assertEquals("one", elements.get(0).getTextContent());
    }

    @Test
    void testFindNullDocument() throws Exception {
        List<Element> elements = XmlHelper.find(null, "//child");
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    @Test
    void testFindNullExpression() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        List<Element> elements = XmlHelper.find(doc, null);
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    @Test
    void testFindEmptyExpression() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        List<Element> elements = XmlHelper.find(doc, "");
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    // ============================================
    // removeAll() Tests
    // ============================================

    @Test
    void testRemoveAllSingleElement() throws Exception {
        Document doc = parseXml("<root><child>content</child><other>keep</other></root>");
        
        XmlHelper.removeAll(doc, "//child");
        
        assertFalse(XmlHelper.exists(doc, "//child"));
        assertTrue(XmlHelper.exists(doc, "//other"));
    }

    @Test
    void testRemoveAllMultipleElements() throws Exception {
        Document doc = parseXml("<root><item>one</item><item>two</item><keep>three</keep></root>");
        
        XmlHelper.removeAll(doc, "//item");
        
        List<Element> items = XmlHelper.find(doc, "//item");
        assertTrue(items.isEmpty());
        assertTrue(XmlHelper.exists(doc, "//keep"));
    }

    @Test
    void testRemoveAllNoMatch() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        
        // Should not throw
        XmlHelper.removeAll(doc, "//nonexistent");
        
        // Original content should be intact
        assertTrue(XmlHelper.exists(doc, "//child"));
    }

    @Test
    void testRemoveAllNullDocument() throws Exception {
        // Should not throw
        assertDoesNotThrow(() -> XmlHelper.removeAll(null, "//child"));
    }

    // ============================================
    // setAttribute() Tests
    // ============================================

    @Test
    void testSetAttributeNew() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        
        int count = XmlHelper.setAttribute(doc, "//child", "id", "123");
        
        assertEquals(1, count);
        List<Element> elements = XmlHelper.find(doc, "//child");
        assertEquals("123", elements.get(0).getAttribute("id"));
    }

    @Test
    void testSetAttributeReplace() throws Exception {
        Document doc = parseXml("<root><child id='old'>content</child></root>");
        
        int count = XmlHelper.setAttribute(doc, "//child", "id", "new");
        
        assertEquals(1, count);
        List<Element> elements = XmlHelper.find(doc, "//child");
        assertEquals("new", elements.get(0).getAttribute("id"));
    }

    @Test
    void testSetAttributeMultipleElements() throws Exception {
        Document doc = parseXml("<root><item>one</item><item>two</item></root>");
        
        int count = XmlHelper.setAttribute(doc, "//item", "class", "myclass");
        
        assertEquals(2, count);
        List<Element> elements = XmlHelper.find(doc, "//item");
        assertEquals("myclass", elements.get(0).getAttribute("class"));
        assertEquals("myclass", elements.get(1).getAttribute("class"));
    }

    @Test
    void testSetAttributeNoMatch() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        
        int count = XmlHelper.setAttribute(doc, "//nonexistent", "id", "123");
        
        assertEquals(0, count);
    }

    @Test
    void testSetAttributeNullDocument() throws Exception {
        int count = XmlHelper.setAttribute(null, "//child", "id", "123");
        assertEquals(0, count);
    }

    @Test
    void testSetAttributeNullAttributeName() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        int count = XmlHelper.setAttribute(doc, "//child", null, "123");
        assertEquals(0, count);
    }

    @Test
    void testSetAttributeEmptyAttributeName() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        int count = XmlHelper.setAttribute(doc, "//child", "", "123");
        assertEquals(0, count);
    }

    // ============================================
    // append() Tests
    // ============================================

    @Test
    void testAppendWithText() throws Exception {
        Document doc = parseXml("<root></root>");
        Element root = doc.getDocumentElement();
        
        Element child = XmlHelper.append(root, "child", "text content");
        
        assertNotNull(child);
        assertEquals("child", child.getTagName());
        assertEquals("text content", child.getTextContent());
        assertTrue(XmlHelper.exists(doc, "//child"));
    }

    @Test
    void testAppendWithoutText() throws Exception {
        Document doc = parseXml("<root></root>");
        Element root = doc.getDocumentElement();
        
        Element child = XmlHelper.append(root, "child", null);
        
        assertNotNull(child);
        assertEquals("child", child.getTagName());
        assertEquals("", child.getTextContent());
    }

    @Test
    void testAppendMultiple() throws Exception {
        Document doc = parseXml("<root></root>");
        Element root = doc.getDocumentElement();
        
        XmlHelper.append(root, "item", "one");
        XmlHelper.append(root, "item", "two");
        XmlHelper.append(root, "item", "three");
        
        List<Element> items = XmlHelper.find(doc, "//item");
        assertEquals(3, items.size());
    }

    @Test
    void testAppendNullParent() throws Exception {
        Element result = XmlHelper.append(null, "child", "text");
        assertNull(result);
    }

    @Test
    void testAppendNullName() throws Exception {
        Document doc = parseXml("<root></root>");
        Element root = doc.getDocumentElement();
        
        Element result = XmlHelper.append(root, null, "text");
        assertNull(result);
    }

    @Test
    void testAppendEmptyName() throws Exception {
        Document doc = parseXml("<root></root>");
        Element root = doc.getDocumentElement();
        
        Element result = XmlHelper.append(root, "", "text");
        assertNull(result);
    }

    // ============================================
    // Complex XPath Tests
    // ============================================

    @Test
    void testComplexXPathExpression() throws Exception {
        Document doc = parseXml("""
            <config>
                <server name="main">
                    <host>localhost</host>
                    <port>8080</port>
                </server>
                <server name="backup">
                    <host>backup.local</host>
                    <port>8081</port>
                </server>
            </config>
            """);
        
        List<Element> hosts = XmlHelper.find(doc, "//server[@name='main']/host");
        assertEquals(1, hosts.size());
        assertEquals("localhost", hosts.get(0).getTextContent());
    }

    @Test
    void testNestedElementAccess() throws Exception {
        Document doc = parseXml("""
            <root>
                <level1>
                    <level2>
                        <level3>deep content</level3>
                    </level2>
                </level1>
            </root>
            """);
        
        assertTrue(XmlHelper.exists(doc, "//level3"));
        List<Element> elements = XmlHelper.find(doc, "//level3");
        assertEquals("deep content", elements.get(0).getTextContent());
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testInvalidXPathExpression() throws Exception {
        Document doc = parseXml("<root><child>content</child></root>");
        
        // Invalid XPath should throw XPathExpressionException
        assertThrows(XPathExpressionException.class, () -> {
            XmlHelper.exists(doc, "//[invalid");
        });
    }

    @Test
    void testWhitespaceHandling() throws Exception {
        Document doc = parseXml("<root><child>  text with spaces  </child></root>");
        
        List<Element> elements = XmlHelper.find(doc, "//child");
        assertEquals("  text with spaces  ", elements.get(0).getTextContent());
    }

    @Test
    void testEmptyDocument() throws Exception {
        Document doc = parseXml("<root></root>");
        
        assertFalse(XmlHelper.exists(doc, "//child"));
        assertTrue(XmlHelper.find(doc, "//child").isEmpty());
    }
}
