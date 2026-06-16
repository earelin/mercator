package net.earelin.mercator.domain.source;

import java.util.Optional;

/**
 * Driven port that strips the {@code txt.php} HTML chrome down to the document's plain text (first
 * fallback, ADR-0002). Returns empty when the response is not real content &mdash; e.g. a BOE error
 * page rather than a document &mdash; so the fetch chain continues to the PDF.
 */
public interface HtmlTextExtractor {

    /** The stripped plain text, or empty if {@code body} is an error page / has no usable content. */
    Optional<String> extractText(byte[] body);
}
