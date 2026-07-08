// :domain — pure-Kotlin domain module.
//
// SCAFFOLD (E01, task #430): physically extracted from
// app/src/main/java/com/alexpo/grammermate/v2/core/domain/**.
//
// Constraints (locked here so body tasks AC-1..AC-18 implement inside a fixed
// contract):
//   - pure JVM Kotlin (org.jetbrains.kotlin.jvm); NO Android application plugin,
//     NO Android SDK in classpath, NO `import android.*` (AC-2).
//   - jvmTarget = 17 (matches :app compileOptions / kotlinOptions). We compile
//     on the Gradle JVM (no jvmToolchain — only JDK 21 is installed on the
//     build host) but emit Java-17 bytecode, exactly like :app.
//   - external deps limited to kotlinx-coroutines-core (Flow is part of the
//     port API). Zero Sherpa, zero java.io.File, zero Room/Hilt/Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

tasks.withType<Test> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    useJUnit()
}

dependencies {
    // Coroutines — Flow is part of the port API surface (AC-9 ports return Flow).
    implementation(libs.kotlinx.coroutines.core)

    // Unit tests — mirrors :app test deps that the existing 314 tests rely on
    // (junit + truth + kotlinx-coroutines-test). NO Robolectric / Android here.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

