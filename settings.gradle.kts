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
        // coinswap-kotlin (org.coinswap:coinswap-kotlin:1.0.0) must be built from source
        // and published locally before building this app:
        //   cd coinswap-ffi/coinswap-kotlin
        //   ./gradlew :lib:assembleRelease
        //   ./gradlew :lib:publishToMavenLocal -PlocalBuild=true
        mavenLocal()
    }
}

rootProject.name = "CoinSwap Mobile"
include(":app")
