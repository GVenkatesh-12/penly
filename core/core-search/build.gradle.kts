plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.core.search"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
