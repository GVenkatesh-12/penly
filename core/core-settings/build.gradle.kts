plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.penly.core.settings"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
