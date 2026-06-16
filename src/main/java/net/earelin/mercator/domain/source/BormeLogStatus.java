package net.earelin.mercator.domain.source;

/**
 * Per-document processing status, mirroring the {@code borme_log.status} CHECK constraint
 * (migration V1.7.0). Fetch only produces {@link #FETCHED} (success) and {@link #ERROR} (final
 * give-up); the later lifecycle states are written by the parse/merge stages.
 */
public enum BormeLogStatus {
    FETCHED,
    PARSED,
    MERGED,
    SKIPPED,
    ERROR
}
