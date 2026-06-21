package net.earelin.mercator.application.rest.admin.imports;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The import endpoint with the toggle enabled, driven over the embedded HTTP server with REST
 * Assured (the injected {@link RequestSpecification} is bound to the running server's port).
 * Flyway/datasource are disabled so the context starts without a Postgres — these tests exercise
 * only the controller/job-store scaffold. {@code given().spec(spec)} starts each request from the
 * server-bound base without mutating the shared spec.
 */
// transactional = false: the import scaffold is DB-free (in-memory JobStore), so we don't want
// micronaut-test wrapping each test in a JDBC transaction (which would need a live Postgres).
@MicronautTest(transactional = false)
@Property(name = "mercator.imports.historical.enabled", value = "true")
@Property(name = "flyway.datasources.default.enabled", value = "false")
// Let HikariCP build its pool bean without an eager connection (Hikari treats a negative timeout as
// "create connections on demand, don't fail at startup") so the context starts with no Postgres.
@Property(name = "datasources.default.initialization-fail-timeout", value = "-1")
class HistoricalImportEnabledTest {

    @Test
    void by_date_import_is_accepted_then_reports_status(RequestSpecification spec) {
        var accepted = given().spec(spec)
                .contentType(ContentType.JSON)
                .body(Map.of("date", "2009-01-02"))
                .when().post("/admin/imports/by-date")
                .andReturn();

        assertThat(accepted.statusCode()).isEqualTo(202);
        var acceptedBody = accepted.jsonPath();
        String jobId = acceptedBody.getString("jobId");
        assertThat(jobId).isNotBlank();
        assertThat(acceptedBody.getString("kind")).isEqualTo("BY_DATE");
        assertThat(acceptedBody.getString("target")).isEqualTo("2009-01-02");
        String statusUrl = acceptedBody.getString("statusUrl");
        assertThat(statusUrl).isEqualTo("/admin/imports/" + jobId);

        var status = given().spec(spec).when().get(statusUrl).andReturn();

        assertThat(status.statusCode()).isEqualTo(200);
        var statusBody = status.jsonPath();
        // Scaffold: no engine yet, so the placeholder marks the job FAILED with an honest reason.
        assertThat(statusBody.getString("status")).isEqualTo("FAILED");
        assertThat(statusBody.getString("detail")).contains("not yet implemented");
    }

    @Test
    void by_month_import_is_accepted(RequestSpecification spec) {
        var response = given().spec(spec)
                .contentType(ContentType.JSON)
                .body(Map.of("month", "2009-01"))
                .when().post("/admin/imports/by-month")
                .andReturn();

        assertThat(response.statusCode()).isEqualTo(202);
        var body = response.jsonPath();
        assertThat(body.getString("kind")).isEqualTo("BY_MONTH");
        assertThat(body.getString("target")).isEqualTo("2009-01");
    }

    @Test
    void malformed_date_is_rejected_with_400(RequestSpecification spec) {
        var response = given().spec(spec)
                .contentType(ContentType.JSON)
                .body(Map.of("date", "not-a-date"))
                .when().post("/admin/imports/by-date")
                .andReturn();

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void unknown_job_id_returns_404(RequestSpecification spec) {
        var response = given().spec(spec).when().get("/admin/imports/imp-unknown").andReturn();

        assertThat(response.statusCode()).isEqualTo(404);
    }
}
