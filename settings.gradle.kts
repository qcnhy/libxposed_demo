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

rootProject.name = "demo"
include(":app")
include(":libxposed-api:api")
include(":libxposed-api:checks")
include(":libxposed-compat")

project(":libxposed-api:api").projectDir = File(rootDir, "libxposed-api/api")
project(":libxposed-api:checks").projectDir = File(rootDir, "libxposed-api/checks")
project(":libxposed-compat").projectDir = File(rootDir, "libxposed-compat")