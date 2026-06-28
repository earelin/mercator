package net.earelin.mercator.acceptance;

import io.restassured.RestAssured;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base for the black-box acceptance tests: boots the full stack — the production image (built by
 * {@code dockerBuild}, passed in via the {@code mercator.acceptance.image} system property), a
 * Postgres, and a WireMock for the external BORME/BOE services — from the project's
 * {@code docker-compose.yml} (the {@code app} profile) once for the suite, then points REST Assured
 * at the app container. The app is opaque, reached only over HTTP.
 *
 * <p>Singleton-container pattern: started on first class load, reaped by Ryuk at JVM exit, so every
 * acceptance class shares the one boot.
 */
abstract class AcceptanceTestSupport {

    private static final String APP_SERVICE = "app";
    private static final int APP_PORT = 8080;

    private static final ComposeContainer ENVIRONMENT =
            new ComposeContainer(new File("docker-compose.yml"))
                    // Host daemon, not the containerised default, so the locally built image is visible.
                    .withLocalCompose(true)
                    // The app/wiremock services are gated behind the `app` profile; db always starts.
                    .withEnv("COMPOSE_PROFILES", "app")
                    .withEnv(stackEnv())
                    // Liveness/routing probe only: once up, Micronaut 404s any unmatched path. The POST
                    // tests are what assert the gated route is actually present.
                    .withExposedService(
                            APP_SERVICE,
                            APP_PORT,
                            Wait.forHttp("/admin/imports/__readiness_probe__")
                                    .forStatusCode(404)
                                    .withStartupTimeout(Duration.ofMinutes(4)));

    /**
     * Compose variables for the acceptance run: a throwaway db password, {@code DB_PORT=0} so the db
     * takes a random host port instead of clashing with a dev db on 5432, and the exact image tag
     * Gradle built (when the system property is absent the compose file falls back to its default tag).
     */
    private static Map<String, String> stackEnv() {
        var env = new HashMap<String, String>();
        env.put("POSTGRES_PASSWORD", "change_me");
        env.put("DB_PORT", "0");
        String image = System.getProperty("mercator.acceptance.image");
        if (image != null && !image.isBlank()) {
            env.put("MERCATOR_IMAGE", image);
        }
        return env;
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
