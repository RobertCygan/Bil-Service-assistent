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
rootProject.name = "lookup-server"
// Walidacja VIN i numeru rejestracyjnego pochodzi z modułu service-engine (ten sam kod co w aplikacji).
includeBuild("../service-engine")
