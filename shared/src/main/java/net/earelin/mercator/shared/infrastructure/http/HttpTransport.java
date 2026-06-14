package net.earelin.mercator.shared.infrastructure.http;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The thin, retry-free seam over an HTTP GET. Keeping this separate from
 * {@link RetryingBoeHttpClient} lets the politeness/backoff logic be unit-tested with a fake
 * transport, no network required.
 */
public interface HttpTransport {

    /**
     * Perform a single GET. Returns the response (any status); throws only for a genuine
     * transport/network failure (connect/read timeout, connection reset, DNS).
     *
     * @throws IOException          on a network/transport error
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    HttpResponseBytes get(URI uri, Map<String, String> headers) throws IOException, InterruptedException;

    /**
     * One HTTP response: status, raw body bytes, and headers.
     */
    record HttpResponseBytes(int statusCode, byte[] body, Map<String, List<String>> headers) {

        /** First value of {@code name} (case-insensitive), if present. */
        public Optional<String> firstHeader(String name) {
            String target = name.toLowerCase(Locale.ROOT);
            return headers.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(target))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst();
        }
    }
}
