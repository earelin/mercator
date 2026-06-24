package net.earelin.mercator.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import net.earelin.mercator.domain.source.DocumentDescriptor;
import net.earelin.mercator.domain.source.SummaryEnumerationService;
import net.earelin.mercator.domain.source.SummaryResult;
import net.earelin.mercator.domain.source.SummaryXmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration test for the BOE summary HTTP client &mdash; the only edge of summary-enumeration that
 * crosses a real process/socket boundary. It wires the production stack
 * ({@link JdkHttpTransport} &rarr; {@link RetryingBoeHttpClient} &rarr;
 * {@link SummaryEnumerationService}) against a <strong>WireMock</strong>-emulated {@code
 * datosabiertos} API over a real socket, and asserts the wire behaviour: request path + {@code
 * Accept}, 404 handling, and the shared retry policy on a transient 5xx. The parsing/mapping logic
 * itself is covered by fast unit tests.
 */
class SummaryEnumerationClientIT {

    private static final String SUMMARY_PATH = "/datosabiertos/api/borme/sumario/";

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void enumerates_a_publication_day_over_the_wire() {
        LocalDate date = LocalDate.of(2026, 6, 9);
        WIREMOCK.stubFor(get(urlEqualTo(SUMMARY_PATH + "20260609"))
                .withHeader("Accept", containing("application/xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml; charset=UTF-8")
                        .withBody(summaryWith("BORME-A-2026-9-01", "BORME-A-2026-9-08"))));

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Published.class);
        assertThat(((SummaryResult.Published) result).documents())
                .extracting(DocumentDescriptor::bormeId)
                .containsExactly("BORME-A-2026-9-01", "BORME-A-2026-9-08");
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo(SUMMARY_PATH + "20260609")));
    }

    @Test
    void non_publication_day_returns_not_published_on_404() {
        LocalDate date = LocalDate.of(2026, 6, 6); // a Saturday
        WIREMOCK.stubFor(get(urlEqualTo(SUMMARY_PATH + "20260606"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(service().enumerate(date))
                .isEqualTo(new SummaryResult.NotPublished(date));
    }

    @Test
    void transient_5xx_is_retried_via_the_shared_policy_then_succeeds() {
        LocalDate date = LocalDate.of(2026, 6, 9);
        String path = SUMMARY_PATH + "20260609";
        WIREMOCK.stubFor(get(urlEqualTo(path)).inScenario("retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        WIREMOCK.stubFor(get(urlEqualTo(path)).inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(summaryWith("BORME-A-2026-9-01"))));

        SummaryResult result = service().enumerate(date);

        assertThat(result).isInstanceOf(SummaryResult.Published.class);
        assertThat(((SummaryResult.Published) result).documents())
                .extracting(DocumentDescriptor::bormeId).containsExactly("BORME-A-2026-9-01");
        // One 503 + one 200 = two requests hit the socket: the retry actually went over the wire.
        WIREMOCK.verify(2, getRequestedFor(urlEqualTo(path)));
    }

    // --- wiring / helpers ------------------------------------------------------------------

    private SummaryEnumerationService service() {
        BoeSourceConfig config = new BoeSourceConfig(
                "Mercator-IT/1.0 (+https://example.test; mailto:it@example.test)",
                1.5, 3,
                Duration.ofMillis(1), Duration.ofMillis(50),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 8L * 1024 * 1024);
        RateLimiter noOpLimiter = () -> { };
        RetryingBoeHttpClient httpClient =
                new RetryingBoeHttpClient(new JdkHttpTransport(config), noOpLimiter, config);
        String base = WIREMOCK.baseUrl() + SUMMARY_PATH;
        return new SummaryEnumerationService(httpClient, new SummaryXmlParser(), base, java.time.Clock.systemUTC());
    }

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
}
