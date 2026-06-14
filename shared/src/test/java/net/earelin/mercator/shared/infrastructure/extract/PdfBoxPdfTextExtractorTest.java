package net.earelin.mercator.shared.infrastructure.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfBoxPdfTextExtractorTest {

    private final PdfBoxPdfTextExtractor extractor = new PdfBoxPdfTextExtractor();

    @Test
    void extracts_text_from_valid_pdf() throws IOException {
        byte[] pdf = singlePagePdf("Mercator PDF fallback sample text");

        Optional<String> text = extractor.extractText(pdf);

        assertThat(text).isPresent();
        assertThat(text.get()).contains("Mercator PDF fallback sample text");
    }

    @Test
    void returns_empty_for_corrupt_pdf() {
        Optional<String> text = extractor.extractText("this is not a pdf".getBytes(StandardCharsets.UTF_8));
        assertThat(text).isEmpty();
    }

    private static byte[] singlePagePdf(String body) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(body);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
