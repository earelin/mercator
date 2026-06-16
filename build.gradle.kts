import de.aaschmid.gradle.plugins.cpd.Cpd

plugins {
    // The Micronaut application: read-only API, the daily-incremental scheduler, and the gated
    // historical-import endpoint, all in one artifact.
    alias(libs.plugins.micronaut.application)
    // CPD (PMD's copy/paste detector) for code-duplication checking.
    alias(libs.plugins.cpd)
    checkstyle
}

group = "net.earelin.mercator"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        // All Micronaut beans (controllers, @Factory wiring, the @Scheduled job) live under
        // net.earelin.mercator.server; the domain/infrastructure packages stay framework-free.
        annotations("net.earelin.mercator.server.*")
    }
}

application {
    mainClass = "net.earelin.mercator.server.Application"
}

dependencies {
    // Domain/infrastructure libraries (formerly the `shared` module). slf4j is plain
    // `implementation` now that there is no external library consumer; jsoup/PDFBox back the
    // document-fetch fallbacks (txt.php HTML, last-resort PDF — ADR-0002, ADR-0012).
    implementation(libs.slf4j.api)
    implementation(libs.jsoup)
    implementation(libs.pdfbox)

    // Micronaut runtime: HTTP API, JSON serialization, Flyway migrations, JDBC/HikariCP.
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation(libs.junit.jupiter)
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    // assertj-db is the standard for database-backed checks (persistence/borme_log tests).
    testImplementation(libs.assertj.db)
    // Testcontainers stands up a real Postgres 18 so the JDBC adapters are exercised against the
    // actual ON CONFLICT upsert and CHECK constraints. The canonical schema lives in this module
    // (src/main/resources/db/migration); the tests apply it with Flyway — one source of truth.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    isIgnoreFailures = false
}

// CPD scans the Java sources and runs as part of `check`. minimumTokenCount is deliberately
// conservative to flag real copy/paste.
cpd {
    language = "java"
    toolVersion = libs.versions.pmd.get()
}

tasks.named<Cpd>("cpdCheck") {
    minimumTokenCount = 100
    ignoreFailures = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Let a DOCKER_API_VERSION env var pin docker-java's Remote API version for the Testcontainers
    // worker (no-op when unset). Some recent daemons reject docker-java's default (1.32); docker-java
    // reads this as the `api.version` system property, so bridge the familiar env var across.
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
}
