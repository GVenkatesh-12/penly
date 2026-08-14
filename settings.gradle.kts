pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Penly"

include(":app")

include(":core:core-common")
include(":core:core-model")
include(":core:core-document")
include(":core:core-ink")
include(":core:core-geometry")
include(":core:core-renderer")
include(":core:core-storage")
include(":core:core-database")
include(":core:core-search")
include(":core:core-export")
include(":core:core-pdf")
include(":core:core-settings")
include(":core:core-telemetry")

include(":feature:feature-home")
include(":feature:feature-notebook")
include(":feature:feature-editor")
include(":feature:feature-settings")

include(":editor:editor-canvas")
include(":editor:editor-tools")
include(":editor:editor-selection")
include(":editor:editor-gestures")
include(":editor:editor-history")

include(":platform:platform-android")
include(":platform:platform-file-picker")
include(":platform:platform-share")
include(":platform:platform-stylus")

include(":testing:testing-fakes")
include(":testing:testing-fixtures")
include(":testing:testing-benchmarks")