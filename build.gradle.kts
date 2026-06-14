import de.aaschmid.gradle.plugins.cpd.Cpd
import org.gradle.api.plugins.quality.CheckstyleExtension

plugins {
    // `base` gives the root project a `check` task so CPD can attach `cpdCheck` to it.
    base
    // CPD (PMD's copy/paste detector) for cross-project code-duplication checking.
    alias(libs.plugins.cpd)
}

// Captured at script level: the Kotlin-DSL `libs` accessor cannot be resolved inside the
// `subprojects { }` lambda receiver.
val checkstyleVersion = libs.versions.checkstyle.get()
val pmdVersion = libs.versions.pmd.get()

subprojects {
    group = "net.earelin.mercator"
    version = "0.1.0-SNAPSHOT"

    // Every subproject builds Java; apply Checkstyle with the single shared config. The plugin
    // wires checkstyleMain/checkstyleTest into `check` once the Java source sets appear.
    apply(plugin = "checkstyle")
    extensions.configure<CheckstyleExtension> {
        toolVersion = checkstyleVersion
        configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
        isIgnoreFailures = false
    }
}

// CPD scans the Java sources of all subprojects (discovered via LifecycleBasePlugin) and runs as
// part of `check`. minimumTokenCount is deliberately conservative to flag real copy/paste.
cpd {
    language = "java"
    toolVersion = pmdVersion
}

tasks.named<Cpd>("cpdCheck") {
    minimumTokenCount = 100
    ignoreFailures = false
}
