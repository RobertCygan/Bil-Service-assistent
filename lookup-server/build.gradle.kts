import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "app.servicepassport"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation("app.servicepassport:service-engine:0.1.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("app.servicepassport.lookup.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
