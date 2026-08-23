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
    implementation(project(":core:core-model"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
