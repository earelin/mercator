pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Build with Gradle 9.5.1 (pinned via the wrapper: gradle/wrapper/gradle-wrapper.properties).
// Single-project build: the root project IS the Micronaut server. The historical import runs
// in-process as a gated admin endpoint, so there is no separate library or CLI module.
rootProject.name = "mercator"
