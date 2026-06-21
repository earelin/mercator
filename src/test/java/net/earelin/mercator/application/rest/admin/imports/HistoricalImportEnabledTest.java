package net.earelin.mercator.application.rest.admin.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The import endpoint with the toggle enabled. Flyway/datasource are disabled so the context starts
 * without a Postgres — these tests exercise only the controller/job-store scaffold.
 */
@MicronautTest
@Property(name = "mercator.imports.historical.enabled", value = "true")
@Property(name = "flyway.datasources.default.enabled", value = "false")
class HistoricalImportEnabledTest {

    private final HttpClient client;

    HistoricalImportEnabledTest(@Client("/") HttpClient client) {
        this.client = client;
    }

    @Test
    void by_date_import_is_accepted_then_reports_status() {
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/admin/imports/by-date", Map.of("date", "2009-01-02")),
                JobView.class);

        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        JobView accepted = response.body();
        assertThat(accepted.jobId()).isNotBlank();
        assertThat(accepted.kind()).isEqualTo("BY_DATE");
        assertThat(accepted.target()).isEqualTo("2009-01-02");
        assertThat(accepted.statusUrl()).isEqualTo("/admin/imports/" + accepted.jobId());

        JobView status = client.toBlocking().retrieve(
                HttpRequest.GET(accepted.statusUrl()), JobView.class);

        // Scaffold: no engine yet, so the placeholder marks the job FAILED with an honest reason.
        assertThat(status.status()).isEqualTo("FAILED");
        assertThat(status.detail()).contains("not yet implemented");
    }

    @Test
    void by_month_import_is_accepted() {
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/admin/imports/by-month", Map.of("month", "2009-01")),
                JobView.class);

        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body().kind()).isEqualTo("BY_MONTH");
        assertThat(response.body().target()).isEqualTo("2009-01");
    }

    @Test
    void malformed_date_is_rejected_with_400() {
        var thrown = catchThrowableOfType(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/admin/imports/by-date", Map.of("date", "not-a-date"))));

        assertThat(thrown.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void unknown_job_id_returns_404() {
        var thrown = catchThrowableOfType(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/admin/imports/imp-unknown")));

        assertThat(thrown.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}
