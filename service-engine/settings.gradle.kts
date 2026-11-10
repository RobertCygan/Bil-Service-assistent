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
}
rootProject.name = "service-engine"
