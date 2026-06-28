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
        // Bean/repository definitions are generated for the whole tree: the application driving
        // adapters + wiring, the infrastructure driven adapters (incl. the Micronaut Data
        // repositories), and the domain core where its application services carry the
        // vendor-neutral jakarta.inject (JSR-330) annotations (ADR-0014).
        annotations("net.earelin.mercator.*")
    }
}

application {
    mainClass = "net.earelin.mercator.application.Application"
}

dependencies {
    annotationProcessor("io.micronaut.data:micronaut-data-processor")

    implementation(libs.slf4j.api)
    implementation(libs.jsoup)
    implementation(libs.pdfbox)
    implementation(libs.jakarta.inject.api)

    // Micronaut runtime
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.data:micronaut-data-jdbc")

    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation(libs.junit.jupiter)
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.db)
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

// A dedicated `integration` source set (src/integration/java), defined with the JVM Test Suite
// plugin, holds the tests that exercise interprocess communication across the app's edges — the
// controllers over Micronaut's embedded HTTP server (driven with REST Assured), the JDBC adapters
// against a real Postgres (Testcontainers), and the HTTP transport over a socket. The fast `test`
// suite keeps the pure-logic unit tests (domain, plus the in-memory infrastructure ones). The
// integration suite is NOT wired into `check`; run it on demand with `./gradlew integration`.
testing {
    suites {
        val integration by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                // The production code under test (main classes + resources, incl. the Flyway
                // migrations the persistence tests apply).
                implementation(project())
                // REST Assured wired to the embedded server's port via micronaut-test (version
                // managed by the Micronaut BOM, inherited through the config extension below).
                implementation("io.micronaut.test:micronaut-test-rest-assured")
            }
            targets.configureEach {
                // Only orders the two when both are asked for in one invocation; `check` runs
                // neither integration nor this ordering edge.
                testTask.configure { shouldRunAfter(tasks.named("test")) }
            }
        }
    }
}

// The Micronaut Gradle plugin only auto-wires the `main`/`test` configurations (the platform BOM,
// micronaut-test(-junit5), and the micronaut-inject-java annotation processor). Have the
// `integration` configurations extend their `test` counterparts so the new source set inherits all
// of it — plus the shared test deps (http-client, Testcontainers, assertj-db, flyway, postgresql).
configurations {
    named("integrationImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
    named("integrationCompileOnly") { extendsFrom(configurations.testCompileOnly.get()) }
    named("integrationAnnotationProcessor") { extendsFrom(configurations.testAnnotationProcessor.get()) }
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
