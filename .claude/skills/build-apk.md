---
name: build-apk
description: Build debug or release APK using Gradle with proper Java classpath
---

You are invoked when the user asks to build an APK. This project requires special handling on Windows due to Gradle 8.9+ wrapper structure.

## When to use

- User asks "build apk", "assemble", "compile", "make apk"
- After code changes that need verification
- Before deploying to device/emulator

## Build process

### Step 1: Check environment

First, verify if Java is available:

```bash
java -version
```

If Java is NOT available (command fails or returns error):
- **STOP and inform the user:** "Java is not available in this environment. Build APK on your local machine using instructions in CLAUDE.md."
- Provide the exact commands they need to run locally (see "Local build instructions" below)

### Step 2: Build APK (if Java available)

If Java IS available, run the build with proper classpath:

```bash
# Debug APK
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

# Release APK (if requested)
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleRelease
```

### Step 3: Report result

**Success:**
- APK location: `app/build/outputs/apk/debug/grammermate.apk` (or release)
- Size and build time

**Failure:**
- Error message
- Suggest checking `docs/BUILD_INSTRUCTIONS.md`

## Local build instructions (when Java unavailable)

If Java is not available in the current environment, provide these instructions to the user:

```cmd
:: Prerequisites
:: - Java 17 (check: java -version)
:: - Android SDK API 34
:: - local.properties with sdk.dir=... OR ANDROID_HOME set

:: Debug APK (output: app\build\outputs\apk\debug\grammermate.apk)
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

:: Or create build.bat:
@echo off
set JAVA_EXE=java
"%JAVA_EXE%" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain %*
:: Then use: build.bat assembleDebug
```

For detailed setup, see `docs/BUILD_INSTRUCTIONS.md`.

## Completion

Return `result:` with:
- `APK built successfully at app/build/outputs/apk/...` (success)
- `failed: Java not available - build locally using instructions in CLAUDE.md` (no Java)
- `failed: build error - [error message]` (build failed)
