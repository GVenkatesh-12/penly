plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.editor.selection"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:core-geometry"))

    testImplementation(libs.junit)
}
