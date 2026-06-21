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
 * With the toggle off (the default), the controller bean is not loaded, so the routes are absent —
 * a request gets a 404, proving the capability is gone rather than merely forbidden.
 */
@MicronautTest
@Property(name = "flyway.datasources.default.enabled", value = "false")
class HistoricalImportDisabledTest {

    private final HttpClient client;

    HistoricalImportDisabledTest(@Client("/") HttpClient client) {
        this.client = client;
    }

    @Test
    void import_endpoint_is_absent_when_disabled() {
        var thrown = catchThrowableOfType(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/admin/imports/by-date", Map.of("date", "2009-01-02"))));

        assertThat(thrown.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}
