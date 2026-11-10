// Samodzielny build: nie zależy od projektu Android z etapów 1–3.
// Integracja: `includeBuild("service-engine")` albo `include(":service-engine")` w głównym settings.gradle.kts.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // Wersje narzędzi z jednego miejsca dla całego repozytorium (ten sam katalog co aplikacja).
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "service-engine"
