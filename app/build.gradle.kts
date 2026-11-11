import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.servicepassport.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.servicepassport.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // -PapkAbi=arm64-v8a builds a test APK for one processor type only (about half the size, for sending to a phone).
        providers.gradleProperty("apkAbi").orNull?.let { abi -> ndk { abiFilters += abi } }
    }
    // The upload key never lives in the repository. Whoever publishes passes its location and passwords as Gradle
    // properties (for example in ~/.gradle/gradle.properties); without them the release build stays unsigned.
    val releaseStore = providers.gradleProperty("releaseStoreFile").orNull
    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = file(releaseStore)
            storePassword = providers.gradleProperty("releaseStorePassword").get()
            keyAlias = providers.gradleProperty("releaseKeyAlias").get()
            keyPassword = providers.gradleProperty("releaseKeyPassword").get()
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            buildConfigField("String", "ENVIRONMENT", "\"local-development\"")
        }
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // -Pscreenshots renders the real screens to PNG files on the JVM (see ScreenshotTest); otherwise nothing is written.
        unitTests.all {
            it.systemProperty("roborazzi.test.record", providers.gradleProperty("screenshots").isPresent.toString())
        }
    }
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
    // Reads which way up a photo was taken. Already shipped inside the text-recognition library; named here because the app uses it directly.
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.tooling)
    debugImplementation(libs.androidx.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test)
}
