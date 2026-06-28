import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import de.aaschmid.gradle.plugins.cpd.Cpd
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    // The Micronaut application: read-only API, the daily-incremental scheduler, and the gated
    // historical-import endpoint, all in one artifact.
    alias(libs.plugins.micronaut.application)
    // CPD (PMD's copy/paste detector) for code-duplication checking.
    alias(libs.plugins.cpd)
    // Error Prone (javac bug-pattern checks) — also the host for NullAway's nullness analysis.
    alias(libs.plugins.errorprone)
    // SpotBugs (bytecode bug-pattern analysis) — hosts the Find Security Bugs detector pack.
    alias(libs.plugins.spotbugs)
    checkstyle
    pmd
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

    // Error Prone compiler plugin + NullAway nullness checker. The gradle-errorprone-plugin wires
    // the `errorprone` configuration onto every JavaCompile task's processor path.
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // Find Security Bugs — a SpotBugs detector pack adding security bug patterns (injection, weak
    // crypto, SSRF, path traversal…). Loaded into SpotBugs via the `spotbugsPlugins` configuration.
    spotbugsPlugins(libs.findsecbugs.plugin)

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
                // WireMock (standalone/shaded, so its Jetty/Jackson stay isolated from the
                // Micronaut BOM) emulates the BOE datosabiertos API for the summary HTTP client IT.
                implementation(libs.wiremock)
            }
            targets.configureEach {
                // Only orders the two when both are asked for in one invocation; `check` runs
                // neither integration nor this ordering edge.
                testTask.configure { shouldRunAfter(tasks.named("test")) }
            }
        }

        // Black-box end-to-end tests (src/acceptance/java): build the production image and drive it
        // over HTTP via Docker Compose + Testcontainers, never touching the production classes. Wired
        // into neither `check` nor `build`; run on demand (needs Docker) with `./gradlew acceptance`.
        val acceptance by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                // Deliberately NOT extending the `test` configurations (unlike `integration`): that
                // pulls in the Micronaut platform BOM, which force-upgrades testcontainers/rest-assured
                // past the catalog pins. Micronaut-free keeps the suite isolated and the pins holding.
                implementation(libs.assertj.core)
                implementation(libs.rest.assured)
                implementation(libs.testcontainers)
                runtimeOnly(libs.logback.classic)
            }
            targets.configureEach {
                testTask.configure {
                    dependsOn(tasks.named("dockerBuild"))
                    shouldRunAfter(tasks.named("test"), tasks.named("integration"))
                    // Pull exactly the image `dockerBuild` produced (its default `<project>:latest`)
                    // without repurposing that global default tag; forwarded to Compose as MERCATOR_IMAGE.
                    systemProperty("mercator.acceptance.image", "${project.name}:latest")
                }
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

// PMD source analysis. Runs as part of `check` (pmdMain + pmdTest, incl. the integration source
// set). The curated ruleset lives in config/pmd; the bundled category rulesets are disabled so only
// the rules we opt into apply.
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetConfig = resources.text.fromFile(rootProject.layout.projectDirectory.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()
    isConsoleOutput = true
    isIgnoreFailures = false
}

// Error Prone runs inside javac on every compile task (main, test and integration), keeping its
// default bug-pattern severities — real-bug patterns already fail the build, advisory ones stay
// warnings. Micronaut's annotation-processor output is excluded so generated beans/introspections
// aren't analysed.
//
// NullAway is promoted to an error and scoped to our own packages, so an unannotated nullable
// dereference becomes a compile failure; it recognises the Micronaut `@Nullable`/`@NonNull` already
// used in the codebase by simple name. It runs on the production (`main`) sources only: NullAway
// models production nullness contracts, whereas tests deliberately pass/handle null and lean on
// AssertJ's `isNotNull()`, which NullAway does not treat as a narrowing check.
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        if (name == "compileJava") {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "net.earelin.mercator")
        } else {
            check("NullAway", CheckSeverity.OFF)
        }
    }
}

// SpotBugs analyses compiled bytecode for bug patterns; the Find Security Bugs pack (wired via the
// `spotbugsPlugins` configuration above) adds security detectors (injection, weak crypto, SSRF,
// path traversal…). MAX effort for the most thorough analysis, MEDIUM confidence to drop
// low-confidence noise. The exclude filter skips Micronaut's annotation-processor output (generated
// beans/introspections) and a few scoped false positives / deliberate trade-offs.
//
// It runs on the production (`main`) sources only (see the task-disabling below): Find Security Bugs
// models the deployed attack surface, whereas test/integration fixtures legitimately do "unsafe"
// things (dynamic SQL to exercise DB constraints, throwaway credentials…) that are pure noise here —
// the same main-only reasoning as NullAway.
spotbugs {
    toolVersion = libs.versions.spotbugs.tool.get()
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
    ignoreFailures = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    // Only analyse production bytecode; skip the per-test-source-set tasks (spotbugsTest, etc.).
    enabled = name == "spotbugsMain"
    // HTML for humans; SARIF for CI to upload to GitHub Code Scanning (one category per tool).
    reports.create("html") { required = true }
    reports.create("sarif") { required = true }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Let a DOCKER_API_VERSION env var pin docker-java's Remote API version for the Testcontainers
    // worker (no-op when unset). Some recent daemons reject docker-java's default (1.32); docker-java
    // reads this as the `api.version` system property, so bridge the familiar env var across.
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
}
