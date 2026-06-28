package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.earelin.mercator.domain.source.FetchedDocument;
import net.earelin.mercator.domain.source.Paragraph;
import net.earelin.mercator.domain.source.ParagraphClass;
import net.earelin.mercator.domain.source.Representation;
import net.earelin.mercator.domain.source.XmlDocumentParser;
import org.junit.jupiter.api.Test;

class CompanyBlockSplitterTest {

    private final CompanyBlockSplitter splitter = new CompanyBlockSplitter();

    @Test
    void single_xml_block_pairs_header_with_its_paragraph() throws IOException {
        List<CompanyBlock> blocks = splitter.split(fetchedXml("/fixtures/borme-a-sample.xml"));

        assertThat(blocks).singleElement().satisfies(block -> {
            assertThat(block.header()).isEqualTo("158029 - COMPAÑÍA ESPAÑOLA DE EJEMPLOS SL.");
            assertThat(block.paragraphs()).singleElement().asString()
                    .startsWith("Constitución.").contains("Datos registrales: T 100, F 50, H M-123.");
        });
    }

    @Test
    void groups_each_articulo_with_its_following_parrafos() throws IOException {
        List<CompanyBlock> blocks = splitter.split(fetchedXml("/fixtures/borme-a-multiblock.xml"));

        assertThat(blocks).extracting(CompanyBlock::header).containsExactly(
                "67350 - PRIMERA EMPRESA EJEMPLO SL.",
                "88120 - SEGUNDA COMPAÑÍA DE MUESTRA SAU.",
                "99001 - TERCERA SOCIEDAD LIMITADA SL.");
        assertThat(blocks.get(0).paragraphs()).hasSize(2);
        assertThat(blocks.get(1).paragraphs()).hasSize(1);
        assertThat(blocks.get(2).paragraphs()).hasSize(1);
    }

    @Test
    void multiple_parrafos_accrue_to_one_block_in_order() {
        FetchedDocument document = xml(List.of(
                articulo("12345 - SOLA EMPRESA SL."),
                parrafo("Constitución. Objeto social: ejemplos."),
                parrafo("Nombramientos. Adm. Unico: GARCÍA LÓPEZ ANA."),
                parrafo("Datos registrales. T 1, F 2, H M-3.")));

        List<CompanyBlock> blocks = splitter.split(document);

        assertThat(blocks).singleElement().satisfies(block ->
                assertThat(block.paragraphs()).containsExactly(
                        "Constitución. Objeto social: ejemplos.",
                        "Nombramientos. Adm. Unico: GARCÍA LÓPEZ ANA.",
                        "Datos registrales. T 1, F 2, H M-3."));
    }

    @Test
    void articulo_without_a_parrafo_yields_a_block_with_no_paragraphs() {
        FetchedDocument document = xml(List.of(articulo("12345 - SIN ACTOS SL.")));

        List<CompanyBlock> blocks = splitter.split(document);

        assertThat(blocks).singleElement().satisfies(block -> {
            assertThat(block.header()).isEqualTo("12345 - SIN ACTOS SL.");
            assertThat(block.paragraphs()).isEmpty();
        });
    }

    @Test
    void parrafos_before_the_first_articulo_are_skipped() {
        FetchedDocument document = xml(List.of(
                parrafo("Texto huérfano sin cabecera."),
                articulo("12345 - PRIMERA CABECERA SL."),
                parrafo("Constitución.")));

        List<CompanyBlock> blocks = splitter.split(document);

        assertThat(blocks).extracting(CompanyBlock::header).containsExactly("12345 - PRIMERA CABECERA SL.");
        assertThat(blocks.get(0).paragraphs()).containsExactly("Constitución.");
    }

    @Test
    void fallback_text_splits_on_company_header_lines() {
        String body = """
                78291 - EJEMPLO FALLBACK SL.
                Constitución. Objeto social: cosas. Datos registrales. T 1, F 2, H X-3.
                OTRA SOCIEDAD DE MUESTRA SA.
                Nombramientos. Adm. Unico: GARCÍA LÓPEZ ANA. Datos registrales. T 9, F 8, H X-4.
                """;

        List<CompanyBlock> blocks = splitter.split(text(body));

        assertThat(blocks).extracting(CompanyBlock::header).containsExactly(
                "78291 - EJEMPLO FALLBACK SL.",
                "OTRA SOCIEDAD DE MUESTRA SA.");
        assertThat(blocks.get(0).paragraphs()).containsExactly(
                "Constitución. Objeto social: cosas. Datos registrales. T 1, F 2, H X-3.");
        assertThat(blocks.get(1).paragraphs()).containsExactly(
                "Nombramientos. Adm. Unico: GARCÍA LÓPEZ ANA. Datos registrales. T 9, F 8, H X-4.");
    }

    @Test
    void fallback_header_numeric_prefix_is_tolerated_but_kept_verbatim() {
        String body = """
                15348 - EMPRESA NUMERADA SL.
                Constitución. Objeto social: ejemplos.
                """;

        List<CompanyBlock> blocks = splitter.split(text(body));

        assertThat(blocks).singleElement().extracting(CompanyBlock::header)
                .isEqualTo("15348 - EMPRESA NUMERADA SL.");
    }

    @Test
    void fallback_text_without_a_recognisable_header_yields_no_blocks() {
        List<CompanyBlock> blocks = splitter.split(text("Una línea cualquiera.\nOtra línea sin cabecera."));

        assertThat(blocks).isEmpty();
    }

    @Test
    void empty_body_yields_no_blocks() {
        assertThat(splitter.split(text(""))).isEmpty();
    }

    private static FetchedDocument fetchedXml(String resource) throws IOException {
        byte[] body = fixture(resource);
        XmlDocumentParser.ParsedXml parsed = new XmlDocumentParser().parse(body);
        return new FetchedDocument(
                "BORME-A-2024-1-01", Representation.XML, parsed.charset(),
                parsed.rawBody(), parsed.metadata(), parsed.paragraphs());
    }

    private static FetchedDocument xml(List<Paragraph> paragraphs) {
        return new FetchedDocument(
                "BORME-A-2024-1-01", Representation.XML, StandardCharsets.UTF_8, "<documento/>", null, paragraphs);
    }

    private static FetchedDocument text(String body) {
        return new FetchedDocument(
                "BORME-A-2024-1-01", Representation.TXT, StandardCharsets.UTF_8, body, null, List.of());
    }

    private static Paragraph articulo(String text) {
        return new Paragraph(ParagraphClass.ARTICULO, text);
    }

    private static Paragraph parrafo(String text) {
        return new Paragraph(ParagraphClass.PARRAFO, text);
    }

    private static byte[] fixture(String resource) throws IOException {
        try (InputStream in = CompanyBlockSplitterTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("missing test fixture %s", resource).isNotNull();
            return in.readAllBytes();
        }
    }
}
