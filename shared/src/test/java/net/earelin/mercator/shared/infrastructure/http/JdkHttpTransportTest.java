package net.earelin.mercator.shared.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import net.earelin.mercator.shared.infrastructure.http.HttpTransport.HttpResponseBytes;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

    @Test
    void returns_status_body_and_headers_under_cap() throws Exception {
        byte[] payload = "hola COMPAÑÍA".getBytes(StandardCharsets.UTF_8);
        HttpServer server = startServer(payload);
        try {
            JdkHttpTransport transport = new JdkHttpTransport(config(1024));

            HttpResponseBytes response = transport.get(baseUri(server), Map.of());

            assertEquals(200, response.statusCode());
            assertArrayEquals(payload, response.body());
            assertEquals("yes", response.firstHeader("X-Test").orElseThrow());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejects_body_larger_than_cap() throws Exception {
        HttpServer server = startServer(new byte[1000]);
        try {
            JdkHttpTransport transport = new JdkHttpTransport(config(100)); // cap below body size
            URI uri = baseUri(server);

            assertThrows(IOException.class, () -> transport.get(uri, Map.of()));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(byte[] body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        HttpHandler handler = exchange -> {
            exchange.getResponseHeaders().add("X-Test", "yes");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        };
        server.createContext("/", handler);
        server.setExecutor(null);
        server.start();
        return server;
    }

    private static URI baseUri(HttpServer server) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/doc");
    }

    private static BoeSourceConfig config(long maxBodyBytes) {
        return new BoeSourceConfig(
                "Mercator-Test/1.0 (+https://example.test; mailto:t@example.test)",
                1.5, 1,
                Duration.ofMillis(100), Duration.ofSeconds(1),
                Duration.ofSeconds(5), Duration.ofSeconds(5), maxBodyBytes);
    }
}
