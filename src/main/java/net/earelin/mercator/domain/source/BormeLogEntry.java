package net.earelin.mercator.domain.source;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One {@code borme_log} row (baseline schema). The domain record doubles as the Micronaut Data
 * entity (the project favours simplicity over a separate persistence row): property names map to
 * the snake_case columns, {@code status}/{@code errorKind} persist as their enum {@code name()}
 * (matching the column CHECK constraints), and {@code sourcePath} persists via
 * {@link SourcePathAttributeConverter} as its lowercase {@code dbValue()}. The DB-managed
 * {@code processed_at} column is not mapped here (the upsert sets it with {@code NOW()}).
 *
 * <p>{@code errorKind}/{@code errorDetail} are set only when {@code status == }{@link
 * BormeLogStatus#ERROR}.
 *
 * @param bormeId     opaque document id (primary key)
 * @param pubDate     publication date of the document's day
 * @param status      processing status
 * @param errorKind   error classification (only for ERROR), else {@code null}
 * @param sourcePath  the write path that produced this row
 * @param errorDetail human-readable detail (only for ERROR), else {@code null}
 */
@MappedEntity("borme_log")
public record BormeLogEntry(
        @Id String bormeId,
        LocalDate pubDate,
        BormeLogStatus status,
        @Nullable ErrorKind errorKind,
        @MappedProperty(converter = SourcePathAttributeConverter.class) SourcePath sourcePath,
        @Nullable String errorDetail) {

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
