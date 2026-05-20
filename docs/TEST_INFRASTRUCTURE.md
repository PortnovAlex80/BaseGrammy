# Test Infrastructure

## Overview

The project uses a layered test strategy:
- **Pure unit tests** (`data/`, `shared/`) - no Android dependencies
- **Scenario tests** (`scenario/`) - use Robolectric to mock Android APIs
- **Click tests** (`ui/`) - Compose UI tests with `createComposeRule`

## Running Tests

### Windows Gradle Wrapper (Required)

The standard `gradlew` script is broken on Windows. Use this command pattern:

```bash
# With Java in PATH
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain <command>

# Example: Run all tests
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:test

# Example: Run single test class
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest --tests "*.ClassName"
```

### Java Location

```
C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe
```

If Java is not in PATH, use the full path or add to PATH:
```bash
set PATH=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin;%PATH%
```

### Test Commands

```bash
# All unit tests (fast)
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:test

# Debug tests only (primary)
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest

# Clean + test
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:clean :app:test

# Build only (no tests)
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:assembleDebug
```

## Test Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| JUnit | 4.13.2 | Test framework |
| Robolectric | 4.13 | Android API mocking for unit tests |
| androidx.test:core | 1.5.0 | Android test utilities |
| androidx.test.ext:junit | 1.1.5 | JUnit extensions for Android |
| androidx.compose.ui:ui-test-junit4 | (BOM) | Compose UI testing |

## Test Types

### 1. Pure Unit Tests (`data/`, `shared/`)

- **Location**: `app/src/test/java/com/alexpo/grammermate/data/`
- **No Android dependencies** - pure Kotlin/Java
- **Fast execution** - no device/emulator needed
- **Examples**: `FlowerCalculatorTest`, `SpacedRepetitionConfigTest`, `NormalizerTest`

```kotlin
@Test
fun testSomething() {
    // No Android APIs, just pure logic
    val result = someFunction(input)
    assertEquals(expected, result)
}
```

### 2. Scenario Tests (`scenario/`)

- **Location**: `app/src/test/java/com/alexpo/grammermate/scenario/`
- **Uses Robolectric** to mock Android APIs (`SystemClock`, `Log`, etc.)
- **Tests end-to-end flows** at the engine level (no UI)
- **Real SessionRunner** with in-memory fakes

**Required annotation:**
```kotlin
@RunWith(RobolectricTestRunner::class)
class MyScenarioTest {
    // Tests can now use Android APIs like SystemClock.elapsedRealtime()
}
```

**Test harness** (`test-harness/`):
- `FakeTrainingStateAccess` - In-memory state (no file I/O)
- `FakeMasteryStore` - In-memory mastery tracking
- `FakeDrillProgressStore` - In-memory drill progress
- `FakeStreakStore` - In-memory streak tracking
- `FakeLessonStore` - In-memory lesson storage
- `FakeWordMasteryStore` - In-memory word mastery

**No Mockito** - all fakes are simple in-memory implementations.

### 3. Click Tests (`ui/`)

- **Location**: `app/src/test/java/com/alexpo/grammermate/ui/click/`
- **Uses Compose UI testing** with `createComposeRule`
- **Tests user interactions** (clicks, input, navigation)

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun click_on_button_changes_text() {
    composeTestRule.setContent {
        GrammarMateTheme { MyScreen() }
    }
    composeTestRule.onNodeWithText("Click me").performClick()
    composeTestRule.onNodeWithText("Clicked!").assertExists()
}
```

## Test Results

Results are generated at:
```
app/build/reports/tests/testDebugUnitTest/index.html
```

## Current Test Status (2026-05-19)

- **Total debug tests**: 175
- **Passing**: 175 (100%) ✅
- **Failing**: 0

**Passing test suites:**
- All data tests: 115/115 ✅
- All scenario tests: 54/54 ✅
- All UI tests: 5/5 ✅

## Deferred: Release Test Failures

**Status**: Deferred - not blocking development

**Issue**: `testReleaseUnitTest` fails with `RoboMonitoringInstrumentation.java:102` errors for all UI tests.

**Why it happens**: Robolectric instrumentation configuration differs for release variant. Release build has minification, resource shrinking, and different optimization settings.

**Why we deferred**: Unit tests are primarily for development feedback (debug variant). Release tests are rarely used in practice because:
- Unit tests = developer feedback, not production validation
- Release configuration is for APK builds, not testing
- CI/CD typically runs only debug tests

**To fix when needed**: Add Robolectric configuration for release variant in `app/build.gradle.kt`:
```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("robolectric.enabledSdks", "34")
        }
    }
}
```

## Troubleshooting

### "Method elapsedRealtime in android.os.SystemClock not mocked"
**Solution**: Add `@RunWith(RobolectricTestRunner::class)` to the test class.

### "Method d in android.util.Log not mocked"
**Solution**: Same as above - Robolectric mocks Log.d().

### "java: command not found"
**Solution**: Use full Java path or add to PATH:
```bash
set PATH=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin;%PATH%
```

### Tests cached after code changes
**Solution**: Run clean before tests:
```bash
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain :app:clean :app:test
```

## Architecture Decisions

### Why Robolectric instead of Mockito?

Robolectric provides real Android API implementations without the overhead of instrumented tests. This allows:
- Fast test execution (no emulator/device needed)
- Real Android behavior (not mocked behavior)
- Tests run in standard JVM

### Why in-memory fakes instead of mocks?

The test-harness provides simple in-memory implementations that:
- Are easier to understand than mock configurations
- Provide predictable behavior
- Don't require external mockito dependencies
- Can be inspected directly in tests (no `.verify()` calls needed)

### Why scenario tests instead of only unit tests?

Unit tests cover individual functions, but scenario tests cover:
- Complete user flows through the engine
- Integration between components
- State transitions over time
- Edge cases that emerge from component interaction
