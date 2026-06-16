package net.earelin.mercator.domain.source;

/**
 * The application-facing port both write paths call to obtain a document's raw input: read-through
 * the cache, else fetch XML &rarr; txt.php &rarr; PDF with the shared politeness/retry policy,
 * caching the result and recording {@code borme_log}. Fail-soft &mdash; total failure is returned
 * as {@link FetchOutcome.Failed}, never thrown.
 */
public interface DocumentSource {

    /**
     * Fetch (or serve from cache) the document described by {@code descriptor}.
     *
     * @param descriptor the document and its per-representation URLs
     * @param context    the publication date and write path (for {@code borme_log})
     * @return the fetched document or a classified failure
     */
    FetchOutcome fetch(DocumentDescriptor descriptor, FetchContext context);
}
