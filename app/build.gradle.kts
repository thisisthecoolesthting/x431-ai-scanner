import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Read caseforge.claudeApiKey from local.properties (gitignored). If unset, the app falls
// back to the value the user pastes in Settings.
val claudeApiKeyFromLocal: String = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) "" else Properties().apply { f.inputStream().use { load(it) } }
        .getProperty("caseforge.claudeApiKey", "").trim()
}

android {
    namespace = "com.caseforge.scanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.caseforge.scanner"
        minSdk = 24            // covers older X431 tablets (Android 7+)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Bakes the dev's API key from local.properties into BuildConfig so first-launch
        // doesn't require pasting it. Empty string in CI / on other machines.
        buildConfigField("String", "CLAUDE_API_KEY_DEFAULT", "\"${claudeApiKeyFromLocal}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/INDEX.LIST")
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking + JSON for the Claude client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // Encrypted preferences (API key storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room (session history)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // PDF parsing for X431 reports
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ML Kit on-device OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // DataStore for non-secret settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}

// After Build → Build APK, copy the debug APK to D:\ for easy sideloading.
// If D:\ doesn't exist on this machine, the copy is skipped silently — no build failure.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val apk = file("build/outputs/apk/debug/app-debug.apk")
        val target = file("D:/app-debug.apk")
        if (apk.exists() && file("D:/").exists()) {
            apk.copyTo(target, overwrite = true)
            println("Copied APK to ${target.absolutePath}")
        }
    }
}

