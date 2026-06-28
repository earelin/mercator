package net.earelin.mercator.domain.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of enumerating one publication day's BORME summary (summary-enumeration). Fail-soft
 * &mdash; every case, including a give-up, is a value, never a thrown exception, so iterating a
 * range never aborts on one bad day (ADR-0018).
 *
 * <p>Enumeration is <strong>stateless</strong>: neither a {@link NotPublished} (404) nor an empty
 * {@link Published} day is persisted &mdash; {@code borme_log} is keyed per document, so a day with
 * no documents has nothing to key a row on. Resumability is owned per-document by the write paths.
 */
public sealed interface SummaryResult
        permits SummaryResult.Published, SummaryResult.NotPublished, SummaryResult.Failed {

    /** The publication day this result describes. */
    LocalDate date();

    /**
     * A 200 summary: the day's Secci&oacute;n A document descriptors, in summary order.
     * {@code documents} is empty when the day published but carried no Secci&oacute;n A items
     * &mdash; a normal "nothing to ingest" outcome, not an error.
     */
    record Published(LocalDate date, List<DocumentDescriptor> documents) implements SummaryResult {
        public Published {
            Objects.requireNonNull(date, "date");
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }

    /** A 404: a non-publication day (weekend/holiday). Skip, do not error, not persisted. */
    record NotPublished(LocalDate date) implements SummaryResult {
        public NotPublished {
            Objects.requireNonNull(date, "date");
        }
    }

    /**
     * The summary could not be obtained or parsed: the shared retry policy gave up on a 5xx/429/
     * network error, or the body was malformed XML. The classified {@link FetchError} lets a later
     * pass decide whether to retry. Distinct from {@link NotPublished} so a transient outage is
     * never mistaken for a non-publication day.
     */
    record Failed(LocalDate date, FetchError error) implements SummaryResult {
        public Failed {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(error, "error");
        }
    }
}
