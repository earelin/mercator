package net.earelin.mercator.domain.source;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses a BOE {@code datosabiertos} summary XML body into the day's Secci&oacute;n A
 * {@link DocumentDescriptor} list. JDK-only (DOM); no external dependency, so it stays in the
 * domain layer (mirrors {@link XmlDocumentParser}).
 *
 * <p>The summary tree is {@code response → data → sumario → diario → seccion[@codigo] → item}. This
 * parser selects only {@code seccion[@codigo="A"]} (Empresarios. Actos inscritos) and collects every
 * descendant {@code <item>}, skipping Secci&oacute;n B/C (out of scope for V1). For each item it
 * reads the opaque {@code identificador} (used verbatim as {@code bormeId}, never parsed/zero-padded),
 * the {@code titulo} (carried verbatim as the raw {@code province}), and the
 * {@code url_pdf}/{@code url_xml}/{@code url_html}. When {@code url_xml} is absent it is reconstructed
 * as {@code xml.php?id={identificador}} so a descriptor always has its primary source.
 */
public final class SummaryXmlParser {

    /** The per-document XML endpoint, used to reconstruct an absent {@code url_xml}. */
    public static final String DEFAULT_XML_PHP_BASE = "https://www.boe.es/diario_borme/xml.php?id=";

    private static final String SECTION_A = "A";

    private final String xmlPhpBase;

    /** Uses the live BOE {@code xml.php} endpoint for the {@code url_xml} fallback. */
    public SummaryXmlParser() {
        this(DEFAULT_XML_PHP_BASE);
    }

    /**
     * @param xmlPhpBase base for reconstructing an absent {@code url_xml} (the identifier is
     *                   appended verbatim), e.g. {@value #DEFAULT_XML_PHP_BASE}
     */
    public SummaryXmlParser(String xmlPhpBase) {
        this.xmlPhpBase = xmlPhpBase;
    }

    /**
     * Parse {@code body} and return the day's Secci&oacute;n A descriptors, in summary order. An
     * empty list means the day carried no Secci&oacute;n A items (a normal "nothing to ingest" day).
     *
     * @throws XmlParseException if the body is not well-formed XML
     */
    public List<DocumentDescriptor> parseSectionA(byte[] body) {
        Document doc = XmlSupport.parse(body);
        Element root = doc.getDocumentElement();

        List<DocumentDescriptor> descriptors = new ArrayList<>();
        NodeList secciones = root.getElementsByTagName("seccion");
        for (int i = 0; i < secciones.getLength(); i++) {
            Element seccion = (Element) secciones.item(i);
            if (!SECTION_A.equalsIgnoreCase(seccion.getAttribute("codigo"))) {
                continue;
            }
            NodeList items = seccion.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                DocumentDescriptor descriptor = toDescriptor((Element) items.item(j));
                if (descriptor != null) {
                    descriptors.add(descriptor);
                }
            }
        }
        return descriptors;
    }

    /** Build a descriptor from one {@code <item>}, or {@code null} if it has no usable identifier. */
    private DocumentDescriptor toDescriptor(Element item) {
        String identificador = XmlSupport.childText(item, "identificador");
        if (identificador == null) {
            return null;
        }
        String province = XmlSupport.childText(item, "titulo");
        URI urlXml = XmlSupport.parseUri(XmlSupport.childText(item, "url_xml"));
        if (urlXml == null) {
            urlXml = URI.create(xmlPhpBase + identificador);
        }
        URI urlHtml = XmlSupport.parseUri(XmlSupport.childText(item, "url_html"));
        URI urlPdf = XmlSupport.parseUri(XmlSupport.childText(item, "url_pdf"));
        return new DocumentDescriptor(identificador, province, urlXml, urlHtml, urlPdf);
    }
}
