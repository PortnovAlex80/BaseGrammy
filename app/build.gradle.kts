plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)        // Kotlin 2.0 Compose compiler plugin (replaces composeOptions)
}

android {
    namespace = "com.alexpo.grammermate"
    compileSdk = 35

    // Production identity (carried over from the former `legacy` flavor —
    // last shipped as apk-12000 / v1.7; see docs/architecture/decisions/006).
    defaultConfig {
        applicationId = "com.alexpo.grammermate"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        isCoreLibraryDesugaringEnabled = true
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

    buildFeatures {
        compose = true
        buildConfig = true               // BuildConfig.DEBUG guards for hot-path logging + StrictMode
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
    // Robolectric-набор вырос (466 тестов) — дефолтного heap тест-JVM мало
    // (OutOfMemoryError на поздних классах после загрузки нескольких
    // Android-environment'ов Robolectric); 2g стабилизирует прогоны.
    maxHeapSize = "2g"
}

// Keep the historical APK name so build.bat / install docs stay valid.
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation("androidx.documentfile:documentfile:1.0.1")

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

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // YAML stores (mastery/config/packs are YAML on disk)
    implementation(libs.snakeyaml)
    implementation(libs.commons.compress)       // tar.bz2 extraction for TTS model download

    // QR code
    implementation(libs.qrose)

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
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
