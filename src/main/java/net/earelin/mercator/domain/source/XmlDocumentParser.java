package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
        Charset charset = XmlSupport.detectCharset(body);
        String rawBody = new String(body, charset);
        Document doc = XmlSupport.parse(body, "malformed per-document XML");
        Element root = doc.getDocumentElement();

        Element metadatos = XmlSupport.firstChildElement(root, "metadatos");
        DocumentMetadata metadata = metadatos == null ? null : parseMetadata(metadatos);

        Element texto = XmlSupport.firstChildElement(root, "texto");
        List<Paragraph> paragraphs = texto == null ? List.of() : parseParagraphs(texto);

        return new ParsedXml(metadata, paragraphs, rawBody, charset);
    }

    private static DocumentMetadata parseMetadata(Element metadatos) {
        String identificador = XmlSupport.childText(metadatos, "identificador");
        String titulo = XmlSupport.childText(metadatos, "titulo");
        String seccion = XmlSupport.childText(metadatos, "seccion");
        LocalDate pubDate = parseDate(XmlSupport.childText(metadatos, "fecha_publicacion"));
        Integer pages = parsePages(metadatos);
        URI urlPdf = XmlSupport.parseUri(XmlSupport.childText(metadatos, "url_pdf"));
        return new DocumentMetadata(identificador, titulo, seccion, pubDate, pages, urlPdf);
    }

    private static List<Paragraph> parseParagraphs(Element texto) {
        List<Paragraph> paragraphs = new ArrayList<>();
        for (Element p : XmlSupport.elements(texto, "p")) {
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
        Integer explicit = parseInt(XmlSupport.childText(metadatos, "numero_paginas"));
        if (explicit != null) {
            return explicit;
        }
        Integer first = parseInt(XmlSupport.childText(metadatos, "pagina_inicial"));
        Integer last = parseInt(XmlSupport.childText(metadatos, "pagina_final"));
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
}
