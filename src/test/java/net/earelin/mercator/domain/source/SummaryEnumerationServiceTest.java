package net.earelin.mercator.domain.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SummaryEnumerationServiceTest {

    private static final String BASE = "https://test.local/sumario/";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final RecordingHttpClient http = new RecordingHttpClient();

    @Test
    void publication_day_returns_published_with_section_a_descriptors() {
        LocalDate date = LocalDate.of(2026, 6, 9);
        http.respond(date, success(summaryWith("BORME-A-2026-9-01", "BORME-A-2026-9-08")));

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Published.class);
        SummaryResult.Published published = (SummaryResult.Published) result;
        assertThat(published.date()).isEqualTo(date);
        assertThat(published.documents()).extracting(DocumentDescriptor::bormeId)
                .containsExactly("BORME-A-2026-9-01", "BORME-A-2026-9-08");
    }

    @Test
    void not_found_returns_not_published() {
        LocalDate date = LocalDate.of(2026, 6, 6); // a Saturday
        http.respond(date, failure(ErrorKind.PERMANENT, 404));

        assertThat(service().enumerate(date))
                .isEqualTo(new SummaryResult.NotPublished(date));
    }

    @Test
    void empty_section_a_is_a_published_day_with_no_documents() {
        LocalDate date = LocalDate.of(2026, 6, 10);
        http.respond(date, success(summaryWith())); // 200, no Sección A items

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Published.class);
        assertThat(((SummaryResult.Published) result).documents()).isEmpty();
    }

    @Test
    void retry_give_up_returns_failed_preserving_error_kind() {
        LocalDate date = LocalDate.of(2026, 6, 9);
        http.respond(date, failure(ErrorKind.RETRYABLE, 503));

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Failed.class);
        assertThat(((SummaryResult.Failed) result).error().kind()).isEqualTo(ErrorKind.RETRYABLE);
    }

    @Test
    void malformed_summary_xml_returns_failed_permanent() {
        LocalDate date = LocalDate.of(2026, 6, 9);
        http.respond(date, success("<response><data".getBytes(StandardCharsets.UTF_8)));

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Failed.class);
        assertThat(((SummaryResult.Failed) result).error().kind()).isEqualTo(ErrorKind.PERMANENT);
    }

    @Test
    void range_iteration_skips_holidays_and_empty_days_without_gaps_or_duplicates() {
        // An arbitrary span mixing published, non-publication (404) and empty-Sección-A days.
        LocalDate start = LocalDate.of(2026, 6, 8);
        LocalDate end = LocalDate.of(2026, 6, 12);
        http.respond(LocalDate.of(2026, 6, 8), success(summaryWith("BORME-A-2026-9-01", "BORME-A-2026-9-08")));
        http.respond(LocalDate.of(2026, 6, 9), failure(ErrorKind.PERMANENT, 404)); // holiday
        http.respond(LocalDate.of(2026, 6, 10), success(summaryWith()));           // empty
        http.respond(LocalDate.of(2026, 6, 11), success(summaryWith("BORME-A-2026-9-28")));
        http.respond(LocalDate.of(2026, 6, 12), failure(ErrorKind.PERMANENT, 404)); // weekend

        List<SummaryResult> results = service().enumerate(start, end).toList();

        // One result per calendar day, in order — no day skipped at the iteration level.
        assertThat(results).extracting(SummaryResult::date).containsExactly(
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));
        // The 404 days are NotPublished; no Failed result anywhere.
        assertThat(results).filteredOn(r -> r instanceof SummaryResult.NotPublished).hasSize(2);
        assertThat(results).noneMatch(r -> r instanceof SummaryResult.Failed);
        // Flattened document set across the span: every published doc exactly once, no duplicates.
        List<String> ids = results.stream()
                .filter(r -> r instanceof SummaryResult.Published)
                .flatMap(r -> ((SummaryResult.Published) r).documents().stream())
                .map(DocumentDescriptor::bormeId)
                .toList();
        assertThat(ids).containsExactly("BORME-A-2026-9-01", "BORME-A-2026-9-08", "BORME-A-2026-9-28");
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void empty_range_when_end_precedes_start() {
        assertThat(service().enumerate(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 8)))
                .isEmpty();
        assertThat(http.calls).as("an inverted range must not touch the network").isZero();
    }

    @Test
    void enumerate_to_today_resolves_the_boundary_in_europe_madrid() {
        // 2026-06-10T23:30Z is already 2026-06-11 in Madrid (UTC+2) — so "today" is the 11th, not
        // the 10th a UTC clock would report. The last enumerated day must be the Madrid date.
        Clock clock = Clock.fixed(Instant.parse("2026-06-10T23:30:00Z"), MADRID);
        http.respond(LocalDate.of(2026, 6, 9), failure(ErrorKind.PERMANENT, 404));
        http.respond(LocalDate.of(2026, 6, 10), success(summaryWith("BORME-A-2026-9-01")));
        http.respond(LocalDate.of(2026, 6, 11), success(summaryWith("BORME-A-2026-9-02")));

        List<SummaryResult> results =
                serviceWithClock(clock).enumerateToToday(LocalDate.of(2026, 6, 9)).toList();

        assertThat(results).extracting(SummaryResult::date).containsExactly(
                LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11));
    }

    // --- helpers / stubs -------------------------------------------------------------------

    private SummaryEnumerationService service() {
        return serviceWithClock(Clock.systemUTC());
    }

    private SummaryEnumerationService serviceWithClock(Clock clock) {
        return new SummaryEnumerationService(http, new SummaryXmlParser(), BASE, clock);
    }

    private static URI summaryUri(LocalDate date) {
        return URI.create(BASE + date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** A minimal Sección A summary listing the given identifiers (titulo = id), or empty Sección A. */
    private static byte[] summaryWith(String... identifiers) {
        StringBuilder items = new StringBuilder();
        for (String id : identifiers) {
            items.append("<item><identificador>").append(id).append("</identificador>")
                    .append("<titulo>").append(id).append("</titulo>")
                    .append("<url_xml>https://www.boe.es/diario_borme/xml.php?id=").append(id)
                    .append("</url_xml></item>");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<response><data><sumario><diario numero=\"9\">"
                + "<seccion codigo=\"A\" nombre=\"Empresarios. Actos inscritos\">"
                + items + "</seccion></diario></sumario></data></response>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static HttpFetchResult success(byte[] body) {
        return new HttpFetchResult.Success(body);
    }

    private static HttpFetchResult failure(ErrorKind kind, int statusCode) {
        return new HttpFetchResult.Failure(kind, statusCode, "status " + statusCode);
    }

    /** Stub returning a canned response per summary date, recording how many requests were made. */
    private static final class RecordingHttpClient implements BoeHttpClient {
        private final Map<URI, HttpFetchResult> responses = new HashMap<>();
        private int calls;

        void respond(LocalDate date, HttpFetchResult result) {
            responses.put(summaryUri(date), result);
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
}
