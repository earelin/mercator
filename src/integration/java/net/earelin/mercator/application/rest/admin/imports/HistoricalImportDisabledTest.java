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
 * With the toggle off (the default), the controller bean is not loaded, so the routes are absent —
 * a request gets a 404, proving the capability is gone rather than merely forbidden. Driven over
 * the embedded HTTP server with REST Assured.
 */
// transactional = false: this controller path is DB-free, so we don't want micronaut-test wrapping
// each test in a JDBC transaction (which would need a live Postgres).
@MicronautTest(transactional = false)
@Property(name = "flyway.datasources.default.enabled", value = "false")
// Let HikariCP build its pool bean without an eager connection (Hikari treats a negative timeout as
// "create connections on demand, don't fail at startup") so the context starts with no Postgres.
@Property(name = "datasources.default.initialization-fail-timeout", value = "-1")
class HistoricalImportDisabledTest {

    @Test
    void import_endpoint_is_absent_when_disabled(RequestSpecification spec) {
        var response = given().spec(spec)
                .contentType(ContentType.JSON)
                .body(Map.of("date", "2009-01-02"))
                .when().post("/admin/imports/by-date")
                .andReturn();

        assertThat(response.statusCode()).isEqualTo(404);
    }
}
