package net.earelin.mercator.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * End-to-end acceptance test of the historical-import admin endpoint against the deployed Docker
 * image. Unlike the integration tests (which run the controller over an embedded server with an
 * in-memory job store), this drives the real container — wired to a real Postgres and started with
 * the {@code prod} profile and the import gate enabled — over the network. Exercising it proves the
 * image builds, starts, connects to the database, applies the Flyway migrations and serves the API.
 */
class HistoricalImportAcceptanceTest extends AcceptanceTestSupport {

    @Test
    void by_date_import_is_accepted_then_reports_status() {
        var accepted = postImport("/admin/imports/by-date", "{\"date\": \"2009-01-02\"}");

        assertThat(accepted.statusCode()).isEqualTo(202);

        // Acceptance level: smoke the happy path end-to-end — the deployed container accepts the
        // request and hands back a pollable status URL that then answers. The exact response-body
        // contract (jobId/kind/target shapes) is asserted by the integration + conformance suites.
        String statusUrl = accepted.jsonPath().getString("statusUrl");
        assertThat(statusUrl).startsWith("/admin/imports/");

        var status = given().when().get(statusUrl).andReturn();
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.jsonPath().getString("status")).isNotBlank();
    }

    @Test
    void by_month_import_is_accepted() {
        var response = postImport("/admin/imports/by-month", "{\"month\": \"2009-01\"}");

        assertThat(response.statusCode()).isEqualTo(202);
        var body = response.jsonPath();
        assertThat(body.getString("kind")).isEqualTo("BY_MONTH");
        assertThat(body.getString("target")).isEqualTo("2009-01");
    }

    @Test
    void malformed_date_is_rejected_with_400() {
        var response = postImport("/admin/imports/by-date", "{\"date\": \"not-a-date\"}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void unknown_job_id_returns_404() {
        var response = given().when().get("/admin/imports/imp-unknown").andReturn();

        assertThat(response.statusCode()).isEqualTo(404);
    }

    /** POSTs a JSON body to an import endpoint and returns the raw response for assertion. */
    private static Response postImport(String path, String jsonBody) {
        return given()
                .contentType(ContentType.JSON)
                .body(jsonBody)
                .when().post(path)
                .andReturn();
    }
}
