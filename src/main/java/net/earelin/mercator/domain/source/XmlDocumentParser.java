package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * Parses a per-document BORME XML body ({@code <documento>} with {@code <metadatos>} and a
 * {@code <texto>} pre-segmented into {@code <p class="articulo|parrafo">}) into a
 * {@link ParsedXml}. JDK-only (DOM); no external dependency, so it stays in the domain layer.
 *
 * <p>Encoding-safe: the body is decoded with the charset declared in the XML prolog (default
 * UTF-8), preserving Spanish characters (&Ntilde;, accents) faithfully. Metadata fields are
 * best-effort &mdash; a field absent from the source becomes {@code null}; the load-bearing output
 * is the paragraph segmentation.
 */
public final class XmlDocumentParser {

    private static final Pattern ENCODING_DECL =
            Pattern.compile("<\\?xml[^>]*\\bencoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * The parsed result.
     *
     * @param metadata   the {@code <metadatos>} block (may be {@code null} if absent)
     * @param paragraphs the {@code articulo}/{@code parrafo} segmentation, in document order
     * @param rawBody    the full XML decoded with {@link #charset}
     * @param charset    the charset the body was decoded with
     */
    public record ParsedXml(
            @Nullable DocumentMetadata metadata,
            List<Paragraph> paragraphs,
            String rawBody,
            Charset charset) {

        public ParsedXml {
            paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        }

        /** A 200 XML body that carries no act content ({@code <texto>} absent or empty). */
        public boolean isEmpty() {
            return paragraphs.isEmpty();
        }
    }

    /**
     * Parse {@code body}.
     *
     * @throws XmlParseException if the body is not well-formed XML
     */
    public ParsedXml parse(byte[] body) {
        Charset charset = detectCharset(body);
        String rawBody = new String(body, charset);
        Document doc = parseDom(body);
        Element root = doc.getDocumentElement();

        Element metadatos = firstChildElement(root, "metadatos");
        DocumentMetadata metadata = metadatos == null ? null : parseMetadata(metadatos);

        Element texto = firstChildElement(root, "texto");
        List<Paragraph> paragraphs = texto == null ? List.of() : parseParagraphs(texto);

        return new ParsedXml(metadata, paragraphs, rawBody, charset);
    }

    private static Charset detectCharset(byte[] body) {
        // Read the prolog as ASCII (the XML declaration is always ASCII-compatible) and honour its
        // declared encoding; default to UTF-8, which is what the BOE serves.
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

    private static Document parseDom(byte[] body) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new XmlParseException("malformed per-document XML: " + e.getMessage(), e);
        }
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

    private static DocumentMetadata parseMetadata(Element metadatos) {
        String identificador = childText(metadatos, "identificador");
        String titulo = childText(metadatos, "titulo");
        String seccion = childText(metadatos, "seccion");
        LocalDate pubDate = parseDate(childText(metadatos, "fecha_publicacion"));
        Integer pages = parsePages(metadatos);
        URI urlPdf = parseUri(childText(metadatos, "url_pdf"));
        return new DocumentMetadata(identificador, titulo, seccion, pubDate, pages, urlPdf);
    }

    private static List<Paragraph> parseParagraphs(Element texto) {
        List<Paragraph> paragraphs = new ArrayList<>();
        NodeList children = texto.getElementsByTagName("p");
        for (int i = 0; i < children.getLength(); i++) {
            Element p = (Element) children.item(i);
            ParagraphClass styleClass = paragraphClass(p.getAttribute("class"));
            if (styleClass != null) {
                paragraphs.add(new Paragraph(styleClass, p.getTextContent().strip()));
            }
        }
        return paragraphs;
    }

    private static @Nullable ParagraphClass paragraphClass(String cssClass) {
        if (cssClass == null) {
            return null;
        }
        return switch (cssClass.trim()) {
            case "articulo" -> ParagraphClass.ARTICULO;
            case "parrafo" -> ParagraphClass.PARRAFO;
            default -> null;
        };
    }

    private static @Nullable Integer parsePages(Element metadatos) {
        Integer explicit = parseInt(childText(metadatos, "numero_paginas"));
        if (explicit != null) {
            return explicit;
        }
        Integer first = parseInt(childText(metadatos, "pagina_inicial"));
        Integer last = parseInt(childText(metadatos, "pagina_final"));
        if (first != null && last != null && last >= first) {
            return last - first + 1;
        }
        return null;
    }

    private static @Nullable LocalDate parseDate(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (DateTimeFormatter fmt : List.of(YYYYMMDD, DD_MM_YYYY)) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        try {
            return LocalDate.parse(v); // ISO-8601 (yyyy-MM-dd)
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable URI parseUri(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable String childText(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        if (child == null) {
            return null;
        }
        String text = child.getTextContent();
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static @Nullable Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
