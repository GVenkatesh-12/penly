plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.testing.fixtures"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
}
