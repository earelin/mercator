package net.earelin.mercator.shared.infrastructure.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * {@link HttpTransport} backed by the JDK {@code java.net.http.HttpClient} (no third-party HTTP
 * dependency &mdash; the cheap-and-simple mandate). One client instance is reused for connection
 * pooling; timeouts and the body-size cap come from {@link BoeSourceConfig}.
 */
public final class JdkHttpTransport implements HttpTransport {

    private static final int READ_CHUNK = 8192;

    private final HttpClient client;
    private final Duration readTimeout;
    private final long maxBodyBytes;

    public JdkHttpTransport(BoeSourceConfig config) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.readTimeout = config.readTimeout();
        this.maxBodyBytes = config.maxBodyBytes();
    }

    @Override
    public HttpResponseBytes get(URI uri, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(readTimeout);
        headers.forEach(request::header);

        // Stream the body so the cap genuinely bounds heap use: we abort as soon as the read
        // crosses maxBodyBytes, never materialising an arbitrarily large array first.
        HttpResponse<InputStream> response =
                client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream in = response.body()) {
            body = readCapped(in, uri);
        }
        return new HttpResponseBytes(response.statusCode(), body, response.headers().map());
    }

    private byte[] readCapped(InputStream in, URI uri) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_CHUNK];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBodyBytes) {
                throw new IOException("response body exceeds cap " + maxBodyBytes + " bytes for " + uri);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
