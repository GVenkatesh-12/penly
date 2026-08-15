plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.penly.editor.canvas"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:core-ink"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-geometry"))
    implementation(project(":core:core-common"))
    implementation(project(":editor:editor-history"))
    implementation(project(":editor:editor-selection"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.rendering.android)

    testImplementation(libs.junit)
}
