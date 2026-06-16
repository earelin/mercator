package net.earelin.mercator.domain.source;

import java.util.Optional;

/**
 * Driven port that extracts text from the authentic-last-resort PDF (ADR-0002), using a lightweight
 * JVM PDF library &mdash; not bormeparser, which is reference-only (ADR-0012). Returns empty if the
 * PDF cannot be read (corrupt / unparseable), which the fetch chain treats as a permanent failure.
 */
public interface PdfTextExtractor {

    /** The extracted text, or empty if the PDF could not be read. */
    Optional<String> extractText(byte[] body);
}
