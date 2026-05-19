import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

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
        minSdk = 24
        targetSdk = 34
        // Monotonic on CI so PackageManager accepts upgrades; local builds stay at 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        versionName = "0.1.0"

        buildConfigField("String", "CLAUDE_API_KEY_DEFAULT", "\"${claudeApiKeyFromLocal}\"")

        val buildNum = System.getenv("GITHUB_RUN_NUMBER") ?: "dev"
        val buildSha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'")
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        buildConfigField("String", "BUILD_INFO", "\"v${versionName} #${buildNum} ${buildSha} (${buildTime})\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
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
    // Required for Compose hosted inside Service-owned WindowManager overlay
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // CameraX — for look_at_engine vision tool
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

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
