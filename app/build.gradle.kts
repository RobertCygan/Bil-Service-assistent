import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseTests = providers.gradleProperty("releaseTests").isPresent

android {
    if (releaseTests) testBuildType = "release"
    namespace = "app.servicepassport.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.servicepassport.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            buildConfigField("String", "ENVIRONMENT", "\"local-development\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // -PreleaseTests: CI runs the device tests against the shrunk build. The extra rules keep what the tests
            // call directly; the libraries are shrunk exactly as in a real release.
            if (releaseTests) {
                proguardFiles("proguard-release-tests.pro")
                testProguardFiles("proguard-device-tests.pro")
                signingConfig = signingConfigs.getByName("debug")
            }
            buildConfigField("String", "ENVIRONMENT", "\"offline\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["test"].resources.srcDir("schemas")
    sourceSets["androidTest"].assets.srcDir("schemas")
    sourceSets["test"].java.srcDir("src/engineIntegrationTest/java")
    sourceSets["androidTest"].java.srcDir("src/engineIntegrationAndroidTest/java")
    lint { abortOnError = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// The make/model selection list is built in vehicle-catalog/ (open data). Only the JSON is shipped.
abstract class CopyVehicleCatalog : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @TaskAction fun copy() { source.get().asFile.copyTo(output.get().file("vehicle-list.json").asFile, overwrite = true) }
}
val vehicleCatalogAssets = tasks.register<CopyVehicleCatalog>("vehicleCatalogAssets") {
    source.set(rootProject.file("vehicle-catalog/vehicle-list.json"))
}
androidComponents { onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(vehicleCatalogAssets, CopyVehicleCatalog::output) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":service-contract"))
    implementation(project(":service-engine"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    // On-device text recognition with the Latin model bundled in the APK: documents are not uploaded.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.tooling)
    debugImplementation(libs.androidx.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test)
}
