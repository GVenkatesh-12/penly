plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.testing.fakes"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
}
