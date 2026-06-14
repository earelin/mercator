plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    api(libs.slf4j.api)

    // BOE per-document fetch fallbacks: jsoup strips txt.php HTML chrome,
    // PDFBox extracts text from the authentic-last-resort PDF (ADR-0002, ADR-0012).
    implementation(libs.jsoup)
    implementation(libs.pdfbox)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
