plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.euptdicio.importer.KaikkiImporterKt"
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.test)
}

