package net.earelin.mercator.shared.infrastructure.http;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import net.earelin.mercator.shared.domain.source.BoeHttpClient;
import net.earelin.mercator.shared.domain.source.ErrorKind;
import net.earelin.mercator.shared.domain.source.HttpFetchResult;
import net.earelin.mercator.shared.infrastructure.http.HttpTransport.HttpResponseBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BoeHttpClient} adapter implementing the ADR-0018 politeness/resilience policy over an
 * {@link HttpTransport}:
 * <ul>
 *   <li>acquires a shared {@link RateLimiter} permit before every request;</li>
 *   <li>sends a descriptive {@code User-Agent};</li>
 *   <li>retries 429/5xx and network errors with bounded exponential backoff + full jitter,
 *       honouring {@code Retry-After};</li>
 *   <li>does <em>not</em> retry 404 (absent document) or other 4xx (treated as permanent);</li>
 *   <li>maps the final outcome to {@link HttpFetchResult} &mdash; never throws.</li>
 * </ul>
 */
public final class RetryingBoeHttpClient implements BoeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(RetryingBoeHttpClient.class);
    private static final String ACCEPT =
            "application/xml, text/xml, text/html, application/pdf;q=0.9, */*;q=0.8";

    /** Pluggable backoff sleep, so tests run without real delays. */
    @FunctionalInterface
    interface Delayer {
        void sleepMillis(long millis) throws InterruptedException;
    }

    private final HttpTransport transport;
    private final RateLimiter rateLimiter;
    private final BoeSourceConfig config;
    private final Delayer delayer;
    /** Returns a jitter fraction in [0,1). */
    private final DoubleSupplier jitter;

    public RetryingBoeHttpClient(HttpTransport transport, RateLimiter rateLimiter, BoeSourceConfig config) {
        this(transport, rateLimiter, config,
                millis -> Thread.sleep(millis),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryingBoeHttpClient(
            HttpTransport transport,
            RateLimiter rateLimiter,
            BoeSourceConfig config,
            Delayer delayer,
            DoubleSupplier jitter) {
        this.transport = transport;
        this.rateLimiter = rateLimiter;
        this.config = config;
        this.delayer = delayer;
        this.jitter = jitter;
    }

    @Override
    public HttpFetchResult get(URI uri) {
        Map<String, String> headers = Map.of("User-Agent", config.userAgent(), "Accept", ACCEPT);
        int attempt = 1;
        while (true) {
            rateLimiter.acquire();
            HttpResponseBytes response;
            try {
                response = transport.get(uri, headers);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new HttpFetchResult.Failure(ErrorKind.RETRYABLE, 0, "interrupted fetching " + uri);
            } catch (IOException e) {
                HttpFetchResult giveUp = giveUpOrNull(attempt, ErrorKind.RETRYABLE, 0,
                        "network error after " + attempt + " attempt(s): " + e.getMessage());
                if (giveUp != null) {
                    return giveUp;
                }
                backoff(attempt, Optional.empty());
                attempt++;
                continue;
            }

            int status = response.statusCode();
            if (status == 200) {
                byte[] body = response.body();
                if (body == null || body.length == 0) {
                    return new HttpFetchResult.Failure(ErrorKind.PERMANENT, 200, "empty body for " + uri);
                }
                return new HttpFetchResult.Success(body);
            }
            if (status == 404) {
                return new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found: " + uri);
            }
            if (status == 429 || status >= 500) {
                HttpFetchResult giveUp = giveUpOrNull(attempt, ErrorKind.RETRYABLE, status,
                        "status " + status + " after " + attempt + " attempt(s) for " + uri);
                if (giveUp != null) {
                    return giveUp;
                }
                backoff(attempt, retryAfter(response));
                attempt++;
                continue;
            }
            // Other 4xx (403, 400, ...): not retryable.
            return new HttpFetchResult.Failure(ErrorKind.PERMANENT, status, "status " + status + " for " + uri);
        }
    }

    /** Returns a give-up {@link HttpFetchResult.Failure} once attempts are exhausted, else null. */
    private HttpFetchResult giveUpOrNull(int attempt, ErrorKind kind, int status, String detail) {
        if (attempt >= config.maxAttempts()) {
            return new HttpFetchResult.Failure(kind, status, detail);
        }
        return null;
    }

    private void backoff(int attempt, Optional<java.time.Duration> retryAfter) {
        long delayMillis;
        if (retryAfter.isPresent()) {
            delayMillis = Math.max(0, retryAfter.get().toMillis());
        } else {
            int shift = Math.min(attempt - 1, 30);
            long exp = config.baseBackoff().toMillis() * (1L << shift);
            long capped = Math.min(exp, config.maxBackoff().toMillis());
            delayMillis = (long) (capped * jitter.getAsDouble()); // full jitter: uniform in [0, capped)
        }
        if (delayMillis <= 0) {
            return;
        }
        try {
            delayer.sleepMillis(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Parse a {@code Retry-After} header expressed in integer seconds; ignores the HTTP-date form. */
    private static Optional<java.time.Duration> retryAfter(HttpResponseBytes response) {
        return response.firstHeader("Retry-After").flatMap(value -> {
            try {
                return Optional.of(java.time.Duration.ofSeconds(Long.parseLong(value.trim())));
            } catch (NumberFormatException e) {
                log.debug("ignoring non-numeric Retry-After: {}", value);
                return Optional.empty();
            }
        });
    }
}
