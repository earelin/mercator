package net.earelin.mercator.domain.source;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service implementing {@link SummarySource}: GET the BOE {@code datosabiertos} summary
 * for a day via the shared polite/retry {@link BoeHttpClient} (ADR-0018, one HTTP path shared with
 * document-fetch), parse the Secci&oacute;n A items with {@link SummaryXmlParser}, and map the HTTP
 * outcome to a {@link SummaryResult}. Depends only on domain ports &mdash; no I/O or framework here.
 *
 * <p>Mapping: 200 &rarr; {@link SummaryResult.Published} (empty when the day has no Secci&oacute;n A
 * items); 404 &rarr; {@link SummaryResult.NotPublished} (non-publication day); any other give-up, or
 * malformed XML, &rarr; {@link SummaryResult.Failed} with the classified error. Never throws.
 */
public final class SummaryEnumerationService implements SummarySource {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryEnumerationService.class);

    /** The documented summary endpoint (ADR-0003); the {@code AAAAMMDD} date is appended. */
    public static final String DEFAULT_SUMMARY_API_BASE =
            "https://www.boe.es/datosabiertos/api/borme/sumario/";

    /** The BOE publishes on the Madrid calendar, so an open-ended "today" is resolved there. */
    public static final ZoneId PUBLICATION_ZONE = ZoneId.of("Europe/Madrid");

    private static final int HTTP_NOT_FOUND = 404;

    private final BoeHttpClient httpClient;
    private final SummaryXmlParser parser;
    private final String summaryApiBase;
    private final Clock clock;

    /** Uses the live BOE summary endpoint and the {@code Europe/Madrid} publication calendar. */
    public SummaryEnumerationService(BoeHttpClient httpClient, SummaryXmlParser parser) {
        this(httpClient, parser, DEFAULT_SUMMARY_API_BASE, Clock.system(PUBLICATION_ZONE));
    }

    /**
     * @param httpClient     the shared polite/retry BOE HTTP client (reused, not a second path)
     * @param parser         the Secci&oacute;n A summary parser
     * @param summaryApiBase summary endpoint base, the {@code AAAAMMDD} date is appended verbatim
     *                       (e.g. {@value #DEFAULT_SUMMARY_API_BASE}); overridable for tests
     * @param clock          resolves an open-ended "today"; should carry the publication zone
     */
    public SummaryEnumerationService(
            BoeHttpClient httpClient, SummaryXmlParser parser, String summaryApiBase, Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.summaryApiBase = Objects.requireNonNull(summaryApiBase, "summaryApiBase");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SummaryResult enumerate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        URI uri = summaryUri(date);
        HttpFetchResult result = httpClient.get(uri);
        return switch (result) {
            case HttpFetchResult.Success success -> parse(date, success.body());
            case HttpFetchResult.Failure failure when failure.statusCode() == HTTP_NOT_FOUND -> {
                LOG.debug("no publication on {} (404)", date);
                yield new SummaryResult.NotPublished(date);
            }
            case HttpFetchResult.Failure failure -> {
                LOG.warn("summary enumeration gave up for {}: {} ({})",
                        date, failure.detail(), failure.kind());
                yield new SummaryResult.Failed(date, new FetchError(failure.kind(), failure.detail()));
            }
        };
    }

    @Override
    public Stream<SummaryResult> enumerateToToday(LocalDate start) {
        Objects.requireNonNull(start, "start");
        return enumerate(start, LocalDate.now(clock));
    }

    private SummaryResult parse(LocalDate date, byte[] body) {
        try {
            List<DocumentDescriptor> documents = parser.parseSectionA(body);
            return new SummaryResult.Published(date, documents);
        } catch (XmlParseException e) {
            LOG.warn("malformed summary XML for {}: {}", date, e.getMessage());
            return new SummaryResult.Failed(date, new FetchError(ErrorKind.PERMANENT, e.getMessage()));
        }
    }

    private URI summaryUri(LocalDate date) {
        return URI.create(summaryApiBase + date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
