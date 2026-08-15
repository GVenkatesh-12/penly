plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.core.ink"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-geometry"))
    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.geometry)
    implementation(libs.androidx.ink.rendering.android)
    implementation(libs.androidx.ink.storage)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
