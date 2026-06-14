package net.earelin.mercator.shared.domain.source;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One row to record in {@code borme_log} (migration V1.7.0). {@code errorKind}/{@code errorDetail}
 * are set only when {@code status == }{@link BormeLogStatus#ERROR}.
 *
 * @param bormeId     opaque document id (primary key)
 * @param pubDate     publication date of the document's day
 * @param status      processing status
 * @param errorKind   error classification (only for ERROR), else {@code null}
 * @param sourcePath  the write path that produced this row
 * @param errorDetail human-readable detail (only for ERROR), else {@code null}
 */
public record BormeLogEntry(
        String bormeId,
        LocalDate pubDate,
        BormeLogStatus status,
        ErrorKind errorKind,
        SourcePath sourcePath,
        String errorDetail) {

    public BormeLogEntry {
        Objects.requireNonNull(bormeId, "bormeId");
        Objects.requireNonNull(pubDate, "pubDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourcePath, "sourcePath");
        if (status == BormeLogStatus.ERROR) {
            Objects.requireNonNull(errorKind, "errorKind is required when status is ERROR");
        } else if (errorKind != null) {
            throw new IllegalArgumentException("errorKind must be null unless status is ERROR");
        }
    }

    /** A FETCHED entry (no error fields). */
    static BormeLogEntry fetched(String bormeId, LocalDate pubDate, SourcePath sourcePath) {
        return new BormeLogEntry(bormeId, pubDate, BormeLogStatus.FETCHED, null, sourcePath, null);
    }

    /** An ERROR entry carrying the failure classification and detail. */
    static BormeLogEntry error(String bormeId, LocalDate pubDate, SourcePath sourcePath, FetchError error) {
        return new BormeLogEntry(
                bormeId, pubDate, BormeLogStatus.ERROR, error.kind(), sourcePath, error.detail());
    }
}
