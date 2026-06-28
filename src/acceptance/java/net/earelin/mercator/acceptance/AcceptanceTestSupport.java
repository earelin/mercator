package net.earelin.mercator.acceptance;

import io.restassured.RestAssured;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base for the black-box acceptance tests. It boots the full application stack — the production
 * Docker image (built by {@code ./gradlew dockerBuild} and passed in via the {@code
 * mercator.acceptance.image} system property), a Postgres, and a WireMock standing in for the
 * external BORME/BOE services — once for the whole suite via Docker Compose ({@code
 * docker/acceptance/compose.yaml}), then points REST Assured at the running app container. The
 * application is treated as opaque: tests reach it only over HTTP, never through its classes.
 *
 * <p>The stack is a Testcontainers <em>singleton container</em>: started on first class load and left
 * to Testcontainers' Ryuk reaper to tear down at JVM exit, so every acceptance test class shares the
 * one boot rather than paying the container-startup cost per class.
 */
abstract class AcceptanceTestSupport {

    private static final String APP_SERVICE = "app";
    private static final int APP_PORT = 8080;

    private static final ComposeContainer ENVIRONMENT =
            new ComposeContainer(new File("docker/acceptance/compose.yaml"))
                    // Use the host Docker daemon so the locally built application image is visible;
                    // the default containerised compose runs in an isolated daemon that cannot see it.
                    .withLocalCompose(true)
                    // Activate the `app` compose profile so the application image is launched
                    // alongside its backing services (the others are profile-less and always start).
                    .withEnv("COMPOSE_PROFILES", "app")
                    // Point Compose at the exact image `dockerBuild` produced (its default
                    // `<project>:latest` tag, passed in by the Gradle test task). The compose file
                    // requires MERCATOR_IMAGE, so a run outside Gradle must set it (system property or
                    // env) — there is no implicit default image.
                    .withEnv(appImageEnv())
                    // Generic liveness/routing probe — NOT proof the import endpoint is mounted: once
                    // Micronaut is up it returns 404 for any unmatched path, so this only confirms the
                    // server is serving (connection-refused while it boots keeps the strategy polling).
                    // The POST tests are what actually assert the gated route is present.
                    .withExposedService(
                            APP_SERVICE,
                            APP_PORT,
                            Wait.forHttp("/admin/imports/__readiness_probe__")
                                    .forStatusCode(404)
                                    .withStartupTimeout(Duration.ofMinutes(4)));

    /**
     * Maps {@code MERCATOR_IMAGE} to the image tag Gradle built, or an empty map when the system
     * property is absent (a run outside Gradle, which must then supply MERCATOR_IMAGE itself).
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
