package net.earelin.mercator.domain.source;

/**
 * Classification of a final fetch failure, mirroring the {@code borme_log.error_kind} CHECK
 * constraint (migration V1.7.0). {@link #RETRYABLE} failures (429/5xx/network) may succeed on a
 * later pass; {@link #PERMANENT} failures (404 in every representation, malformed body) will not
 * (ADR-0018).
 */
public enum ErrorKind {
    RETRYABLE,
    PERMANENT
}
