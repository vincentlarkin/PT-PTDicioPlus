plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqlite.jdbc)
}

tasks.test {
    // Integration tests read the built dictionary when it exists (python3 -I tools/build_dictionary.py).
    systemProperty("dicio.db", rootProject.layout.projectDirectory.file("data/build/dicio.sqlite").asFile.absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = true }
}
