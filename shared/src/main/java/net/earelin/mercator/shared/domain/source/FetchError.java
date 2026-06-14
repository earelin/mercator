package net.earelin.mercator.shared.domain.source;

import java.util.Objects;

/**
 * Describes why a document could not be fetched in any representation, after the rate-limited,
 * bounded-retry chain gave up. Carried by {@link FetchOutcome.Failed} and recorded as a
 * {@code borme_log} ERROR row.
 *
 * @param kind   whether a later pass might succeed ({@link ErrorKind#RETRYABLE}) or not
 *               ({@link ErrorKind#PERMANENT})
 * @param detail a human-readable summary for {@code borme_log.error_detail}
 */
public record FetchError(ErrorKind kind, String detail) {

    public FetchError {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }
}
