package net.earelin.mercator.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryXmlParserTest {

    private final SummaryXmlParser parser = new SummaryXmlParser();

    @Test
    void selects_section_a_items_and_ignores_b_and_c() throws IOException {
        List<DocumentDescriptor> descriptors = parser.parseSectionA(fixture("/fixtures/borme-summary-publication-day.xml"));

        // Exactly the three Sección A identifiers, in summary order; B and C are skipped.
        assertThat(descriptors).extracting(DocumentDescriptor::bormeId)
                .containsExactly("BORME-A-2026-9-01", "BORME-A-2026-9-08", "BORME-A-2026-9-28");
    }

    @Test
    void carries_titulo_verbatim_as_province_preserving_spanish_characters() throws IOException {
        List<DocumentDescriptor> descriptors = parser.parseSectionA(fixture("/fixtures/borme-summary-publication-day.xml"));

        assertThat(descriptors).extracting(DocumentDescriptor::province)
                .containsExactly("ARAÑÓN", "BARCELONA", "MÁLAGA");
    }

    @Test
    void captures_all_three_per_item_urls() throws IOException {
        List<DocumentDescriptor> descriptors = parser.parseSectionA(fixture("/fixtures/borme-summary-publication-day.xml"));

        DocumentDescriptor barcelona = descriptors.get(1);
        assertThat(barcelona.urlXml())
                .hasToString("https://www.boe.es/diario_borme/xml.php?id=BORME-A-2026-9-08");
        assertThat(barcelona.urlHtml())
                .hasToString("https://www.boe.es/diario_borme/txt.php?id=BORME-A-2026-9-08");
        assertThat(barcelona.urlPdf())
                .hasToString("https://www.boe.es/borme/dias/2026/06/09/pdfs/BORME-A-2026-9-08.pdf");
    }

    @Test
    void reconstructs_url_xml_from_identifier_when_absent() {
        // An item with no <url_xml> must still get a primary source via the xml.php fallback.
        byte[] body = sectionA("""
                <item>
                  <identificador>BORME-A-2026-9-50</identificador>
                  <titulo>ZARAGOZA</titulo>
                  <url_pdf>https://www.boe.es/borme/dias/2026/06/09/pdfs/BORME-A-2026-9-50.pdf</url_pdf>
                  <url_html>https://www.boe.es/diario_borme/txt.php?id=BORME-A-2026-9-50</url_html>
                </item>
                """);

        List<DocumentDescriptor> descriptors = parser.parseSectionA(body);

        assertThat(descriptors).singleElement().satisfies(d ->
                assertThat(d.urlXml())
                        .hasToString("https://www.boe.es/diario_borme/xml.php?id=BORME-A-2026-9-50"));
    }

    @Test
    void uses_configured_xml_php_base_for_the_fallback() {
        SummaryXmlParser custom = new SummaryXmlParser("http://localhost:8089/xml.php?id=");
        byte[] body = sectionA("""
                <item><identificador>BORME-A-2026-9-50</identificador><titulo>ZARAGOZA</titulo></item>
                """);

        assertThat(custom.parseSectionA(body)).singleElement().satisfies(d ->
                assertThat(d.urlXml()).hasToString("http://localhost:8089/xml.php?id=BORME-A-2026-9-50"));
    }

    @Test
    void skips_items_with_no_identifier() {
        byte[] body = sectionA("""
                <item><titulo>NO ID</titulo></item>
                <item><identificador>BORME-A-2026-9-01</identificador><titulo>ARAÑÓN</titulo></item>
                """);

        assertThat(parser.parseSectionA(body)).extracting(DocumentDescriptor::bormeId)
                .containsExactly("BORME-A-2026-9-01");
    }

    @Test
    void a_day_with_no_section_a_yields_an_empty_list() throws IOException {
        assertThat(parser.parseSectionA(fixture("/fixtures/borme-summary-no-section-a.xml"))).isEmpty();
    }

    @Test
    void malformed_xml_raises_xml_parse_exception() {
        byte[] body = "<response><data><sumario".getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(XmlParseException.class)
                .isThrownBy(() -> parser.parseSectionA(body));
    }

    // --- helpers ---------------------------------------------------------------------------

    /** Wrap one or more {@code <item>} blocks in a minimal Sección A summary document. */
    private static byte[] sectionA(String items) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<response><data><sumario><diario numero=\"9\">"
                + "<seccion codigo=\"A\" nombre=\"Empresarios. Actos inscritos\">"
                + items
                + "</seccion></diario></sumario></data></response>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String resource) throws IOException {
        try (InputStream in = SummaryXmlParserTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("missing test fixture %s", resource).isNotNull();
            return in.readAllBytes();
        }
    }
}
