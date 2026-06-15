plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    api(libs.slf4j.api)

    // BOE per-document fetch fallbacks: jsoup strips txt.php HTML chrome,
    // PDFBox extracts text from the authentic-last-resort PDF (ADR-0002, ADR-0012).
    implementation(libs.jsoup)
    implementation(libs.pdfbox)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    // assertj-db is the standard for database-backed checks (persistence/borme_log tests).
    testImplementation(libs.assertj.db)
    // Testcontainers stands up a real Postgres 18 so the JDBC borme_log adapter is exercised
    // against the actual ON CONFLICT upsert and CHECK constraints. The canonical schema lives in
    // this module (src/main/resources/db/migration); the test applies it with Flyway, so there is
    // a single source of truth shared by the server runtime and these tests.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Let a DOCKER_API_VERSION env var pin docker-java's Remote API version for the Testcontainers
    // worker (no-op when unset). Some recent daemons reject docker-java's default (1.32); docker-java
    // reads this as the `api.version` system property, so bridge the familiar env var across.
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
}
