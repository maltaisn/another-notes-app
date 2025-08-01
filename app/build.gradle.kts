/*
 * Copyright 2025 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.googlePlayPublisher)
    alias(libs.plugins.githubRelease)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

android {
    namespace = "com.maltaisn.notes"

    defaultConfig {
        applicationId = "com.maltaisn.notes"
        minSdk = 21
        compileSdk = 36
        buildToolsVersion = "35.0.0"
        targetSdk = 36
        versionCode = 10505
        versionName = "1.5.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
    }

    @Suppress("UnstableApiUsage")
    testFixtures {
        enable = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDirs(file("$projectDir/schemas"))
    }

    signingConfigs {
        create("release") {
            if (project.hasProperty("releaseKeyStoreFile")) {
                storeFile = file(providers.gradleProperty("releaseKeyStoreFile"))
                storePassword = providers.gradleProperty("releaseKeyStorePassword").get()
                keyAlias = providers.gradleProperty("releaseKeyStoreKey").get()
                keyPassword = providers.gradleProperty("releaseKeyStoreKeyPassword").get()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"

            // enable debug features only if not taking screenshots
            // androidTest can seemingly only be run in debug mode, hence why it's needed.
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES",
                (System.getenv("taking_screenshots")?.toBoolean() ?: true).toString())
        }
        getByName("release") {
            // Using legacy package name 'com.maltaisn.notes.sync' which was used at the time where
            // there was a sync flavor. Package can't be changed on Play Store so it was kept.
            applicationIdSuffix = ".sync"

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "false")
        }
    }

    packaging {
        // see https://stackoverflow.com/questions/44342455
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // App dependencies
    implementation(libs.androidx.coreKtx)
    implementation(libs.androidx.fragmentKtx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preferenceKtx)
    implementation(libs.material)
    implementation(libs.recurpicker)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutinesCore)
    implementation(libs.kotlin.coroutinesAndroid)
    implementation(libs.kotlin.serializationJson)

    // Dagger Hilt
    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hiltCompiler)

    // Architecture components
    ksp(libs.room.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.lifecycle.common)
    implementation(libs.lifecycle.livedataKtx)
    implementation(libs.lifecycle.viewmodelKtx)
    implementation(libs.lifecycle.viewmodelSavedstate)

    // Navigation component
    implementation(libs.navigation.uiKtx)
    implementation(libs.navigation.fragmentKtx)
    implementation(libs.navigation.hilt)

    // Debug
    debugImplementation(libs.leakcanary.android)
    debugImplementation(libs.venom)
    releaseImplementation(libs.venom.noop)

    // Dependencies for shared test code
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlin.test.junit)
    testFixturesApi(libs.kotlin.coroutinesTest)
    testFixturesApi(libs.mockito.kotlin)
    testFixturesApi(libs.androidx.arch.coreTesting)
    testFixturesApi(libs.androidx.test.core)
    testFixturesApi(libs.androidx.test.coreKtx)
    testFixturesApi(libs.androidx.test.junit)
    testFixturesApi(libs.androidx.test.junitKtx)
    testFixturesApi(libs.androidx.test.rules)

    // Dependencies for unit tests
    testImplementation(testFixtures(project(":app")))

    // Dependencies for android tests
    androidTestImplementation(testFixtures(project(":app")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.room.testing)
    // For screenshots
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.espresso.contrib)
}

tasks.named("build") {
    // don't test & don't lint for build task.
    setDependsOn(dependsOn.filter { it != "check" })
}

play {
    serviceAccountCredentials = file("fake-key.json")
}
if (file("publishing.gradle").exists()) {
    apply(from = "publishing.gradle")
}

tasks.register<Exec>("takeScreenshots") {
    commandLine("./screenshots.sh")
}
