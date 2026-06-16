package net.earelin.mercator.domain.source;

import java.util.Objects;

/**
 * The result of {@link DocumentSource#fetch}: either a {@link Fetched} document (from the network
 * or the cache) or a {@link Failed} fetch. Fail-soft &mdash; a failure is a value, never a thrown
 * exception, so a single bad document never aborts a run (ADR-0018).
 */
public sealed interface FetchOutcome permits FetchOutcome.Fetched, FetchOutcome.Failed {

    /** A document successfully fetched or served from cache. */
    record Fetched(FetchedDocument document) implements FetchOutcome {
        public Fetched {
            Objects.requireNonNull(document, "document");
        }
    }

    /** A document that could not be fetched in any representation. */
    record Failed(FetchError error) implements FetchOutcome {
        public Failed {
            Objects.requireNonNull(error, "error");
        }
    }

    static Fetched fetched(FetchedDocument document) {
        return new Fetched(document);
    }

    static Failed failed(FetchError error) {
        return new Failed(error);
    }
}
