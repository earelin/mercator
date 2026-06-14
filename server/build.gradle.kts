plugins {
    alias(libs.plugins.micronaut.application)
}

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
        annotations("dev.mercator.server.*")
    }
}

application {
    mainClass = "dev.mercator.server.Application"
}

dependencies {
    implementation(project(":shared"))
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly(libs.logback.classic)
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
