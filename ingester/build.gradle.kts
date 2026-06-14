plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.picocli)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "net.earelin.mercator.ingester.IngesterCommand"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
