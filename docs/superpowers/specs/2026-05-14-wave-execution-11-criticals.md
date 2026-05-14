# Wave Execution Plan: 9 of 11 CRITICALs

**Source:** 2026-05-14 re-audit (docs/superpowers/specs/2026-05-14-vendor-code-re-audit.md)
**Goal:** Fix 9 CRITICALs in 3 waves (8 agents total)
**Not covered here:** CRITICAL-ARCH-1 (extra ViewModels — needs design decision), CRITICAL-XC-1 (backup encryption — needs crypto library choice)
**Build command:** `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
**Test command:** same prefix + ` test`

---

## WAVE 1: DATA LAYER MUTEX (3 agents, parallel, zero file overlap)

All 3 agents work in `data/` only. Each agent touches distinct files. No shared state.

### Agent D1: VerbDrillStore + WordMasteryStore mutex

**CRITICALs fixed:** CRITICAL-DATA-1

**Files you OWN (only you edit):**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt`

**Files you MUST NOT touch:** Everything else. Especially StoreFactory.kt, VerbDrillViewModel.kt, TrainingViewModel.kt.

**Pattern to follow** (copy exactly from MasteryStore.kt):

```kotlin
// 1. Add import at top:
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// 2. Add field in class body:
private val mutex = ReentrantLock()

// 3. Wrap load-modify-save methods with mutex.withLock:
```

**VerbDrillStore.kt changes:**

Class is `VerbDrillStoreImpl` (line 23). Add `private val mutex = ReentrantLock()` field.

Wrap `upsertComboProgress` (lines 116-121):
```kotlin
// BEFORE:
override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {
    val all = loadProgress().toMutableMap()
    all[key] = progress
    progressCache = all
    saveProgress(all)
}

// AFTER:
override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) = mutex.withLock {
    val all = loadProgress().toMutableMap()
    all[key] = progress
    progressCache = all
    saveProgress(all)
}
```

`loadProgress()` (line 43) — NOT wrapped with mutex. It uses `progressCache` as read-only fast path. The cache field is `var progressCache: Map<...>? = null` — reads of a `var` reference in Kotlin are atomic for reference types. This is safe as-is because:
- Reads see either null (will load from disk under mutex in upsertComboProgress) or a fully constructed Map
- The only writer is `upsertComboProgress` which now holds mutex

`saveProgress` (lines 92-110) — NOT wrapped separately. It is always called from within `upsertComboProgress` which holds mutex.

`loadAllCardsForPack`, `getCardsForTenses` (read-only) — no change needed.

**WordMasteryStore.kt changes:**

Class is `WordMasteryStoreImpl` (line 35). Add `private val mutex = ReentrantLock()` field.

Wrap `upsertMastery` (lines 125-131):
```kotlin
// BEFORE:
override fun upsertMastery(state: WordMasteryState) {
    val all = loadAll().toMutableMap()
    all[state.wordId] = state
    cache = all
    cacheLoaded = true
    saveAll(all)
}

// AFTER:
override fun upsertMastery(state: WordMasteryState) = mutex.withLock {
    val all = loadAll().toMutableMap()
    all[state.wordId] = state
    cache = all
    cacheLoaded = true
    saveAll(all)
}
```

`loadAll()` (lines 56-62) — NOT wrapped. Same reasoning as VerbDrillStore: `var cache` + `var cacheLoaded` are atomic reference reads.

`saveAll` (lines 93-112) — NOT wrapped separately. Always called from within `upsertMastery`.

Also wrap any other mutating methods if they exist (search for methods that call `saveAll` or modify `cache`).

**Verification:**
1. Build: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
2. Run ALL tests: same prefix + ` test`
3. Grep YOUR files to confirm: `grep -n "mutex" VerbDrillStore.kt WordMasteryStore.kt` should show declaration + withLock usage
4. Grep to confirm NO other store files were touched: `git diff --name-only` should show ONLY your 2 files

---

### Agent D2: StreakStore + ProgressStore + DrillProgressStore mutex

**CRITICALs fixed:** CRITICAL-DATA-2 + 2 WARNING findings

**Files you OWN (only you edit):**
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/DrillProgressStore.kt`

**Files you MUST NOT touch:** Everything else. Especially VerbDrillStore.kt, WordMasteryStore.kt, TrainingViewModel.kt.

**Pattern:** Same ReentrantLock + withLock as Agent D1.

**StreakStore.kt changes:**

Class is `StreakStoreImpl` (line 20). Add `private val mutex = ReentrantLock()`.

Wrap `recordSubLessonCompletion` (lines 73-94):
```kotlin
override fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> = mutex.withLock {
    val current = load(languageId)
    // ... rest unchanged ...
    save(updated)
    return Pair(updated, streakStatus.isNewStreak)
}
```

Fix `getCurrentStreak` (lines 147-164) — this is a READ method with a WRITE side effect. Split it:
```kotlin
// BEFORE:
override fun getCurrentStreak(languageId: String): StreakData {
    val current = load(languageId)
    val lastCompletionMs = current.lastCompletionDateMs ?: return current
    val now = System.currentTimeMillis()
    val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletionMs)
    if (daysSinceLastCompletion > 1) {
        val reset = current.copy(currentStreak = 0)
        save(reset)
        return reset
    }
    return current
}

// AFTER:
override fun getCurrentStreak(languageId: String): StreakData = mutex.withLock {
    val current = load(languageId)
    val lastCompletionMs = current.lastCompletionDateMs ?: return current
    val now = System.currentTimeMillis()
    val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletionMs)
    if (daysSinceLastCompletion > 1) {
        val reset = current.copy(currentStreak = 0)
        save(reset)
        return reset
    }
    return current
}
```

The side-effect (save on stale streak) stays inside mutex, which is correct. The alternative (separating read from write) would require callers to change, which is out of scope.

Also wrap `save` if it's public and can be called from outside. Check the interface: if `save` is in the `StreakStore` interface, wrap it too. If it's private/internal, it's always called from within mutex.

**ProgressStore.kt changes:**

Class is `ProgressStoreImpl` (line 16). Add `private val mutex = ReentrantLock()`.

Methods to wrap:
- `save(progress: TrainingProgress)` (line 87) — wrap with mutex.withLock
- `clear()` (line 126) — wrap with mutex.withLock
- `load(): TrainingProgress` (line 22) — wrap with mutex.withLock (because load reads from file, and save writes to same file — they can race)

**DrillProgressStore.kt changes:**

Class is `DrillProgressStoreImpl` (line 14). Add `private val mutex = ReentrantLock()`.

Methods to wrap:
- `saveDrillProgress(lessonId, cardIndex)` (line 31) — wrap with mutex.withLock
- `getDrillProgress(lessonId)` (line 22) — wrap with mutex.withLock
- `clearDrillProgress(lessonId)` (line 45) — wrap with mutex.withLock
- `hasProgress(lessonId)` (line 41) — delegates to getDrillProgress, no change needed if getDrillProgress is wrapped

Note: DrillProgressStore uses per-lesson files. The risk is lower than shared-file stores, but wrapping is still correct practice.

**Verification:** Same as Agent D1. `git diff --name-only` should show exactly 3 files.

---

### Agent D3: Fix MasteryStore + VocabProgressStore clear() bypass

**WARNING findings fixed:** W-DATA-1

**Files you OWN (only you edit):**
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VocabProgressStore.kt`

**Files you MUST NOT touch:** Everything else.

**MasteryStore.kt change:**

The store already has `private val mutex = ReentrantLock()` at line 40.

Fix `clear()` (lines 233-239):
```kotlin
// BEFORE:
override fun clear() {
    cache.clear()
    cacheLoaded = true
    if (file.exists()) {
        file.delete()
    }
}

// AFTER:
override fun clear() = mutex.withLock {
    cache.clear()
    cacheLoaded = true
    if (file.exists()) {
        file.delete()
    }
}
```

**VocabProgressStore.kt change:**

Same pattern. Store already has `private val mutex = ReentrantLock()` at line 56.

Fix `clear()` (lines 265-271):
```kotlin
override fun clear() = mutex.withLock {
    cache.clear()
    cacheLoaded = true
    if (file.exists()) {
        file.delete()
    }
}
```

**Verification:** Same as other agents. Confirm only 2 files changed.

---

## WAVE 1 CHECKPOINT

After all 3 agents complete, main context MUST:

1. Run: `git diff --name-only` — expect exactly 7 files (2 + 3 + 2)
2. Build: spawn a build subagent with assembleDebug
3. Run tests: spawn a test subagent
4. Verify no files overlap between agents
5. If any agent failed — fix before proceeding to Wave 2

---

## WAVE 2: UI + BUILD + SECURITY (3 agents, parallel, zero file overlap)

### Agent U1: StoryQuizScreen crash fix + VocabDrillScreen report sheet

**CRITICALs fixed:** CRITICAL-UI-1, CRITICAL-UI-2

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/StoryQuizScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`

**Files you MUST NOT touch:** TrainingViewModel.kt, GrammarMateApp.kt, Models.kt, SharedReportSheet.kt (read only — do not modify its API).

#### Fix 1: StoryQuizScreen composition-time callbacks

**Problem:** Lines 42, 53, 59 call `onClose()` / `onComplete()` directly during composition. This throws `IllegalStateException` because state changes during composition are forbidden.

**Current code** (lines 35-62):
```kotlin
@Composable
fun StoryQuizScreen(
    story: StoryQuiz?,
    testMode: Boolean,
    onClose: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    if (story == null) {
        onClose()     // <-- CRASH: state change during composition
        return
    }
    // ... state declarations ...
    if (story.questions.isEmpty()) {
        onComplete(true)    // <-- CRASH
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        onComplete(allCorrect)    // <-- CRASH
        return
    }
```

**Fix:** Replace direct calls with `LaunchedEffect`:
```kotlin
@Composable
fun StoryQuizScreen(
    story: StoryQuiz?,
    testMode: Boolean,
    onClose: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    if (story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val selections = remember(story.storyId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val results = remember(story.storyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var questionIndex by remember(story.storyId) { mutableStateOf(0) }
    var showResult by remember(story.storyId) { mutableStateOf(false) }
    var errorMessage by remember(story.storyId) { mutableStateOf<String?>(null) }
    val selectAnAnswerText = stringResource(R.string.story_select_an_answer)
    val incorrectText = stringResource(R.string.story_incorrect)
    if (story.questions.isEmpty()) {
        LaunchedEffect(Unit) { onComplete(true) }
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        LaunchedEffect(allCorrect) { onComplete(allCorrect) }
        return
    }
    // ... rest of function unchanged ...
```

Note: The third `onComplete` uses `allCorrect` as the LaunchedEffect key because it's a derived value. Using `Unit` would also work since the composable returns immediately.

#### Fix 2: VocabDrillScreen report sheet → SharedReportSheet

**Problem:** VocabDrillScreen.kt lines 575-660 contain an inline ModalBottomSheet that duplicates SharedReportSheet. The inline version has 3 options (flag/unflag, export, copy) but is MISSING the "hide card" option that SharedReportSheet provides.

**SharedReportSheet API** (from SharedReportSheet.kt lines 54-66):
```kotlin
@Composable
fun SharedReportSheet(
    onDismiss: () -> Unit,
    cardPromptText: String?,
    isFlagged: Boolean,
    onFlag: () -> Unit,
    onUnflag: () -> Unit,
    onHideCard: () -> Unit,
    onExportBadSentences: () -> String?,
    onCopyText: () -> Unit,
    exportResult: ((String?) -> Unit)? = null,
    title: String = "Card options",
    showExportConfirmation: Boolean = true
)
```

**What to do:**

1. In VocabDrillScreen.kt, find the `if (showReportSheet)` block (around line 575)
2. Replace the entire inline ModalBottomSheet with SharedReportSheet call
3. Map the existing callbacks to SharedReportSheet parameters:

```kotlin
// Replace the entire "if (showReportSheet) { ... ModalBottomSheet { ... } }" block with:
if (showReportSheet) {
    SharedReportSheet(
        onDismiss = { showReportSheet = false },
        cardPromptText = "${card.word.word} — ${card.word.meaningRu ?: ""}",
        isFlagged = isBadSentence(),
        onFlag = onFlagBadSentence,
        onUnflag = onUnflagBadSentence,
        onHideCard = { /* Vocab drill doesn't have hide-card semantics; dismiss */ showReportSheet = false },
        onExportBadSentences = onExportBadSentences,
        onCopyText = {
            if (reportText.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(reportText))
            }
        },
        exportResult = { path ->
            exportMessage = if (path != null) context.getString(R.string.training_exported_to, path) else context.getString(R.string.training_no_bad_sentences)
        },
        title = stringResource(R.string.vocab_word_options)
    )
}
```

**IMPORTANT:** Check that VocabDrillScreen already imports `SharedReportSheet`. If not, add the import. Also check that `clipboardManager` and `reportText` are available in the scope where you place this code. Also check if `onHideCard` should do something meaningful — look at how VocabDrillViewModel handles card hiding. If it doesn't support it, the empty dismiss is correct.

**Verification:**
1. Build
2. Grep VocabDrillScreen.kt to confirm NO `ModalBottomSheet` remains for the report sheet
3. Grep to confirm `SharedReportSheet` is imported
4. Check StoryQuizScreen.kt has `LaunchedEffect` wrapping all callbacks

---

### Agent S1: Signing config + CI pipeline

**CRITICALs fixed:** CRITICAL-XC-2, CRITICAL-TEST-1

**Files you OWN:**
- `app/build.gradle.kts` (signing config only — do NOT change dependencies or other sections)
- `.github/workflows/ci.yml` (NEW file — create it)

**Files you MUST NOT touch:** Any Kotlin source files, any other gradle files.

#### Fix 1: Signing configuration

**Current state** (build.gradle.kts lines 23-31):
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

**Add signing config** BEFORE the `buildTypes` block:
```kotlin
android {
    // ... existing config ...

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
}
```

This uses environment variables so credentials don't appear in source code. For local development without a keystore, debug builds work fine. For CI, the keystore file and passwords are set as GitHub secrets.

#### Fix 2: CI pipeline

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main, feature/* ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Run unit tests
        run: ./gradlew test

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: app/build/reports/tests/
```

**Note:** CI runs on Linux, so `./gradlew` works directly (not the Windows `java -cp` workaround). The Windows workaround is only needed for local development.

**Verification:**
1. Build locally with the Windows command to confirm signing config doesn't break debug builds
2. Check that `.github/workflows/ci.yml` parses as valid YAML
3. `git diff --name-only` should show: `app/build.gradle.kts` + `.github/workflows/ci.yml`

---

### Agent S2: Backup restore validation

**CRITICALs fixed:** CRITICAL-XC-3

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/data/BackupRestorer.kt`

**Files you MUST NOT touch:** BackupManager.kt, any ViewModel, any UI files.

**Problem:** `BackupRestorer` blindly copies YAML content from backup to internal storage. No schema version check, no structural validation. A malformed or malicious backup can corrupt app state.

**Current methods:**
- `restoreFromPath(backupPath: String): Boolean` (line 30) — legacy file-path restore
- `restoreFromUri(backupUri: Uri): Boolean` (line 74) — SAF restore

Both use `AtomicFileWriter` for writes (good), but neither validates the input.

**Fix approach:**

Add a validation function that checks backup integrity before accepting it:

```kotlin
private fun validateBackupContent(content: String, fileName: String): Boolean {
    if (content.isBlank()) return false

    // Check for valid YAML structure
    val yaml = Yaml()
    return try {
        val data = yaml.load<Any>(content)
        when (data) {
            is Map<*, *> -> {
                // Check schema version if present
                val version = data["schemaVersion"]
                if (version != null && version !is Number) return false

                // Basic sanity: non-empty data section for store files
                val dataSection = data["data"]
                if (dataSection != null && dataSection !is Map<*, *> && dataSection !is List<*>) return false

                true
            }
            is List<*> -> data.isNotEmpty()
            else -> true // Allow simple values for config files
        }
    } catch (e: Exception) {
        Log.w(TAG, "Backup validation failed for $fileName", e)
        false
    }
}
```

Then call this before each `AtomicFileWriter.writeText` or `AtomicFileWriter.copyAtomic` in both restore methods:

```kotlin
// In restoreFromPath, before writing each file:
val content = srcFile.readText()
if (!validateBackupContent(content, srcFile.name)) {
    Log.w(TAG, "Skipping invalid backup file: ${srcFile.name}")
    continue
}
AtomicFileWriter.writeText(destFile, content)
```

For binary/file copies (copyAtomic), add a post-copy validation by reading the restored file back:

```kotlin
AtomicFileWriter.copyAtomic(srcFile, destFile)
val restoredContent = destFile.readText()
if (!validateBackupContent(restoredContent, destFile.name)) {
    destFile.delete()
    Log.w(TAG, "Deleted invalid restored file: ${destFile.name}")
}
```

**Also add:** A version compatibility check. Read the `schemaVersion` from the restored data and compare against the current app's expected versions. If the backup is from a newer version, warn (but don't reject — forward compat is nice to have).

**IMPORTANT:** Do NOT reject backups that have no `schemaVersion` field — early versions didn't have it. Only reject backups where the field exists but is not a number, or where the YAML is completely unparseable.

**Verification:**
1. Build
2. Check that `validateBackupContent` is called before every write in both restore methods
3. Confirm backup files are never written without validation

---

## WAVE 2 CHECKPOINT

After all 3 agents complete:
1. `git diff --name-only` — expect 5 files modified + 1 new (ci.yml)
2. Build
3. Run all tests
4. Verify no file overlap between agents

---

## WAVE 3: TESTABILITY (2 agents, parallel, minimal overlap risk)

### Agent T1: CardSessionStateMachine → StateFlow

**CRITICALs fixed:** CRITICAL-TEST-3

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/CardSessionStateMachine.kt`

**Files you MUST ALSO modify (consumers — minimal changes):**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeSessionProvider.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillCardSessionProvider.kt` (if it exists — search for it)

**Files you MUST NOT touch:** TrainingViewModel.kt, GrammarMateApp.kt, any screen files.

**Problem:** CardSessionStateMachine uses Compose `mutableStateOf` for 6 fields. This requires the Compose runtime, making the class untestable in plain JUnit. The state machine logic (retry counting, hint management) is pure domain logic that should work in any context.

**Current state** (all fields use mutableStateOf):
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class CardSessionStateMachine(
    val maxAttempts: Int = 3,
    val answerProvider: (SessionCard) -> String
) {
    var incorrectAttempts: Int by mutableStateOf(0)
    var hintAnswer: String? by mutableStateOf(null)
    var showIncorrectFeedback: Boolean by mutableStateOf(false)
    var remainingAttempts: Int by mutableStateOf(maxAttempts)
    var isPaused: Boolean by mutableStateOf(false)
    var voiceTriggerToken: Int by mutableStateOf(0)
```

**Fix:** Replace `mutableStateOf` with `MutableStateFlow` + property delegation:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CardSessionStateMachine(
    val maxAttempts: Int = 3,
    val answerProvider: (SessionCard) -> String
) {
    private val _incorrectAttempts = MutableStateFlow(0)
    val incorrectAttempts: StateFlow<Int> = _incorrectAttempts.asStateFlow()

    private val _hintAnswer = MutableStateFlow<String?>(null)
    val hintAnswer: StateFlow<String?> = _hintAnswer.asStateFlow()

    private val _showIncorrectFeedback = MutableStateFlow(false)
    val showIncorrectFeedback: StateFlow<Boolean> = _showIncorrectFeedback.asStateFlow()

    private val _remainingAttempts = MutableStateFlow(maxAttempts)
    val remainingAttempts: StateFlow<Int> = _remainingAttempts.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _voiceTriggerToken = MutableStateFlow(0)
    val voiceTriggerToken: StateFlow<Int> = _voiceTriggerToken.asStateFlow()
```

**BUT WAIT:** The current code uses `by mutableStateOf()` delegation which allows direct `=` assignment (e.g., `incorrectAttempts = 5`). Switching to StateFlow means:
- Reading: `stateMachine.incorrectAttempts` → now `stateMachine.incorrectAttempts.value`
- Writing: `stateMachine.incorrectAttempts = 5` → now `stateMachine._incorrectAttempts.value = 5` (but _x is private)

This is a SIGNIFICANT API change for all consumers. Two approaches:

**Approach A (recommended — keep var API):**
```kotlin
var incorrectAttempts: Int by _incorrectAttempts::value
    private set
```
Wait — `MutableStateFlow` doesn't support `by` delegation directly. Use a helper:

```kotlin
private val _incorrectAttempts = MutableStateFlow(0)
var incorrectAttempts: Int
    get() = _incorrectAttempts.value
    set(value) { _incorrectAttempts.value = value }
```

This keeps the public API identical (`.incorrectAttempts` read and `.incorrectAttempts = x` write) while using StateFlow internally.

**Approach B (breaking — expose StateFlow):** Expose StateFlow directly. All consumers must use `.value`. More Compose-idiomatic but requires touching more files.

**Use Approach A.** It preserves the existing API. Internal `_x` flows are exposed as `StateFlow` for anyone who wants reactive observation, but direct property access still works.

**Consumer updates (minimal):**

For `DailyPracticeSessionProvider.kt` and `VerbDrillCardSessionProvider.kt`:
- Search for all usages of `stateMachine.incorrectAttempts`, `stateMachine.remainingAttempts`, etc.
- If they READ: no change needed (Approach A keeps getter)
- If they WRITE: no change needed (Approach A keeps setter)
- If they use `by mutableStateOf()` to OBSERVE the state machine's fields from a Composable — these need to switch to `collectAsState()` from the exposed StateFlow. Search for patterns like `val x by stateMachine` or `remember { derivedStateOf { stateMachine.xxx } }`.

**Verification:**
1. Build
2. Run all tests
3. Grep CardSessionStateMachine.kt: `grep "mutableStateOf"` should return ZERO results
4. Grep: `grep "MutableStateFlow"` should return 6 results
5. Check consumers compile and tests pass

---

### Agent T2: AudioCoordinator interface extraction (OPTIONAL — evaluate first)

**CRITICALs fixed:** CRITICAL-TEST-2

**WARNING:** This is the highest-risk change in the entire wave plan. It touches the audio subsystem which has hardware dependencies. If you break AudioCoordinator, TTS and ASR stop working.

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt`

**Files you MUST NOT touch:** TrainingViewModel.kt, GrammarMateApp.kt, any screen files. Only AudioCoordinator.kt.

**Current constructor** (lines 34-39):
```kotlin
class AudioCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val configStore: AppConfigStore
) {
```

**Fields instantiated inline** (lines 46-75):
```kotlin
private val soundPool = SoundPool.Builder()...build()
private val successSoundId = soundPool.load(appContext, R.raw.voicy_correct_answer, 1)
private val errorSoundId = soundPool.load(appContext, R.raw.voicy_bad_answer, 1)
val ttsEngine = TtsProvider.getInstance(appContext).ttsEngine
val ttsModelManager = TtsModelManager(appContext)
val asrModelManager = AsrModelManager(appContext)
val asrEngine: AsrEngine? = try { AsrEngine(appContext) } catch ...
```

**Fix strategy:** Extract services behind interfaces that can be faked in tests. But this is a LARGE refactor. A safer incremental approach:

**Phase 1 (do this now):** Add constructor parameters with defaults, keeping current behavior:
```kotlin
class AudioCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val configStore: AppConfigStore,
    private val soundPoolProvider: (Context) -> SoundPool = { ctx ->
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(...)
            .build()
    },
    private val ttsProvider: (Application) -> TtsEngine = { app ->
        TtsProvider.getInstance(app).ttsEngine
    },
    private val ttsModelManagerProvider: (Context) -> TtsModelManager = { ctx ->
        TtsModelManager(ctx)
    },
    private val asrModelManagerProvider: (Context) -> AsrModelManager = { ctx ->
        AsrModelManager(ctx)
    },
    private val asrEngineProvider: (Context) -> AsrEngine? = { ctx ->
        try { AsrEngine(ctx) } catch (e: Exception) { null }
    }
) {
```

Then use these providers instead of direct instantiation. The default lambdas preserve exact current behavior. Tests can inject no-op providers.

**This approach:**
- Zero behavioral change (defaults replicate current behavior exactly)
- Opens the door for testing (inject fakes that don't need hardware)
- No changes needed in consumers (TrainingViewModel, GrammarMateApp)
- Low risk: if defaults are wrong, the build fails immediately

**Verification:**
1. Build
2. Run all tests
3. `git diff AudioCoordinator.kt` — confirm only constructor + field initialization changed
4. Confirm all `val ttsEngine = ...` etc. are now using their respective provider lambdas

---

## WAVE 3 CHECKPOINT

After both agents complete:
1. `git diff --name-only` — expect 3-4 files
2. Build
3. Run all tests
4. Special attention: confirm CardSessionStateMachine no longer imports `mutableStateOf`

---

## GLOBAL RULES FOR ALL AGENTS

1. **READ-ONLY first.** Read the target files completely before making any changes.
2. **One wave at a time.** Do not start Wave 2 until Wave 1 checkpoint passes.
3. **Build after every change.** If the build fails, fix it before reporting completion.
4. **Run ALL tests after changes.** If any test fails, report it — do not silently fix.
5. **Do NOT modify files outside your ownership.** If you need a change in another file, report it as a blocker.
6. **Do NOT add comments explaining what you changed.** Write clean code, not diary entries.
7. **Commit footer:** `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`
8. **Build command (Windows):** `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
9. **Test command (Windows):** same prefix + ` test`

## POST-WAVE: Remaining 2 CRITICALs (separate research task)

After all 3 waves complete, a separate research task addresses:
- **CRITICAL-ARCH-1:** VerbDrillViewModel + VocabDrillViewModel violate single-ViewModel constraint → design decision needed
- **CRITICAL-XC-1:** Backup encryption → crypto library choice + design
