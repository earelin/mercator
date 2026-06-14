package net.earelin.mercator.shared.infrastructure.extract;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void extractsTextFromValidPdf() throws IOException {
        byte[] pdf = singlePagePdf("Mercator PDF fallback sample text");

        Optional<String> text = extractor.extractText(pdf);

        assertTrue(text.isPresent());
        assertTrue(text.get().contains("Mercator PDF fallback sample text"), text.get());
    }

    @Test
    void returnsEmptyForCorruptPdf() {
        Optional<String> text = extractor.extractText("this is not a pdf".getBytes(StandardCharsets.UTF_8));
        assertTrue(text.isEmpty());
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
