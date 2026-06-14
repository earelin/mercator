package net.earelin.mercator.shared.domain.source;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final InMemoryCache cache = new InMemoryCache();
    private final RecordingBormeLog bormeLog = new RecordingBormeLog();

    @Test
    void xml_happy_path_fetches_parses_caches_and_logs_fetched() {
        FakeHttpClient http = new FakeHttpClient(Map.of(URL_XML, success(XML_BODY)));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));

        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertThat(doc.representation()).isEqualTo(Representation.XML);
        assertThat(doc.paragraphs()).hasSize(2);
        assertThat(cache.store).containsKey("BORME-A-2024-1-01");
        assertThat(bormeLog.entries).hasSize(1);
        assertThat(bormeLog.entries.get(0).status()).isEqualTo(BormeLogStatus.FETCHED);
    }

    @Test
    void cache_hit_serves_with_zero_network_requests() {
        cache.put("BORME-A-2024-1-01", new CachedDocument(Representation.XML, XML_BODY, StandardCharsets.UTF_8));
        FakeHttpClient http = new FakeHttpClient(Map.of()); // any call would throw

        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));
        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
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
        FakeHttpClient http = new FakeHttpClient(Map.of(URL_XML, success(XML_BODY)));

        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));
        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertThat(doc.representation()).isEqualTo(Representation.XML);
        assertThat(http.calls).as("corrupt cache entry must trigger a re-fetch").isEqualTo(1);
        // The good body replaces the corrupt one in the cache.
        assertThat(cache.store.get("BORME-A-2024-1-01").body()).isEqualTo(XML_BODY);
    }

    @Test
    void empty_xml_falls_back_to_txt() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, success(EMPTY_XML_BODY),
                URL_HTML, success("ignored".getBytes(StandardCharsets.UTF_8))));
        DocumentFetchService service = service(http, html(Optional.of("stripped txt body")), pdf(Optional.empty()));

        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertThat(doc.representation()).isEqualTo(Representation.TXT);
        assertThat(doc.rawBody()).isEqualTo("stripped txt body");
        assertThat(cache.store.get("BORME-A-2024-1-01").representation()).isEqualTo(Representation.TXT);
    }

    @Test
    void txt_error_page_falls_back_to_pdf() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_HTML, success("error page".getBytes(StandardCharsets.UTF_8)),
                URL_PDF, success("%PDF-bytes".getBytes(StandardCharsets.UTF_8))));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.of("pdf text")));

        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertThat(doc.representation()).isEqualTo(Representation.PDF);
        assertThat(doc.rawBody()).isEqualTo("pdf text");
    }

    @Test
    void all_representations_fail_records_retryable_error() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, new HttpFetchResult.Failure(ErrorKind.RETRYABLE, 500, "server error"),
                URL_HTML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_PDF, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found")));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));

        FetchOutcome.Failed failed = assertFailed(service.fetch(DESCRIPTOR, CONTEXT));
        assertThat(failed.error().kind()).isEqualTo(ErrorKind.RETRYABLE);
        assertThat(bormeLog.entries).hasSize(1);
        BormeLogEntry entry = bormeLog.entries.get(0);
        assertThat(entry.status()).isEqualTo(BormeLogStatus.ERROR);
        assertThat(entry.errorKind()).isEqualTo(ErrorKind.RETRYABLE);
    }

    @Test
    void all_representations_permanently_absent_records_permanent_error() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_HTML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_PDF, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found")));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));

        FetchOutcome.Failed failed = assertFailed(service.fetch(DESCRIPTOR, CONTEXT));
        assertThat(failed.error().kind()).isEqualTo(ErrorKind.PERMANENT);
        assertThat(bormeLog.entries.get(0).errorKind()).isEqualTo(ErrorKind.PERMANENT);
    }

    // --- helpers / fakes -------------------------------------------------------------------

    private DocumentFetchService service(FakeHttpClient http, HtmlTextExtractor html, PdfTextExtractor pdf) {
        return new DocumentFetchService(http, cache, bormeLog, new XmlDocumentParser(), html, pdf);
    }

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

    private static HtmlTextExtractor html(Optional<String> result) {
        return body -> result;
    }

    private static PdfTextExtractor pdf(Optional<String> result) {
        return body -> result;
    }

    private static final class FakeHttpClient implements BoeHttpClient {
        private final Map<URI, HttpFetchResult> responses;
        private int calls;

        FakeHttpClient(Map<URI, HttpFetchResult> responses) {
            this.responses = responses;
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
