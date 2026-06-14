package net.earelin.mercator.shared.infrastructure.http;

import java.io.IOException;
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

        HttpResponse<byte[]> response =
                client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());

        byte[] body = response.body();
        if (body != null && body.length > maxBodyBytes) {
            throw new IOException("response body " + body.length
                    + " bytes exceeds cap " + maxBodyBytes + " for " + uri);
        }
        return new HttpResponseBytes(response.statusCode(), body, response.headers().map());
    }
}
