package net.earelin.mercator.shared.domain.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class XmlDocumentParserTest {

    private final XmlDocumentParser parser = new XmlDocumentParser();

    @Test
    void parsesMetadataParagraphsAndPreservesEncoding() throws IOException {
        byte[] body = fixture("/fixtures/borme-a-sample.xml");

        XmlDocumentParser.ParsedXml parsed = parser.parse(body);

        assertEquals(StandardCharsets.UTF_8, parsed.charset());
        assertFalse(parsed.isEmpty());

        DocumentMetadata meta = parsed.metadata();
        assertNotNull(meta);
        assertEquals("BORME-A-2024-1-01", meta.identificador());
        assertEquals("A", meta.seccion());
        assertEquals(LocalDate.of(2024, 1, 2), meta.pubDate());
        assertEquals(3, meta.pages());
        assertEquals(
                "https://www.boe.es/borme/dias/2024/01/02/pdfs/BORME-A-2024-1-01.pdf",
                meta.urlPdf().toString());
        // Spanish characters survive the round-trip.
        assertTrue(meta.titulo().contains("ARAÑÓN"), meta.titulo());

        // The <p class="otro"> is ignored; only articulo/parrafo are kept, in order.
        assertEquals(2, parsed.paragraphs().size());
        assertEquals(ParagraphClass.ARTICULO, parsed.paragraphs().get(0).styleClass());
        assertTrue(parsed.paragraphs().get(0).text().contains("COMPAÑÍA ESPAÑOLA"));
        assertEquals(ParagraphClass.PARRAFO, parsed.paragraphs().get(1).styleClass());
        assertTrue(parsed.paragraphs().get(1).text().contains("Constitución"));

        assertTrue(parsed.rawBody().contains("COMPAÑÍA ESPAÑOLA"));
    }

    @Test
    void emptyWhenNoTextoParagraphs() {
        byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<documento><metadatos><identificador>BORME-A-2024-1-01</identificador>"
                + "</metadatos><texto></texto></documento>")
                .getBytes(StandardCharsets.UTF_8);

        XmlDocumentParser.ParsedXml parsed = parser.parse(body);

        assertTrue(parsed.isEmpty());
        assertNotNull(parsed.metadata());
    }

    @Test
    void throwsOnMalformedXml() {
        byte[] body = "<documento><texto><p class=\"parrafo\">unclosed".getBytes(StandardCharsets.UTF_8);
        assertThrows(XmlParseException.class, () -> parser.parse(body));
    }

    private static byte[] fixture(String resource) throws IOException {
        try (InputStream in = XmlDocumentParserTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing test fixture " + resource);
            return in.readAllBytes();
        }
    }
}
