import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Wtyczka serializacji musi mieć tę samą wersję co Kotlin; wspólny katalog nie ma jeszcze dla niej wpisu.
    id("org.jetbrains.kotlin.plugin.serialization") version libs.versions.kotlin
    `java-test-fixtures`
}

group = "app.servicepassport"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Jedyna zależność poza biblioteką standardową: zapis i odczyt pliku eksportu.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
