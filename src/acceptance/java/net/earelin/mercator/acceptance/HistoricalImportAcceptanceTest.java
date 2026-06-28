package net.earelin.mercator.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * End-to-end acceptance test of the import admin endpoint against the deployed image: drives the real
 * container (real Postgres, {@code prod} profile, import gate on) over the network — proving the image
 * builds, starts, migrates and serves the API — where the integration tests use an embedded server.
 */
class HistoricalImportAcceptanceTest extends AcceptanceTestSupport {

    @Test
    void by_date_import_is_accepted_then_reports_status() {
        var accepted = postImport("/admin/imports/by-date", "{\"date\": \"2009-01-02\"}");

        assertThat(accepted.statusCode()).isEqualTo(202);

        // Acceptance altitude: smoke that a request is accepted and the status URL then answers; the
        // exact response-body contract is covered by the integration + conformance suites.
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

    private static Response postImport(String path, String jsonBody) {
        return given()
                .contentType(ContentType.JSON)
                .body(jsonBody)
                .when().post(path)
                .andReturn();
    }
}
