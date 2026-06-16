package net.earelin.mercator.domain.source;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The per-day / per-run context a {@link DocumentSource#fetch} call needs that is not part of the
 * document {@link DocumentDescriptor} itself: the publication date (known at summary time, written
 * into {@code borme_log}) and which write path is running. Supplied by the caller (the backfill or
 * the daily incremental).
 */
public record FetchContext(LocalDate pubDate, SourcePath sourcePath) {

    public FetchContext {
        Objects.requireNonNull(pubDate, "pubDate");
        Objects.requireNonNull(sourcePath, "sourcePath");
    }
}
