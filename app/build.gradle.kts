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
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // TODO: real release key before store upload
        }
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
