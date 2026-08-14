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

    testImplementation(libs.junit)
}
