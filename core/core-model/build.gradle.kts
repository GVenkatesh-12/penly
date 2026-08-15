plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.penly.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:core-common"))
    implementation(project(":core:core-geometry"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
