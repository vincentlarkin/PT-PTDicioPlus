plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.euptdicio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.euptdicio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
}

tasks.register<Copy>("copyDictionaryDatabase") {
    val builtDictionary = rootProject.layout.projectDirectory.file("data/build/euptdicio-kaikki.sqlite")
    val dictionaryAssetDir = layout.projectDirectory.dir("src/main/assets/dictionary")
    from(builtDictionary)
    into(dictionaryAssetDir)
    rename { "euptdicio.sqlite" }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("copyDictionaryDatabase")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":dictionary-core"))

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
