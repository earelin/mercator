package net.earelin.mercator.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces the inward-only dependency direction between the layers (ADR-0014) so it cannot erode
 * silently: <strong>application → infrastructure → domain</strong>. Domain objects may carry
 * persistence/serialization annotations (e.g. {@code @MappedEntity}, {@code @Serdeable}) and serve
 * as DB entities / API bodies — only the dependency direction is policed, not framework-freedom.
 *
 * <p>Production classes are imported once (tests excluded).
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
}
