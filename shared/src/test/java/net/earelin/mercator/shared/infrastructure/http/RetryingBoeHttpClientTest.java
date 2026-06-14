package net.earelin.mercator.shared.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.earelin.mercator.shared.domain.source.ErrorKind;
import net.earelin.mercator.shared.domain.source.HttpFetchResult;
import net.earelin.mercator.shared.infrastructure.http.HttpTransport.HttpResponseBytes;
import org.junit.jupiter.api.Test;

class RetryingBoeHttpClientTest {

    private static final URI URI_UNDER_TEST = URI.create("https://www.boe.es/diario_borme/xml.php?id=x");

    private final List<Long> sleeps = new ArrayList<>();

    @Test
    void returns_body_on_200() {
        FakeTransport transport = new FakeTransport().enqueue(ok("hello"));
        HttpFetchResult result = client(transport, 5).get(URI_UNDER_TEST);

        HttpFetchResult.Success success = assertInstanceOf(HttpFetchResult.Success.class, result);
        assertEquals("hello", new String(success.body(), StandardCharsets.UTF_8));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void empty_body_is_permanent_failure() {
        FakeTransport transport = new FakeTransport().enqueue(status(200, new byte[0]));
        HttpFetchResult.Failure failure =
                assertInstanceOf(HttpFetchResult.Failure.class, client(transport, 5).get(URI_UNDER_TEST));
        assertEquals(ErrorKind.PERMANENT, failure.kind());
        assertEquals(200, failure.statusCode());
    }

    @Test
    void not_found_is_permanent_and_not_retried() {
        FakeTransport transport = new FakeTransport().enqueue(status(404, new byte[0]));
        HttpFetchResult.Failure failure =
                assertInstanceOf(HttpFetchResult.Failure.class, client(transport, 5).get(URI_UNDER_TEST));
        assertEquals(ErrorKind.PERMANENT, failure.kind());
        assertEquals(404, failure.statusCode());
        assertEquals(1, transport.served);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void other_client_error_is_permanent_and_not_retried() {
        FakeTransport transport = new FakeTransport().enqueue(status(403, new byte[0]));
        HttpFetchResult.Failure failure =
                assertInstanceOf(HttpFetchResult.Failure.class, client(transport, 5).get(URI_UNDER_TEST));
        assertEquals(ErrorKind.PERMANENT, failure.kind());
        assertEquals(403, failure.statusCode());
        assertEquals(1, transport.served);
    }

    @Test
    void retries_on_429_then_succeeds() {
        FakeTransport transport = new FakeTransport().enqueue(status(429, new byte[0])).enqueue(ok("ok"));
        HttpFetchResult result = client(transport, 5).get(URI_UNDER_TEST);

        assertInstanceOf(HttpFetchResult.Success.class, result);
        assertEquals(2, transport.served);
        assertEquals(1, sleeps.size());
    }

    @Test
    void honours_retry_after_header() {
        FakeTransport transport = new FakeTransport()
                .enqueue(new HttpResponseBytes(503, new byte[0], Map.of("Retry-After", List.of("2"))))
                .enqueue(ok("ok"));

        assertInstanceOf(HttpFetchResult.Success.class, client(transport, 5).get(URI_UNDER_TEST));
        assertEquals(List.of(2000L), sleeps, "Retry-After of 2s overrides computed backoff");
    }

    @Test
    void gives_up_as_retryable_after_max_attempts_on_5xx() {
        FakeTransport transport = new FakeTransport()
                .enqueue(status(500, new byte[0]))
                .enqueue(status(500, new byte[0]))
                .enqueue(status(500, new byte[0]));
        HttpFetchResult.Failure failure =
                assertInstanceOf(HttpFetchResult.Failure.class, client(transport, 3).get(URI_UNDER_TEST));

        assertEquals(ErrorKind.RETRYABLE, failure.kind());
        assertEquals(500, failure.statusCode());
        assertEquals(3, transport.served);
        assertEquals(2, sleeps.size(), "3 attempts → 2 backoffs");
    }

    @Test
    void retries_network_error_then_succeeds() {
        FakeTransport transport = new FakeTransport().enqueue(new IOException("connection reset")).enqueue(ok("ok"));
        assertInstanceOf(HttpFetchResult.Success.class, client(transport, 5).get(URI_UNDER_TEST));
        assertEquals(1, sleeps.size());
    }

    @Test
    void gives_up_as_retryable_when_network_errors_persist() {
        FakeTransport transport = new FakeTransport()
                .enqueue(new IOException("timeout"))
                .enqueue(new IOException("timeout"));
        HttpFetchResult.Failure failure =
                assertInstanceOf(HttpFetchResult.Failure.class, client(transport, 2).get(URI_UNDER_TEST));
        assertEquals(ErrorKind.RETRYABLE, failure.kind());
        assertEquals(0, failure.statusCode());
    }

    // --- helpers ---------------------------------------------------------------------------

    private RetryingBoeHttpClient client(HttpTransport transport, int maxAttempts) {
        BoeSourceConfig config = new BoeSourceConfig(
                "Mercator-Test/1.0 (+https://example.test; mailto:t@example.test)",
                1.5, maxAttempts,
                Duration.ofMillis(100), Duration.ofSeconds(30),
                Duration.ofSeconds(10), Duration.ofSeconds(30), 1024L);
        RetryingBoeHttpClient.Delayer delayer = sleeps::add;
        return new RetryingBoeHttpClient(transport, () -> { }, config, delayer, () -> 1.0);
    }

    private static HttpResponseBytes ok(String body) {
        return status(200, body.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponseBytes status(int code, byte[] body) {
        return new HttpResponseBytes(code, body, Map.of());
    }

    private static final class FakeTransport implements HttpTransport {
        private final Deque<Object> steps = new ArrayDeque<>();
        private int served;

        FakeTransport enqueue(Object responseOrException) {
            steps.add(responseOrException);
            return this;
        }

        @Override
        public HttpResponseBytes get(URI uri, Map<String, String> headers) throws IOException {
            served++;
            Object next = steps.poll();
            if (next == null) {
                throw new AssertionError("no more programmed responses");
            }
            if (next instanceof IOException io) {
                throw io;
            }
            return (HttpResponseBytes) next;
        }
    }
}
