package net.earelin.mercator.domain.source;

/**
 * Which write path is processing a document, mirroring the {@code borme_log.source_path} CHECK
 * constraint (migration V1.7.0). There is no {@code api_ingest} value &mdash; the public API is
 * read-only (ADR-0006).
 */
public enum SourcePath {

    BACKFILL("backfill"),
    DAILY_INCREMENTAL("daily_incremental");

    private final String dbValue;

    SourcePath(String dbValue) {
        this.dbValue = dbValue;
    }

    /** The exact string stored in {@code borme_log.source_path}. */
    public String dbValue() {
        return dbValue;
    }
}
