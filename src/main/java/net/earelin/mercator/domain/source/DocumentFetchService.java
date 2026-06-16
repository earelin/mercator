package net.earelin.mercator.domain.source;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service implementing {@link DocumentSource}: read-through the cache, otherwise fetch
 * the fallback chain XML &rarr; txt.php &rarr; PDF (ADR-0002) via the polite, bounded-retry
 * {@link BoeHttpClient} (ADR-0018), caching the raw body with its {@link Representation} tag and
 * recording {@code borme_log}. Depends only on domain ports &mdash; no I/O or framework here.
 *
 * <p>A cache hit re-materialises from the stored bytes with <em>zero</em> network requests. On a
 * fresh fetch the first representation that yields usable content wins; if all fail, the document
 * is recorded as a {@code borme_log} ERROR and returned as {@link FetchOutcome.Failed} (fail-soft).
 */
public final class DocumentFetchService implements DocumentSource {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentFetchService.class);
    private static final int MAX_ERROR_DETAIL = 1000;

    private final BoeHttpClient httpClient;
    private final DocumentCache cache;
    private final BormeLog bormeLog;
    private final XmlDocumentParser xmlParser;
    private final HtmlTextExtractor htmlExtractor;
    private final PdfTextExtractor pdfExtractor;

    public DocumentFetchService(
            BoeHttpClient httpClient,
            DocumentCache cache,
            BormeLog bormeLog,
            XmlDocumentParser xmlParser,
            HtmlTextExtractor htmlExtractor,
            PdfTextExtractor pdfExtractor) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.bormeLog = Objects.requireNonNull(bormeLog, "bormeLog");
        this.xmlParser = Objects.requireNonNull(xmlParser, "xmlParser");
        this.htmlExtractor = Objects.requireNonNull(htmlExtractor, "htmlExtractor");
        this.pdfExtractor = Objects.requireNonNull(pdfExtractor, "pdfExtractor");
    }

    @Override
    public FetchOutcome fetch(DocumentDescriptor descriptor, FetchContext context) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(context, "context");
        String bormeId = descriptor.bormeId();

        Optional<FetchedDocument> cached = fromCache(bormeId);
        if (cached.isPresent()) {
            LOG.debug("cache hit for {} ({})", bormeId, cached.get().representation());
            return FetchOutcome.fetched(cached.get());
        }

        List<HttpFetchResult.Failure> failures = new ArrayList<>();
        FetchedDocument doc = tryRepresentation(bormeId, descriptor.urlXml(), Representation.XML, failures);
        if (doc == null) {
            doc = tryRepresentation(bormeId, descriptor.urlHtml(), Representation.TXT, failures);
        }
        if (doc == null) {
            doc = tryRepresentation(bormeId, descriptor.urlPdf(), Representation.PDF, failures);
        }

        if (doc != null) {
            bormeLog.record(BormeLogEntry.fetched(bormeId, context.pubDate(), context.sourcePath()));
            return FetchOutcome.fetched(doc);
        }

        FetchError error = classify(failures);
        LOG.warn("fetch gave up for {}: {} ({})", bormeId, error.detail(), error.kind());
        bormeLog.record(BormeLogEntry.error(bormeId, context.pubDate(), context.sourcePath(), error));
        return FetchOutcome.failed(error);
    }

    private Optional<FetchedDocument> fromCache(String bormeId) {
        Optional<CachedDocument> entry = cache.get(bormeId);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        CachedDocument cachedDoc = entry.get();
        try {
            return materialize(bormeId, cachedDoc.representation(), cachedDoc.body());
        } catch (RuntimeException e) {
            LOG.warn("cached {} ({}) could not be re-materialised; re-fetching: {}",
                    bormeId, cachedDoc.representation(), e.toString());
            return Optional.empty();
        }
    }

    /**
     * Attempt one representation. Returns the materialised document on success, or {@code null}
     * after appending a {@link HttpFetchResult.Failure} describing why it could not be used.
     */
    private FetchedDocument tryRepresentation(
            String bormeId, URI uri, Representation representation, List<HttpFetchResult.Failure> failures) {
        if (uri == null) {
            failures.add(new HttpFetchResult.Failure(
                    ErrorKind.PERMANENT, 0, representation + " url not available"));
            return null;
        }
        HttpFetchResult result = httpClient.get(uri);
        switch (result) {
            case HttpFetchResult.Failure failure -> {
                failures.add(failure);
                return null;
            }
            case HttpFetchResult.Success success -> {
                return materialiseFresh(bormeId, representation, success.body(), failures);
            }
        }
    }

    private FetchedDocument materialiseFresh(
            String bormeId, Representation representation, byte[] body, List<HttpFetchResult.Failure> failures) {
        try {
            Optional<FetchedDocument> doc = materialize(bormeId, representation, body);
            if (doc.isPresent()) {
                cache.put(bormeId, new CachedDocument(representation, body, doc.get().charset()));
                return doc.get();
            }
            failures.add(new HttpFetchResult.Failure(
                    ErrorKind.PERMANENT, 200, representation + " body had no usable content"));
            return null;
        } catch (XmlParseException e) {
            failures.add(new HttpFetchResult.Failure(
                    ErrorKind.PERMANENT, 200, representation + " malformed: " + e.getMessage()));
            return null;
        }
    }

    /**
     * Build a {@link FetchedDocument} from raw bytes for the given representation. Empty means a
     * valid response with no usable content (empty XML, txt error page, unreadable PDF) &mdash; the
     * caller falls back. Throws {@link XmlParseException} only for malformed XML.
     */
    private Optional<FetchedDocument> materialize(String bormeId, Representation representation, byte[] body) {
        return switch (representation) {
            case XML -> {
                XmlDocumentParser.ParsedXml parsed = xmlParser.parse(body);
                yield parsed.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new FetchedDocument(
                                bormeId, Representation.XML, parsed.charset(), parsed.rawBody(),
                                parsed.metadata(), parsed.paragraphs()));
            }
            // For TXT/PDF the body is already decoded text; the charset is nominal (UTF_8) and not
            // load-bearing — a cache re-materialisation re-runs jsoup/PDFBox on the raw bytes and
            // re-detects the real charset. Only the XML representation's charset is meaningful.
            case TXT -> htmlExtractor.extractText(body).map(text -> new FetchedDocument(
                    bormeId, Representation.TXT, StandardCharsets.UTF_8, text, null, List.of()));
            case PDF -> pdfExtractor.extractText(body).map(text -> new FetchedDocument(
                    bormeId, Representation.PDF, StandardCharsets.UTF_8, text, null, List.of()));
        };
    }

    private static FetchError classify(List<HttpFetchResult.Failure> failures) {
        boolean retryable = failures.stream().anyMatch(f -> f.kind() == ErrorKind.RETRYABLE);
        ErrorKind kind = retryable ? ErrorKind.RETRYABLE : ErrorKind.PERMANENT;
        String detail = failures.isEmpty()
                ? "no representations available"
                : failures.stream()
                        .map(f -> f.statusCode() + ":" + f.detail())
                        .collect(Collectors.joining("; "));
        if (detail.length() > MAX_ERROR_DETAIL) {
            detail = detail.substring(0, MAX_ERROR_DETAIL);
        }
        return new FetchError(kind, detail);
    }
}
