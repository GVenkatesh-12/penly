plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.core.geometry"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {

    testImplementation(libs.junit)
}
