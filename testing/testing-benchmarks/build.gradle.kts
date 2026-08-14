plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.testing.benchmarks"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
}
