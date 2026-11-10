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
rootProject.name = "lookup-server"
// Walidacja VIN i numeru rejestracyjnego pochodzi z modułu service-engine (ten sam kod co w aplikacji).
includeBuild("../service-engine")
