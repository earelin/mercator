package net.earelin.mercator.shared.domain.source;

import java.util.Objects;

/**
 * The outcome of a single {@link BoeHttpClient#get} call &mdash; the politeness/retry chain has
 * already run, so this is the <em>final</em> result for one URL: either the response
 * {@link Success#body()} or a classified {@link Failure}.
 */
public sealed interface HttpFetchResult permits HttpFetchResult.Success, HttpFetchResult.Failure {

    /** A 200 response with its raw body bytes. */
    record Success(byte[] body) implements HttpFetchResult {
        public Success {
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * The request did not yield a usable body (404, non-retryable 4xx, or retries exhausted on
     * 429/5xx/network errors).
     *
     * @param kind       {@link ErrorKind#RETRYABLE} if a later pass might succeed, else
     *                   {@link ErrorKind#PERMANENT}
     * @param statusCode the HTTP status, or {@code 0} for a transport/network error
     * @param detail     a human-readable reason
     */
    record Failure(ErrorKind kind, int statusCode, String detail) implements HttpFetchResult {
        public Failure {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
