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

rootProject.name = "CarServiceAssistent"
include(":app", ":service-contract")
// service-engine is intentionally not included until its separate PR is reviewed and integrated.

// Test-only bridge to a separately checked-out, pinned PR #2 revision. No engine in the APK yet.
providers.gradleProperty("engineIntegrationRoot").orNull?.let { engineRoot ->
    includeBuild(file(engineRoot).resolve("service-engine")) {
        dependencySubstitution {
            substitute(module("app.servicepassport:service-engine")).using(project(":"))
        }
    }
}
