plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register("check") {
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:check" })
    description = "Runs every subproject check task (CI exit criterion for Phase 0)."
}

subprojects {
    plugins.withId("com.android.application") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    plugins.withId("com.android.library") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        }
    }
}

listOf(
    "ktlintCheck",
    "detekt",
    "lintDebug",
    "testDebugUnitTest",
    "compileDebugAndroidTestKotlin",
    "assembleDebug",
).forEach { taskName ->
    tasks.register(taskName) {
        dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:$taskName" })
        description = "Runs $taskName in every module."
    }
}