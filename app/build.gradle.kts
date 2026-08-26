plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)        // Kotlin 2.0 Compose compiler plugin (replaces composeOptions)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.alexpo.grammermate"
    compileSdk = 35                            // bumped from 34 → M3 Adaptive + Compose 2024.10

    defaultConfig {
        applicationId = "com.alexpo.grammermate"
        minSdk = 26                            // bumped from 24 → FSRS, DataStore, modern APIs
        targetSdk = 35
        versionCode = 100                      // v2 fresh start
        versionName = "2.0.0"

        testInstrumentationRunner = "com.alexpo.grammermate.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export for migration regression tests
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "grammermate"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Room schema exports как assets DEBUG-варианта: Robolectric-тесты читают
    // merged assets debug (unit tests не имеют собственного asset-merge),
    // MigrationTestHelper берёт оттуда схемы 1..N.json. Release-APL их не
    // содержит — набор scoped на debug.
    sourceSets {
        getByName("debug") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
    buildFeatures {
        compose = true
    }
    // No composeOptions block: Kotlin 2.0 uses the Compose Gradle plugin above.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<Test> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    // Robolectric-набор вырос (584 теста) — дефолтного heap тест-JVM мало
    // (OutOfMemoryError на поздних классах после загрузки нескольких
    // Android-environment'ов Robolectric); 2g стабилизирует прогоны.
    maxHeapSize = "2g"
    systemProperty("android.manifest_resource_path",
        layout.buildDirectory.file("intermediates/merged_manifests/debug/AndroidManifest.xml").get().asFile.absolutePath)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val source = File(apkDir, "app-debug.apk")
        val target = File(apkDir, "grammermate.apk")
        if (source.exists()) {
            source.copyTo(target, overwrite = true)
        }
    }
}

dependencies {
    // Domain module — pure-Kotlin ports/models (extracted from v2.core.domain).
    // AC-1: :app depends on :domain; the v2/core/domain package is gone from :app.
    implementation(project(":domain"))

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Compose Material 3 Adaptive (responsive layouts for phones/tablets/foldables)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // Room (user-state persistence — single transaction boundary, fixes card_15)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (settings / preferences / migration flags)
    implementation(libs.androidx.datastore.preferences)

    // Hilt (DI — constructor injection, KSP-processed alongside Room)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (Room TypeConverters, audit log)
    implementation(libs.kotlinx.serialization.json)

    // Legacy / migration
    implementation(libs.snakeyaml)              // one-time YAML reader for migration
    implementation(libs.commons.compress)       // tar.bz2 extraction for TTS model download

    // QR code
    implementation(libs.qrose)

    // SRS — локальная pure-Kotlin реализация в domain-слое (SrsScheduler.kt).
    // Внешних зависимостей нет; это убирает риск отсутствия стабильного FSRS-артефакта.

    // Sherpa-ONNX TTS (static-linked ONNX Runtime) — local AAR
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented tests (androidTest — requires Hilt test setup)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    // Hilt test
    androidTestImplementation(libs.hilt.android)
    kspAndroidTest(libs.hilt.compiler)
}
