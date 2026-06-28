package net.earelin.mercator.acceptance;

import io.restassured.RestAssured;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base for the black-box acceptance tests: boots the full stack — the production image (built by
 * {@code dockerBuild}, passed in via the {@code mercator.acceptance.image} system property), a
 * Postgres, and a WireMock for the external BORME/BOE services — once for the suite via Docker
 * Compose, then points REST Assured at the app container. The app is opaque, reached only over HTTP.
 *
 * <p>Singleton-container pattern: started on first class load, reaped by Ryuk at JVM exit, so every
 * acceptance class shares the one boot.
 */
abstract class AcceptanceTestSupport {

    private static final String APP_SERVICE = "app";
    private static final int APP_PORT = 8080;

    private static final ComposeContainer ENVIRONMENT =
            new ComposeContainer(new File("docker/acceptance/compose.yaml"))
                    // Host daemon, not the containerised default, so the locally built image is visible.
                    .withLocalCompose(true)
                    // The app image is gated behind the `app` profile; the backing services always start.
                    .withEnv("COMPOSE_PROFILES", "app")
                    .withEnv(appImageEnv())
                    // Liveness/routing probe only: once up, Micronaut 404s any unmatched path. The POST
                    // tests are what assert the gated route is actually present.
                    .withExposedService(
                            APP_SERVICE,
                            APP_PORT,
                            Wait.forHttp("/admin/imports/__readiness_probe__")
                                    .forStatusCode(404)
                                    .withStartupTimeout(Duration.ofMinutes(4)));

    /**
     * {@code MERCATOR_IMAGE} bound to the image tag Gradle built, or empty when the system property is
     * absent — the compose file requires the variable, so a run outside Gradle must supply it.
     */
    private static Map<String, String> appImageEnv() {
        String image = System.getProperty("mercator.acceptance.image");
        return image == null || image.isBlank() ? Map.of() : Map.of("MERCATOR_IMAGE", image);
    }

    static {
        ENVIRONMENT.start();
    }

    @BeforeAll
    static void point_rest_assured_at_the_running_container() {
        RestAssured.baseURI = "http://" + ENVIRONMENT.getServiceHost(APP_SERVICE, APP_PORT);
        RestAssured.port = ENVIRONMENT.getServicePort(APP_SERVICE, APP_PORT);
    }
}
