plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.penly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.penly.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (project.hasProperty("keystoreFile")) {
                signingConfig =
                    signingConfigs.create("release") {
                        storeFile = file(project.property("keystoreFile") as String)
                        storePassword = project.property("keystorePassword") as String
                        keyAlias = project.property("keyAlias") as String
                        keyPassword = project.property("keyPassword") as String
                    }
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:feature-editor"))
    implementation(project(":core:core-document"))
    implementation(project(":core:core-storage"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:core-ink"))
    androidTestImplementation(project(":core:core-common"))
    androidTestImplementation(project(":core:core-model"))
    androidTestImplementation(project(":core:core-geometry"))
    androidTestImplementation(project(":core:core-document"))
    androidTestImplementation(project(":editor:editor-canvas"))
    androidTestImplementation(libs.androidx.ink.strokes)
    androidTestImplementation(libs.androidx.ink.brush)
    androidTestImplementation(libs.androidx.ink.rendering.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
