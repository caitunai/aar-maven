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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AAR Maven"
include(":app")
include(":aar-wrapper:jldecryption")
include(":aar-wrapper:jl_audio_decode")
include(":aar-wrapper:jl_bluetooth_connect")
include(":aar-wrapper:jl_bluetooth_rcsp")
include(":aar-wrapper:jl_bt_ota")
include(":aar-wrapper:jl_rcsp")
include(":aar-wrapper:jl_watch")
