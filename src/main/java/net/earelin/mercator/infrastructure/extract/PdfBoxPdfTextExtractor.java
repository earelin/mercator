package net.earelin.mercator.infrastructure.extract;

import java.io.IOException;
import java.util.Optional;
import net.earelin.mercator.domain.source.PdfTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PdfTextExtractor} backed by Apache PDFBox &mdash; the lightweight JVM PDF library the
 * feature mandates, not bormeparser (ADR-0012). The PDF is the authentic last resort (ADR-0002).
 * Returns empty on a corrupt/unreadable PDF, which the fetch chain treats as a permanent failure.
 */
public final class PdfBoxPdfTextExtractor implements PdfTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxPdfTextExtractor.class);

    @Override
    public Optional<String> extractText(byte[] body) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(body))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            text = text == null ? "" : text.strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (IOException | RuntimeException e) {
            LOG.warn("PDF text extraction failed: {}", e.toString());
            return Optional.empty();
        }
    }
}
