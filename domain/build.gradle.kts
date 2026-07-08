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

// AC-2 (E01, task #432): continuously enforce that :domain stays free of any
// Android- or Sherpa-ONNX imports. The Kotlin/JVM plugin already makes
// `android.*` unresolvable at compile time (NFR-3); this task is a belt-and-
// braces grep that fails the build the moment a stale `^import android` or
// `^import com.k2fsa.sherpa` leaks into domain/src (e.g. a copy-paste from a
// migrated file). DoD = the two greps return 0 lines; wiring them as a gradle
// task makes the invariant re-checked on every `:domain:check` / CI run, not
// only at the manual AC-2 verification step.
val forbiddenImports = listOf(
    "^import android" to "android.* (Android SDK)",
    "^import com.k2fsa.sherpa" to "com.k2fsa.sherpa.* (Sherpa-ONNX)",
)

tasks.register("enforceNoAndroidImports") {
    group = "verification"
    description = "AC-2: fail if any domain source imports android.* or com.k2fsa.sherpa.*"
    val sourceDirs = sourceSets.getByName("main").java.srcDirs

    inputs.files(sourceDirs)
    doLast {
        var violations = 0
        sourceDirs.forEach { dir ->
            (fileTree(dir) { include("**/*.kt") }).forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    forbiddenImports.forEach { (pattern, label) ->
                        if (line.matches(Regex("$pattern\\b.*"))) {
                            logger.error("AC-2 violation: ${file.path}:${index + 1}: $line  [$label]")
                            violations++
                        }
                    }
                }
            }
        }
        check(violations == 0) {
            "AC-2 FAILED: found $violations forbidden android/sherpa imports in :domain " +
                "(expected 0). See messages above."
        }
        logger.lifecycle("AC-2 OK: 0 android/sherpa imports in :domain (checked ${sourceDirs.size} src dirs).")
    }
}

tasks.named("check") { dependsOn("enforceNoAndroidImports") }

