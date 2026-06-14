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
rootProject.name = "mercator"

// Gradle multi-project: three subprojects.
// - shared:   common library (BOE client, parser, normalisation, IngestionService)
// - server:   Micronaut read-only API + daily-incremental scheduler
// - ingester: offline CLI for the historical backfill
include("shared", "server", "ingester")
