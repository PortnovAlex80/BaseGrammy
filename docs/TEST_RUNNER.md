# Windows Test Runner for BaseGrammy

## Problem

Windows has a command-line length limit of approximately 32,767 characters. When running tests with ~280 JAR dependencies, the classpath string exceeds this limit, causing test execution to fail.

## Solution

Instead of manually constructing a classpath and invoking JUnit directly, this solution uses **Gradle's native test runner**. Gradle handles the classpath internally, bypassing the Windows command-line length limitation.

## Files Created

### 1. `run-test.bat` - Main Test Runner

A Windows batch file that runs tests via Gradle wrapper.

**Usage:**
```batch
REM Run a specific test class
run-test.bat com.alexpo.grammermate.data.FlowerCalculatorTest

REM Run all tests
run-test.bat --all

REM List available test classes
run-test.bat --list

REM Show help
run-test.bat --help
```

**Configuration:**
- `JAVA_HOME` is set to `C:\Users\user\.jdks\corretto-21.0.8` (edit if needed)
- `PROJECT_DIR` is set to `D:\Development\BaseGrammy`

### 2. `run-test.ps1` - PowerShell Alternative

A PowerShell version with colored output and more robust argument handling.

**Usage:**
```powershell
.\run-test.ps1 -TestClass com.alexpo.grammermate.data.FlowerCalculatorTest
.\run-test.ps1 -All
.\run-test.ps1 -List
.\run-test.ps1 -Help
```

### 3. `test-classpath.init.gradle.kts` - Classpath Generator (Optional)

A Gradle init script that can generate a classpath file for reference. This is not required for the main solution but can be useful for debugging or custom test runners.

**Usage:**
```batch
gradle --init-script=test-classpath.init.gradle.kts :app:generateTestClasspath
```

This creates:
- `.classpath/test-classpath.txt` - One JAR per line (for Java `@file` syntax)
- `.classpath/test-classpath-semicolon.txt` - Semicolon-separated classpath

## How It Works

1. **Gradle Wrapper Approach**: The batch file invokes Gradle via the wrapper JARs
2. **Delegation**: Gradle handles the full classpath internally (no Windows command-line limit)
3. **Test Selection**: Uses Gradle's `--tests` flag to filter which tests to run

## Examples

### Run a specific test class
```batch
run-test.bat com.alexpo.grammermate.data.FlowerCalculatorTest
```

### Run a specific test method
```batch
run-test.bat com.alexpo.grammermate.data.FlowerCalculatorTest.testBloomState
```

### Run all tests in a package
```batch
run-test.bat com.alexpo.grammermate.data.*
```

### Run all UI tests
```batch
run-test.bat com.alexpo.grammermate.ui.*
```

## Troubleshooting

### Java not found
If you get "Java not found" error:
1. Find your Java installation: `where java`
2. Update `JAVA_HOME` in `run-test.bat` to point to your Java directory

### Gradle daemon issues
If tests hang or fail due to daemon problems:
```batch
REM Stop all Gradle daemons
gradlew --stop

REM Then run tests again
run-test.bat --all
```

### Out of memory errors
For large test suites, increase Gradle memory by creating `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

## Alternative: Direct JUnit Runner (Not Recommended)

If you need to run JUnit directly without Gradle, you can use the generated classpath file:

1. Generate classpath: `gradlew --init-script=test-classpath.init.gradle.kts :app:generateTestClasspath`
2. Run with Java's `@file` syntax:
   ```batch
   java -cp @.classpath\test-classpath.txt;app\build\tmp\kotlin-classes\debugUnitTest org.junit.runner.JUnitCore com.alexpo.grammermate.data.FlowerCalculatorTest
   ```

**Note:** This approach may have issues with Android resources and Robolectric setup. The Gradle approach is preferred.

## Summary

| Approach | Works? | Pros | Cons |
|----------|--------|------|------|
| Gradle wrapper (recommended) | ✅ | Bypasses limit, proper Android test setup | Requires Gradle |
| Java `@file` syntax | ⚠️ | Simple | May have Android/Robolectric issues |
| Direct classpath string | ❌ | N/A | Exceeds Windows limit |

**Recommendation:** Use `run-test.bat` which delegates to Gradle wrapper.
