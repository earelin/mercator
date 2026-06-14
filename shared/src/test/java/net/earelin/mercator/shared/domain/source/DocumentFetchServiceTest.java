package net.earelin.mercator.shared.domain.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(Representation.XML, doc.representation());
        assertEquals(2, doc.paragraphs().size());
        assertTrue(cache.store.containsKey("BORME-A-2024-1-01"));
        assertEquals(1, bormeLog.entries.size());
        assertEquals(BormeLogStatus.FETCHED, bormeLog.entries.get(0).status());
    }

    @Test
    void cache_hit_serves_with_zero_network_requests() {
        cache.put("BORME-A-2024-1-01", new CachedDocument(Representation.XML, XML_BODY, StandardCharsets.UTF_8));
        FakeHttpClient http = new FakeHttpClient(Map.of()); // any call would throw

        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));
        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertEquals(Representation.XML, doc.representation());
        assertEquals(0, http.calls, "cache hit must not touch the network");
        assertTrue(bormeLog.entries.isEmpty(), "cache hit should not re-record borme_log");
    }

    @Test
    void empty_xml_falls_back_to_txt() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, success(EMPTY_XML_BODY),
                URL_HTML, success("ignored".getBytes(StandardCharsets.UTF_8))));
        DocumentFetchService service = service(http, html(Optional.of("stripped txt body")), pdf(Optional.empty()));

        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchedDocument doc = assertFetched(outcome);
        assertEquals(Representation.TXT, doc.representation());
        assertEquals("stripped txt body", doc.rawBody());
        assertEquals(Representation.TXT, cache.store.get("BORME-A-2024-1-01").representation());
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
        assertEquals(Representation.PDF, doc.representation());
        assertEquals("pdf text", doc.rawBody());
    }

    @Test
    void all_representations_fail_records_retryable_error() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, new HttpFetchResult.Failure(ErrorKind.RETRYABLE, 500, "server error"),
                URL_HTML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_PDF, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found")));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));

        FetchOutcome outcome = service.fetch(DESCRIPTOR, CONTEXT);

        FetchOutcome.Failed failed = assertInstanceOf(FetchOutcome.Failed.class, outcome);
        assertEquals(ErrorKind.RETRYABLE, failed.error().kind());
        assertEquals(1, bormeLog.entries.size());
        BormeLogEntry entry = bormeLog.entries.get(0);
        assertEquals(BormeLogStatus.ERROR, entry.status());
        assertEquals(ErrorKind.RETRYABLE, entry.errorKind());
    }

    @Test
    void all_representations_permanently_absent_records_permanent_error() {
        FakeHttpClient http = new FakeHttpClient(Map.of(
                URL_XML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_HTML, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found"),
                URL_PDF, new HttpFetchResult.Failure(ErrorKind.PERMANENT, 404, "not found")));
        DocumentFetchService service = service(http, html(Optional.empty()), pdf(Optional.empty()));

        FetchOutcome.Failed failed = assertInstanceOf(FetchOutcome.Failed.class, service.fetch(DESCRIPTOR, CONTEXT));
        assertEquals(ErrorKind.PERMANENT, failed.error().kind());
        assertEquals(ErrorKind.PERMANENT, bormeLog.entries.get(0).errorKind());
    }

    // --- helpers / fakes -------------------------------------------------------------------

    private DocumentFetchService service(FakeHttpClient http, HtmlTextExtractor html, PdfTextExtractor pdf) {
        return new DocumentFetchService(http, cache, bormeLog, new XmlDocumentParser(), html, pdf);
    }

    private static FetchedDocument assertFetched(FetchOutcome outcome) {
        return assertInstanceOf(FetchOutcome.Fetched.class, outcome).document();
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
