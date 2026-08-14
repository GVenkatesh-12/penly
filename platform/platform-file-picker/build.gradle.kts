plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.platform.filepicker"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {

    testImplementation(libs.junit)
}
