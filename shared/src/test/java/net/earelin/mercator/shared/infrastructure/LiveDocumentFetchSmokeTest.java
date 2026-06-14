package net.earelin.mercator.shared.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.earelin.mercator.shared.domain.source.DocumentDescriptor;
import net.earelin.mercator.shared.domain.source.DocumentFetchService;
import net.earelin.mercator.shared.domain.source.FetchContext;
import net.earelin.mercator.shared.domain.source.FetchOutcome;
import net.earelin.mercator.shared.domain.source.Representation;
import net.earelin.mercator.shared.domain.source.SourcePath;
import net.earelin.mercator.shared.domain.source.XmlDocumentParser;
import net.earelin.mercator.shared.infrastructure.cache.DiskDocumentCache;
import net.earelin.mercator.shared.infrastructure.extract.JsoupHtmlTextExtractor;
import net.earelin.mercator.shared.infrastructure.extract.PdfBoxPdfTextExtractor;
import net.earelin.mercator.shared.infrastructure.http.BoeSourceConfig;
import net.earelin.mercator.shared.infrastructure.http.HttpTransport;
import net.earelin.mercator.shared.infrastructure.http.JdkHttpTransport;
import net.earelin.mercator.shared.infrastructure.http.RetryingBoeHttpClient;
import net.earelin.mercator.shared.infrastructure.http.TokenBucketRateLimiter;
import net.earelin.mercator.shared.infrastructure.log.LoggingBormeLog;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.time.LocalDate;

/**
 * Live end-to-end smoke test against the real BOE. Disabled by default (hits the network); run it
 * manually to validate the wiring and the document-fetch acceptance criteria. Update {@code ID} to
 * any real Secci&oacute;n A document if this one ages out.
 */
@Disabled("hits the live BOE network; run manually")
class LiveDocumentFetchSmokeTest {

    private static final String ID = "BORME-A-2024-1-01";
    private static final URI URL_XML = URI.create("https://www.boe.es/diario_borme/xml.php?id=" + ID);
    private static final URI URL_HTML = URI.create("https://www.boe.es/diario_borme/txt.php?id=" + ID);
    private static final URI URL_PDF =
            URI.create("https://www.boe.es/borme/dias/2024/01/02/pdfs/" + ID + ".pdf");

    @Test
    void fetches_real_xml_then_serves_from_cache_without_new_requests(@TempDir Path cacheDir) {
        BoeSourceConfig config = BoeSourceConfig.defaults(
                "Mercator-SmokeTest/0.1 (+https://github.com/earelin/mercator; mailto:xavier.carriba@gmail.com)");
        CountingTransport transport = new CountingTransport(new JdkHttpTransport(config));
        RetryingBoeHttpClient http =
                new RetryingBoeHttpClient(transport, new TokenBucketRateLimiter(config.requestsPerSecond()), config);
        DocumentFetchService service = new DocumentFetchService(
                http, new DiskDocumentCache(cacheDir), new LoggingBormeLog(),
                new XmlDocumentParser(), new JsoupHtmlTextExtractor(), new PdfBoxPdfTextExtractor());

        DocumentDescriptor descriptor = new DocumentDescriptor(ID, "ARABA/ÁLAVA", URL_XML, URL_HTML, URL_PDF);
        FetchContext context = new FetchContext(LocalDate.of(2024, 1, 2), SourcePath.BACKFILL);

        FetchOutcome first = service.fetch(descriptor, context);
        var fetched = assertInstanceOf(FetchOutcome.Fetched.class, first);
        assertEquals(Representation.XML, fetched.document().representation());
        assertTrue(fetched.document().rawBody().contains("texto") || !fetched.document().paragraphs().isEmpty());
        int requestsAfterFirst = transport.gets.get();
        assertTrue(requestsAfterFirst >= 1);

        FetchOutcome second = service.fetch(descriptor, context);
        assertInstanceOf(FetchOutcome.Fetched.class, second);
        assertEquals(requestsAfterFirst, transport.gets.get(), "cache hit must issue zero new requests");
    }

    private static final class CountingTransport implements HttpTransport {
        private final HttpTransport delegate;
        private final AtomicInteger gets = new AtomicInteger();

        CountingTransport(HttpTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpResponseBytes get(URI uri, Map<String, String> headers)
                throws IOException, InterruptedException {
            gets.incrementAndGet();
            return delegate.get(uri, headers);
        }
    }
}
