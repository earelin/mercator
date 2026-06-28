package net.earelin.mercator.acceptance;

import io.restassured.RestAssured;
import java.io.File;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base for the black-box acceptance tests. It boots the full application stack — the production
 * Docker image ({@code mercator:acceptance}, built by {@code ./gradlew dockerBuild}) plus a Postgres
 * — once for the whole suite via Docker Compose ({@code docker/acceptance/compose.yaml}), then points
 * REST Assured at the running app container. The application is treated as opaque: tests reach it
 * only over HTTP, never through its classes.
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
                    // alongside its backing db (the db itself is profile-less and always starts).
                    .withEnv("COMPOSE_PROFILES", "app")
                    // Ready once the API answers: a GET for a non-existent import job returns a
                    // definite 404 only when the server is up and routing (connection-refused while
                    // it boots keeps the strategy polling).
                    .withExposedService(
                            APP_SERVICE,
                            APP_PORT,
                            Wait.forHttp("/admin/imports/__readiness_probe__")
                                    .forStatusCode(404)
                                    .withStartupTimeout(Duration.ofMinutes(4)));

    static {
        ENVIRONMENT.start();
    }

    @BeforeAll
    static void point_rest_assured_at_the_running_container() {
        RestAssured.baseURI = "http://" + ENVIRONMENT.getServiceHost(APP_SERVICE, APP_PORT);
        RestAssured.port = ENVIRONMENT.getServicePort(APP_SERVICE, APP_PORT);
    }
}
