package net.earelin.mercator.shared.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentFetchServiceTest {

    private static final URI URL_XML = URI.create("https://www.boe.es/diario_borme/xml.php?id=BORME-A-2024-1-01");
    private static final URI URL_HTML = URI.create("https://www.boe.es/diario_borme/txt.php?id=BORME-A-2024-1-01");
    private static final URI URL_PDF = URI.create("https://www.boe.es/borme/dias/2024/01/02/pdfs/BORME-A-2024-1-01.pdf");

    private static final DocumentDescriptor DESCRIPTOR =
            new DocumentDescriptor("BORME-A-2024-1-01", "ARAÑÓN", URL_XML, URL_HTML, URL_PDF);
    private static final FetchContext CONTEXT =
            new FetchContext(LocalDate.of(2024, 1, 2), SourcePath.BACKFILL);

    private static final byte[] XML_BODY = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<documento><metadatos><identificador>BORME-A-2024-1-01</identificador></metadatos>"
            + "<texto><p class=\"articulo\">158029 - COMPAÑÍA SL.</p>"
            + "<p class=\"parrafo\">Constitución.</p></texto></documento>")
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] EMPTY_XML_BODY = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<documento><texto></texto></documento>").getBytes(StandardCharsets.UTF_8);

    // Stateful stub doubles — assertions are made against their captured state, not interactions.
    private final RecordingHttpClient http = new RecordingHttpClient();
    private final InMemoryCache cache = new InMemoryCache();
    private final RecordingBormeLog bormeLog = new RecordingBormeLog();
    // The extractors are pure input stubs (no state to assert) — Mockito stubs them concisely.
    private final HtmlTextExtractor htmlExtractor = mock(HtmlTextExtractor.class);
    private final PdfTextExtractor pdfExtractor = mock(PdfTextExtractor.class);
    private final DocumentFetchService service = new DocumentFetchService(
            http, cache, bormeLog, new XmlDocumentParser(), htmlExtractor, pdfExtractor);

    @Test
    void xml_happy_path_fetches_parses_caches_and_logs_fetched() {
        http.respond(URL_XML, success(XML_BODY));

        FetchedDocument doc = assertFetched(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(doc.representation()).isEqualTo(Representation.XML);
        assertThat(doc.paragraphs()).hasSize(2);
        assertThat(cache.store.get("BORME-A-2024-1-01").representation()).isEqualTo(Representation.XML);
        assertThat(bormeLog.entries).singleElement()
                .extracting(BormeLogEntry::status).isEqualTo(BormeLogStatus.FETCHED);
    }

    @Test
    void cache_hit_serves_with_zero_network_requests() {
        cache.put("BORME-A-2024-1-01", new CachedDocument(Representation.XML, XML_BODY, StandardCharsets.UTF_8));

        FetchedDocument doc = assertFetched(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(doc.representation()).isEqualTo(Representation.XML);
        assertThat(http.calls).as("cache hit must not touch the network").isZero();
        assertThat(bormeLog.entries).as("cache hit should not re-record borme_log").isEmpty();
    }

    @Test
    void corrupt_cache_entry_is_ignored_and_refetched() {
        // A cached XML body that no longer parses must not poison the result: fall through to a
        // fresh fetch rather than failing.
        cache.put("BORME-A-2024-1-01",
                new CachedDocument(Representation.XML, "<malformed".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        http.respond(URL_XML, success(XML_BODY));

        FetchedDocument doc = assertFetched(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(doc.representation()).isEqualTo(Representation.XML);
        assertThat(http.calls).as("corrupt cache entry must trigger a re-fetch").isEqualTo(1);
        // The good body replaces the corrupt one in the cache.
        assertThat(cache.store.get("BORME-A-2024-1-01").body()).isEqualTo(XML_BODY);
    }

    @Test
    void empty_xml_falls_back_to_txt() {
        http.respond(URL_XML, success(EMPTY_XML_BODY));
        http.respond(URL_HTML, success("ignored".getBytes(StandardCharsets.UTF_8)));
        when(htmlExtractor.extractText(any())).thenReturn(Optional.of("stripped txt body"));

        FetchedDocument doc = assertFetched(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(doc.representation()).isEqualTo(Representation.TXT);
        assertThat(doc.rawBody()).isEqualTo("stripped txt body");
        assertThat(cache.store.get("BORME-A-2024-1-01").representation()).isEqualTo(Representation.TXT);
    }

    @Test
    void txt_error_page_falls_back_to_pdf() {
        http.respond(URL_XML, failure(ErrorKind.PERMANENT, 404));
        http.respond(URL_HTML, success("error page".getBytes(StandardCharsets.UTF_8)));
        http.respond(URL_PDF, success("%PDF-bytes".getBytes(StandardCharsets.UTF_8)));
        // htmlExtractor returns empty by default → the txt error page is skipped.
        when(pdfExtractor.extractText(any())).thenReturn(Optional.of("pdf text"));

        FetchedDocument doc = assertFetched(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(doc.representation()).isEqualTo(Representation.PDF);
        assertThat(doc.rawBody()).isEqualTo("pdf text");
    }

    @Test
    void all_representations_fail_records_retryable_error() {
        http.respond(URL_XML, failure(ErrorKind.RETRYABLE, 500));
        http.respond(URL_HTML, failure(ErrorKind.PERMANENT, 404));
        http.respond(URL_PDF, failure(ErrorKind.PERMANENT, 404));

        FetchOutcome.Failed failed = assertFailed(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(failed.error().kind()).isEqualTo(ErrorKind.RETRYABLE);
        assertThat(bormeLog.entries).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo(BormeLogStatus.ERROR);
            assertThat(entry.errorKind()).isEqualTo(ErrorKind.RETRYABLE);
        });
    }

    @Test
    void all_representations_permanently_absent_records_permanent_error() {
        http.respond(URL_XML, failure(ErrorKind.PERMANENT, 404));
        http.respond(URL_HTML, failure(ErrorKind.PERMANENT, 404));
        http.respond(URL_PDF, failure(ErrorKind.PERMANENT, 404));

        FetchOutcome.Failed failed = assertFailed(service.fetch(DESCRIPTOR, CONTEXT));

        assertThat(failed.error().kind()).isEqualTo(ErrorKind.PERMANENT);
        assertThat(bormeLog.entries).singleElement()
                .extracting(BormeLogEntry::errorKind).isEqualTo(ErrorKind.PERMANENT);
    }

    // --- helpers / stubs -------------------------------------------------------------------

    private static FetchedDocument assertFetched(FetchOutcome outcome) {
        assertThat(outcome).isInstanceOf(FetchOutcome.Fetched.class);
        return ((FetchOutcome.Fetched) outcome).document();
    }

    private static FetchOutcome.Failed assertFailed(FetchOutcome outcome) {
        assertThat(outcome).isInstanceOf(FetchOutcome.Failed.class);
        return (FetchOutcome.Failed) outcome;
    }

    private static HttpFetchResult success(byte[] body) {
        return new HttpFetchResult.Success(body);
    }

    private static HttpFetchResult failure(ErrorKind kind, int statusCode) {
        return new HttpFetchResult.Failure(kind, statusCode, "status " + statusCode);
    }

    /** Stub that returns canned responses per URL and records how many requests were made. */
    private static final class RecordingHttpClient implements BoeHttpClient {
        private final Map<URI, HttpFetchResult> responses = new HashMap<>();
        private int calls;

        void respond(URI uri, HttpFetchResult result) {
            responses.put(uri, result);
        }

        @Override
        public HttpFetchResult get(URI uri) {
            calls++;
            HttpFetchResult result = responses.get(uri);
            if (result == null) {
                throw new AssertionError("unexpected GET " + uri);
            }
            return result;
        }
    }

    private static final class InMemoryCache implements DocumentCache {
        private final Map<String, CachedDocument> store = new HashMap<>();

        @Override
        public Optional<CachedDocument> get(String bormeId) {
            return Optional.ofNullable(store.get(bormeId));
        }

        @Override
        public void put(String bormeId, CachedDocument document) {
            store.put(bormeId, document);
        }
    }

    private static final class RecordingBormeLog implements BormeLog {
        private final List<BormeLogEntry> entries = new ArrayList<>();

        @Override
        public void record(BormeLogEntry entry) {
            entries.add(entry);
        }
    }
}
