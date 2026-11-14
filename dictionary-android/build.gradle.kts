plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.euptdicio.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":dictionary-core"))
    implementation(libs.kotlinx.coroutines.android)
}
