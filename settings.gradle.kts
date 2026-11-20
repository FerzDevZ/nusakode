pluginManagement {
    repositories {
        google()
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

rootProject.name = "kodenusa"

include(":core:build")
include(":core:project")
include(":core:ai")
include(":core:git")
include(":core:diagnose")
include(":build:tier0")
include(":build:hostcli")
include(":core:ui")
include(":core:editor")
include(":app")
