plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.core.renderer"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-ink"))
    implementation(project(":core:core-geometry"))

    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.rendering.android)

    testImplementation(libs.junit)
}
