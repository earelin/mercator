package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Shared, JDK-only DOM helpers for the per-document and summary XML parsers. Kept in one place so
 * the secure-factory hardening (XXE off, no external entities) and the small element/text/URI
 * accessors are defined once rather than duplicated across {@link XmlDocumentParser} and
 * {@link SummaryXmlParser}. Package-private &mdash; a domain-internal utility, not a port.
 */
final class XmlSupport {

    private static final Pattern ENCODING_DECL =
            Pattern.compile("<\\?xml[^>]*\\bencoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private XmlSupport() {
    }

    /**
     * Parse {@code body} into a hardened DOM document.
     *
     * @param context caller-supplied prefix for the failure message, so each parser keeps its own
     *                error wording (e.g. "malformed per-document XML")
     * @throws XmlParseException if the body is not well-formed XML
     */
    static Document parse(byte[] body, String context) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new XmlParseException(context + ": " + e.getMessage(), e);
        }
    }

    /**
     * The charset declared in the XML prolog, defaulting to UTF-8 (what the BOE serves). The prolog
     * is read as ASCII (the XML declaration is always ASCII-compatible) to honour its declared
     * encoding before decoding the rest of the body.
     */
    static Charset detectCharset(byte[] body) {
        int prologLen = Math.min(body.length, 128);
        String prolog = new String(body, 0, prologLen, StandardCharsets.US_ASCII);
        Matcher m = ENCODING_DECL.matcher(prolog);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).trim());
            } catch (RuntimeException ignored) {
                // unknown/illegal charset name — fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // XXE hardening: no DOCTYPE, no external entities.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /** Every descendant element named {@code tagName}, in document order. */
    static List<Element> elements(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            elements.add((Element) nodes.item(i));
        }
        return elements;
    }

    /** The first direct child element named {@code tagName}, or {@code null} if there is none. */
    static @Nullable Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /** The trimmed text of the first {@code tagName} child, or {@code null} if absent/blank. */
    static @Nullable String childText(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        if (child == null) {
            return null;
        }
        String text = child.getTextContent();
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Parse {@code value} into a URI, or {@code null} if it is blank or syntactically invalid. */
    static @Nullable URI parseUri(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
