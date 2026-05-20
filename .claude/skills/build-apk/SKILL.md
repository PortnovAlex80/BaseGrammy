---
name: build-apk
description: Build debug or release APK using Gradle with proper Java classpath
---

# Build APK

Builds the GrammarMate Android app using the correct Java classpath for Gradle wrapper on Windows.

## When to use

- User asks to "build APK", "compile", "assemble"
- After code changes that need verification
- Before deployment or testing

## Windows Gradle wrapper issue

This project uses Gradle 8.9+ which requires 3 JAR files in classpath:
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper-shared.jar`
- `gradle/wrapper/gradle-cli.jar`

Standard `gradlew.bat` doesn't work. Use Java directly with full classpath.

## Java location

Use Java from IntelliJ IDEA (recommended):
```
C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe
```

Or system Java if JAVA_HOME is set.

## Commands

### Debug APK
```bash
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Output: `app/build/outputs/apk/debug/grammermate.apk`

### Release APK
```bash
... GradleWrapperMain assembleRelease
```

### Run tests
```bash
... GradleWrapperMain test
```

### Clean build
```bash
... GradleWrapperMain clean
```

## Full reference

See `docs/BUILD_INSTRUCTIONS.md` for detailed setup instructions.
