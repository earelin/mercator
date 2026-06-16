package net.earelin.mercator.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class XmlDocumentParserTest {

    private final XmlDocumentParser parser = new XmlDocumentParser();

    @Test
    void parses_metadata_paragraphs_and_preserves_encoding() throws IOException {
        byte[] body = fixture("/fixtures/borme-a-sample.xml");

        XmlDocumentParser.ParsedXml parsed = parser.parse(body);

        assertThat(parsed.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(parsed.isEmpty()).isFalse();

        DocumentMetadata meta = parsed.metadata();
        assertThat(meta).isNotNull();
        assertThat(meta.identificador()).isEqualTo("BORME-A-2024-1-01");
        assertThat(meta.seccion()).isEqualTo("A");
        assertThat(meta.pubDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(meta.pages()).isEqualTo(3);
        assertThat(meta.urlPdf())
                .hasToString("https://www.boe.es/borme/dias/2024/01/02/pdfs/BORME-A-2024-1-01.pdf");
        // Spanish characters survive the round-trip.
        assertThat(meta.titulo()).contains("ARAÑÓN");

        // The <p class="otro"> is ignored; only articulo/parrafo are kept, in order.
        assertThat(parsed.paragraphs()).hasSize(2);
        assertThat(parsed.paragraphs().get(0).styleClass()).isEqualTo(ParagraphClass.ARTICULO);
        assertThat(parsed.paragraphs().get(0).text()).contains("COMPAÑÍA ESPAÑOLA");
        assertThat(parsed.paragraphs().get(1).styleClass()).isEqualTo(ParagraphClass.PARRAFO);
        assertThat(parsed.paragraphs().get(1).text()).contains("Constitución");

        assertThat(parsed.rawBody()).contains("COMPAÑÍA ESPAÑOLA");
    }

    @Test
    void empty_when_no_texto_paragraphs() {
        byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<documento><metadatos><identificador>BORME-A-2024-1-01</identificador>"
                + "</metadatos><texto></texto></documento>")
                .getBytes(StandardCharsets.UTF_8);

        XmlDocumentParser.ParsedXml parsed = parser.parse(body);

        assertThat(parsed.isEmpty()).isTrue();
        assertThat(parsed.metadata()).isNotNull();
    }

    @Test
    void throws_on_malformed_xml() {
        byte[] body = "<documento><texto><p class=\"parrafo\">unclosed".getBytes(StandardCharsets.UTF_8);
        assertThatExceptionOfType(XmlParseException.class).isThrownBy(() -> parser.parse(body));
    }

    private static byte[] fixture(String resource) throws IOException {
        try (InputStream in = XmlDocumentParserTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("missing test fixture %s", resource).isNotNull();
            return in.readAllBytes();
        }
    }
}
