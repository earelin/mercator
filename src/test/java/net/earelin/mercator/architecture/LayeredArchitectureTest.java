package net.earelin.mercator.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces the hexagonal layer boundaries (ADR-0014) so they cannot erode silently:
 *
 * <ul>
 *   <li><strong>domain</strong> — the framework-free core; depends on nothing outward (no
 *       Micronaut, JDBC, HTTP or parser libraries), only the vendor-neutral {@code jakarta.inject}
 *       standard;
 *   <li><strong>infrastructure</strong> — driven adapters; may depend on the domain and on
 *       infrastructure-facing Micronaut tooling, but never on the application layer;
 *   <li><strong>application</strong> — Micronaut driving adapters + wiring; the top layer,
 *       depended on by no one.
 * </ul>
 *
 * <p>Production classes are imported once (tests excluded) and reused across the rules.
 */
class LayeredArchitectureTest {

    private static final String ROOT = "net.earelin.mercator";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void layers_only_depend_inward() {
        layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy(ROOT + ".domain..")
                .layer("Infrastructure").definedBy(ROOT + ".infrastructure..")
                .layer("Application").definedBy(ROOT + ".application..")
                // Nothing depends on the driving adapters + wiring.
                .whereLayer("Application").mayNotBeAccessedByAnyLayer()
                // Driven adapters are composed only by the application wiring.
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Application")
                // The core is depended on by both outer layers, and depends on neither.
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .because("dependencies point inward only: application → infrastructure → domain "
                        + "(ADR-0014)")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domain_core_is_free_of_frameworks_and_io() {
        noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                // Exclude the synthetic bean-definition classes Micronaut's annotation processor
                // emits next to a @Singleton domain service ($…$Definition); those legitimately
                // reference io.micronaut, but the hand-written core does not (ADR-0014).
                .and().areNotAnnotatedWith("io.micronaut.core.annotation.Generated")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.micronaut..",
                        "java.sql..",
                        "javax.sql..",
                        "java.net.http..",
                        "org.jsoup..",
                        "org.apache.pdfbox..",
                        "org.postgresql..",
                        "com.zaxxer..")
                .because("the domain core depends on nothing outward — no Micronaut, JDBC, HTTP "
                        + "or parser libraries; only the vendor-neutral jakarta.inject standard "
                        + "is allowed (ADR-0014)")
                .check(PRODUCTION_CLASSES);
    }
}
