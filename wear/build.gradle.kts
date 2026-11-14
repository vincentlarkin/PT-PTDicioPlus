plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.euptdicio.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.euptdicio.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

}

val bundleDictionary = tasks.register<BundleDictionary>("bundleDictionary") {
    database.set(rootProject.layout.projectDirectory.file("data/build/dicio.sqlite"))
    outputDir.set(layout.buildDirectory.dir("generated/dictionaryAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleDictionary, BundleDictionary::outputDir)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":dictionary-android"))

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.input)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)
}
