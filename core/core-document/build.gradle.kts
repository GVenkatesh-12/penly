plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.penly.core.document"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        unitTests {
            // PenlyStore logs skipped documents via android.util.Log; JVM tests get
            // no-op defaults instead of stub exceptions.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-geometry"))
    implementation(project(":core:core-ink"))
    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.ink.brush)
    implementation(project(":core:core-storage"))
    implementation(project(":core:core-database"))
    testImplementation(libs.junit)
    androidTestImplementation(project(":core:core-ink"))
    androidTestImplementation(libs.androidx.ink.strokes)
    androidTestImplementation(libs.androidx.ink.brush)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
