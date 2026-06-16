package net.earelin.mercator.infrastructure.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning for the polite BOE HTTP layer (ADR-0018). A plain value object &mdash; the Micronaut
 * {@code server} layer binds it from config; the {@code domain}/{@code infrastructure} core stays
 * framework-free.
 *
 * @param userAgent         descriptive User-Agent identifying the project + a contact, e.g.
 *                          {@code Mercator/0.1 (+https://github.com/earelin/mercator; mailto:...)}
 * @param requestsPerSecond the global rate ceiling (~1&ndash;2 req/s)
 * @param maxAttempts       bounded total attempts per URL (1 = no retry)
 * @param baseBackoff       base delay for exponential backoff
 * @param maxBackoff        cap on a single backoff delay
 * @param connectTimeout    TCP connect timeout
 * @param readTimeout       per-request response timeout
 * @param maxBodyBytes      reject bodies larger than this (guards memory on pathological responses)
 */
public record BoeSourceConfig(
        String userAgent,
        double requestsPerSecond,
        int maxAttempts,
        Duration baseBackoff,
        Duration maxBackoff,
        Duration connectTimeout,
        Duration readTimeout,
        long maxBodyBytes) {

    public BoeSourceConfig {
        Objects.requireNonNull(userAgent, "userAgent");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must be descriptive, not blank");
        }
        if (!(requestsPerSecond > 0.0)) {
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(baseBackoff, "baseBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("maxBodyBytes must be > 0");
        }
    }

    /** Sensible defaults for the given (required) descriptive User-Agent. */
    public static BoeSourceConfig defaults(String userAgent) {
        return new BoeSourceConfig(
                userAgent,
                1.5,
                5,
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                64L * 1024 * 1024);
    }
}
