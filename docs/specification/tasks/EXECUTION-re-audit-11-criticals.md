# EXECUTION PROMPT: Re-Audit 11 CRITICALs — 4-Wave Fix

**Created:** 2026-05-14
**Source:** docs/superpowers/specs/2026-05-14-vendor-code-re-audit.md (YELLOW verdict, 11 CRITICALs, 0 BLOCKERs)
**Scope:** Fix 9 CRITICALs in code + research 2 architectural decisions
**Branch:** feature/re-audit-critical-fixes (from feature/perf-and-cursor-fixes)
**Estimated effort:** 2-3 weeks (P0/P1 items first)

---

## Build Commands (Windows)

```bash
# Prefix for ALL Gradle commands (Windows wrapper workaround):
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain

# Build debug APK:
... assembleDebug

# Run all unit tests:
... test

# Single test class:
... test --tests "com.alexpo.grammermate.data.MasteryStoreTest"
```

---

## 11 CRITICALs Overview

| # | ID | Title | Wave | Type |
|---|-----|-------|------|------|
| 1 | CRITICAL-DATA-1 | VerbDrillStore + WordMasteryStore zero sync | Wave 1 | Code fix |
| 2 | CRITICAL-DATA-2 | StreakStore zero sync + read-with-write side-effect | Wave 1 | Code fix |
| 3 | W-DATA-1 | MasteryStore/VocabProgressStore clear() bypasses mutex | Wave 1 | Code fix |
| 4 | CRITICAL-UI-1 | StoryQuizScreen composition-time callback crash | Wave 2 | Code fix |
| 5 | CRITICAL-UI-2 | VocabDrillScreen 4th report sheet | Wave 2 | Code fix |
| 6 | CRITICAL-XC-2 | No signing configuration | Wave 2 | Config fix |
| 7 | CRITICAL-TEST-1 | No CI pipeline | Wave 2 | Config fix |
| 8 | CRITICAL-XC-3 | Backup restore zero validation | Wave 2 | Code fix |
| 9 | CRITICAL-TEST-3 | CardSessionStateMachine untestable (Compose mutableStateOf) | Wave 3 | Code fix |
| 10 | CRITICAL-TEST-2 | AudioCoordinator untestable (hardcoded hardware services) | Wave 3 | Code fix |
| 11 | CRITICAL-ARCH-1 | VerbDrillVM + VocabDrillVM violate single-ViewModel rule | Wave 4 | Research |
| 12 | CRITICAL-XC-1 | Backup unencrypted (plaintext YAML) | Wave 4 | Research |

Also fixes WARNING findings: W-DATA-2 (ProgressStore/DrillProgressStore no sync), W-DATA-1 (clear() bypass).

---

## Global Rules

1. **One wave at a time.** Do not start Wave N+1 until Wave N checkpoint passes.
2. **Agents within a wave are parallel** — they MUST NOT touch each other's files.
3. **Build after every agent.** If build fails, fix before reporting completion.
4. **Run ALL tests after every wave.** `test` command.
5. **Do NOT add comments** explaining changes. Clean code only.
6. **Commit footer:** `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`
7. **Hotspot files — single owner per wave:** TrainingViewModel.kt, GrammarMateApp.kt, Models.kt, trace-index.md, CHANGELOG.md. None of the waves below touch these files.

---

## WAVE 1: Data Layer Mutex (3 agents, parallel, zero file overlap)

All 3 agents work in `data/` only. Each agent touches distinct store files. No shared state between agents.

### ReentrantLock Pattern (reference for all agents)

Copy this exact pattern from MasteryStore.kt:

```kotlin
// Import:
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Field in class body:
private val mutex = ReentrantLock()

// Usage on mutating methods:
override fun someMethod() = mutex.withLock {
    // load → modify → save
}
```

Read-only methods (load-only, no mutation) do NOT need mutex wrapping. The pattern:
- `load*()` methods: NO mutex (read-only, `var cache` reference reads are atomic in Kotlin)
- `save*()` / `upsert*()` methods: YES mutex (load-modify-save sequence)
- `clear()` methods: YES mutex (mutates cache + deletes file)

---

### Agent D1: VerbDrillStore + WordMasteryStore mutex

**Fixes:** CRITICAL-DATA-1

**Files you OWN (only you edit):**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt`

**Files you MUST NOT touch:** StoreFactory.kt, VerbDrillViewModel.kt, VocabDrillViewModel.kt, TrainingViewModel.kt, any other store files.

**VerbDrillStore.kt** — class `VerbDrillStoreImpl` (line 23):

1. Add `import java.util.concurrent.locks.ReentrantLock` and `import kotlin.concurrent.withLock`
2. Add field: `private val mutex = ReentrantLock()`
3. Wrap `upsertComboProgress` (lines 116-121):

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

4. `loadProgress()` (line 43) — NO change. Uses `progressCache` as atomic reference read.
5. `saveProgress()` (lines 92-110) — NO change. Always called from within mutex (inside `upsertComboProgress`).
6. `loadAllCardsForPack`, `getCardsForTenses`, `getComboProgress` — NO change. Read-only methods.

**WordMasteryStore.kt** — class `WordMasteryStoreImpl` (line 35):

1. Add same imports + `private val mutex = ReentrantLock()`
2. Wrap `upsertMastery` (lines 125-131):

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

3. `loadAll()` (lines 56-62) — NO change. Atomic reference reads.
4. `saveAll()` (lines 93-112) — NO change. Always called from within mutex.
5. Search for any OTHER methods that modify `cache` or call `saveAll`. If found, wrap those too.

**Verification:**
1. Build: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug`
2. Tests: same prefix + ` test`
3. `git diff --name-only` — must show ONLY your 2 files
4. Grep: `grep -n "mutex" VerbDrillStore.kt WordMasteryStore.kt` — must show declaration + withLock usage

---

### Agent D2: StreakStore + ProgressStore + DrillProgressStore mutex

**Fixes:** CRITICAL-DATA-2 + W-DATA-2

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/DrillProgressStore.kt`

**Files you MUST NOT touch:** VerbDrillStore.kt, WordMasteryStore.kt, MasteryStore.kt, VocabProgressStore.kt, TrainingViewModel.kt.

**StreakStore.kt** — class `StreakStoreImpl` (line 20):

1. Add ReentrantLock imports + `private val mutex = ReentrantLock()`
2. Wrap `recordSubLessonCompletion` (lines 73-94):
```kotlin
override fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> = mutex.withLock {
    // ... entire body unchanged ...
}
```
3. Wrap `getCurrentStreak` (lines 147-164):
```kotlin
override fun getCurrentStreak(languageId: String): StreakData = mutex.withLock {
    // ... entire body unchanged ...
}
```
Note: `getCurrentStreak` has a write side-effect (`save(reset)` when streak is stale). This is kept inside mutex — correct because concurrent reads must see the reset.

4. Check: is `save()` public in the `StreakStore` interface? If yes, wrap it too. If private/internal, it's always called from within mutex.
5. `load()` (lines 47-67) — wrap with mutex too, because it reads from file and `save` writes to same file.

**ProgressStore.kt** — class `ProgressStoreImpl` (line 16):

1. Add ReentrantLock imports + `private val mutex = ReentrantLock()`
2. Wrap ALL three methods:
   - `load(): TrainingProgress` (line 22) → `= mutex.withLock { ... }`
   - `save(progress: TrainingProgress)` (line 87) → `= mutex.withLock { ... }`
   - `clear()` (line 126) → `= mutex.withLock { ... }`

**DrillProgressStore.kt** — class `DrillProgressStoreImpl` (line 14):

1. Add ReentrantLock imports + `private val mutex = ReentrantLock()`
2. Wrap mutating methods:
   - `saveDrillProgress(lessonId, cardIndex)` (line 31) → `= mutex.withLock { ... }`
   - `getDrillProgress(lessonId)` (line 22) → `= mutex.withLock { ... }`
   - `clearDrillProgress(lessonId)` (line 45) → `= mutex.withLock { ... }`
   - `hasProgress(lessonId)` (line 41) — NO change if it delegates to `getDrillProgress` (already wrapped)

**Verification:** Same as D1. `git diff --name-only` must show exactly 3 files.

---

### Agent D3: Fix MasteryStore + VocabProgressStore clear() bypass

**Fixes:** W-DATA-1

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VocabProgressStore.kt`

**Files you MUST NOT touch:** All other store files, any ViewModel, any UI files.

Both stores already have `private val mutex = ReentrantLock()` (MasteryStore line 40, VocabProgressStore line 56). Only `clear()` needs fixing.

**MasteryStore.kt** — fix `clear()` (lines 233-239):
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

**VocabProgressStore.kt** — fix `clear()` (lines 265-271):
```kotlin
override fun clear() = mutex.withLock {
    cache.clear()
    cacheLoaded = true
    if (file.exists()) {
        file.delete()
    }
}
```

**Verification:** `git diff --name-only` must show exactly 2 files. Grep confirms `clear()` now has `mutex.withLock`.

---

### WAVE 1 CHECKPOINT (mandatory before Wave 2)

After all 3 agents complete:
1. `git diff --name-only` — expect exactly 7 files (2 + 3 + 2)
2. Build: spawn build subagent
3. Run ALL tests: spawn test subagent
4. Verify: no file overlap between D1/D2/D3
5. Report to user: "Wave 1 complete. 3/3 agents OK. 5 stores now mutexed (VerbDrill, WordMastery, Streak, Progress, DrillProgress) + 2 clear() fixes. Next: Wave 2."
6. If any agent FAILED — fix in main context or spawn fix agent BEFORE proceeding.

---

## WAVE 2: UI + Build + Security (3 agents, parallel, zero file overlap)

### Agent U1: StoryQuizScreen crash + VocabDrillScreen report sheet

**Fixes:** CRITICAL-UI-1, CRITICAL-UI-2

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/StoryQuizScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`

**Files you MUST NOT touch:** SharedReportSheet.kt (read only), TrainingViewModel.kt, GrammarMateApp.kt, any other screen files.

#### Fix 1: StoryQuizScreen — wrap callbacks in LaunchedEffect

**Problem:** Lines 42, 53, 59 call `onClose()` / `onComplete()` directly during composition. This throws `IllegalStateException` because Compose forbids state changes during composition.

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
        onClose()          // CRASH: state change during composition
        return
    }
    // ... state declarations ...
    if (story.questions.isEmpty()) {
        onComplete(true)   // CRASH
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        onComplete(allCorrect)  // CRASH
        return
    }
```

**Fix — replace all 3 direct calls with LaunchedEffect:**

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

The third `LaunchedEffect(allCorrect)` uses `allCorrect` as key so it re-fires if the value changes (though in practice this composable returns immediately so it fires once).

**Important:** Do NOT move the `remember`/`stringResource` calls above the null guard. They must stay where they are because `story` is guaranteed non-null after the guard.

#### Fix 2: VocabDrillScreen — replace inline report sheet with SharedReportSheet

**Problem:** Lines 575-660 contain an inline `ModalBottomSheet` that duplicates SharedReportSheet. Missing "hide card" option.

**SharedReportSheet API** (read from `ui/components/SharedReportSheet.kt`):
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

**Steps:**
1. Ensure `import com.alexpo.grammermate.ui.components.SharedReportSheet` is present (add if missing)
2. Find the `if (showReportSheet)` block (~line 575)
3. Replace the entire inline ModalBottomSheet with:

```kotlin
if (showReportSheet) {
    SharedReportSheet(
        onDismiss = { showReportSheet = false },
        cardPromptText = "${card.word.word} — ${card.word.meaningRu ?: ""}",
        isFlagged = isBadSentence(),
        onFlag = onFlagBadSentence,
        onUnflag = onUnflagBadSentence,
        onHideCard = { showReportSheet = false },
        onExportBadSentences = onExportBadSentences,
        onCopyText = {
            if (reportText.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(reportText))
            }
        },
        exportResult = { path ->
            exportMessage = if (path != null)
                context.getString(R.string.training_exported_to, path)
            else
                context.getString(R.string.training_no_bad_sentences)
        },
        title = stringResource(R.string.vocab_word_options)
    )
}
```

**Important checks before implementing:**
- Verify `clipboardManager` is in scope (search for `val clipboardManager` in the file)
- Verify `reportText` is in scope
- Verify `context` is available (usually via `LocalContext.current`)
- Check if VocabDrillScreen has a meaningful "hide card" action. If not, `onHideCard = { showReportSheet = false }` is correct — it just dismisses.
- If `context` is NOT available, use `exportResult = null` and handle the message inline like the original code did.

**Verification:**
1. Build
2. Grep VocabDrillScreen.kt: `grep "ModalBottomSheet"` — should NOT appear in the report sheet section (only in other sheets if any)
3. Grep: `grep "SharedReportSheet"` — should appear in imports AND usage
4. Grep StoryQuizScreen.kt: `grep "LaunchedEffect"` — should appear 3 times (onClose + 2 onComplete)

---

### Agent S1: Signing config + CI pipeline

**Fixes:** CRITICAL-XC-2, CRITICAL-TEST-1

**Files you OWN:**
- `app/build.gradle.kts` (signing section ONLY — do not touch dependencies or other sections)
- `.github/workflows/ci.yml` (NEW file — create it)

**Files you MUST NOT touch:** Any Kotlin source files, any other gradle files, AndroidManifest.xml.

#### Fix 1: Signing configuration

**Current state** — `build.gradle.kts` buildTypes block (lines 23-31):
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

**Add BEFORE the buildTypes block:**
```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias = System.getenv("KEY_ALIAS") ?: "grammermate"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
    }
}
```

**Add inside the release buildType:**
```kotlin
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
```

This uses environment variables — no credentials in source code. Debug builds are unaffected.

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

**Note:** CI runs on Linux — `./gradlew` works directly. Windows workaround (`java -cp`) is only for local dev.

**Verification:**
1. Build locally with Windows command — debug build must still work
2. `.github/workflows/ci.yml` must parse as valid YAML
3. `git diff --name-only`: `app/build.gradle.kts` + `.github/workflows/ci.yml`

---

### Agent S2: Backup restore validation

**Fixes:** CRITICAL-XC-3

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/data/BackupRestorer.kt`

**Files you MUST NOT touch:** BackupManager.kt, any ViewModel, any UI files.

**Problem:** Both `restoreFromPath()` and `restoreFromUri()` blindly copy YAML/CSV content without validation. Malformed backup can corrupt app state.

**Fix — add validation function:**

```kotlin
private fun validateBackupContent(content: String, fileName: String): Boolean {
    if (content.isBlank()) return false
    val yaml = Yaml()
    return try {
        val data = yaml.load<Any>(content)
        when (data) {
            is Map<*, *> -> {
                val version = data["schemaVersion"]
                if (version != null && version !is Number) return false
                true
            }
            is List<*> -> true
            else -> true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Backup validation failed for $fileName", e)
        false
    }
}
```

**Apply in `restoreFromPath`:**

Before every `AtomicFileWriter.writeText(destFile, content)` call:
```kotlin
val content = srcFile.readText(Charsets.UTF_8)
if (!validateBackupContent(content, srcFile.name)) {
    Log.w(TAG, "Skipping invalid backup file: ${srcFile.name}")
    continue
}
AtomicFileWriter.writeText(destFile, content)
```

For `AtomicFileWriter.copyAtomic(src, dest)` calls, add post-copy validation:
```kotlin
AtomicFileWriter.copyAtomic(srcFile, destFile)
val restored = destFile.readText(Charsets.UTF_8)
if (!validateBackupContent(restored, destFile.name)) {
    destFile.delete()
    Log.w(TAG, "Deleted invalid restored file: ${destFile.name}")
}
```

**Apply same pattern in `restoreFromUri`.**

**IMPORTANT rules:**
- Do NOT reject backups without `schemaVersion` — early versions didn't have it
- Do NOT reject empty sections — a backup with zero mastery data is valid
- ONLY reject: completely blank files, unparseable YAML, or non-numeric schemaVersion
- CSV files (if any in backup) — skip YAML validation for `.csv` extension, just check non-blank

**Verification:**
1. Build
2. Grep BackupRestorer.kt for `validateBackupContent` — must appear before every write
3. Confirm no behavioral change for valid backups

---

### WAVE 2 CHECKPOINT

After all 3 agents complete:
1. `git diff --name-only` — expect 4 files modified + 1 new (ci.yml)
2. Build
3. Run ALL tests
4. Verify no file overlap between U1/S1/S2
5. Report: "Wave 2 complete. 5/5 CRITICALs fixed. Next: Wave 3 (testability)."

---

## WAVE 3: Testability (2 agents, parallel)

### Agent T1: CardSessionStateMachine → StateFlow

**Fixes:** CRITICAL-TEST-3

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/CardSessionStateMachine.kt`

**Files you MUST ALSO modify (consumer updates — minimal):**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeSessionProvider.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillCardSessionProvider.kt` (search for it first)

**Files you MUST NOT touch:** TrainingViewModel.kt, GrammarMateApp.kt, any screen files.

**Problem:** Uses Compose `mutableStateOf` for 6 fields. Requires Compose runtime. Untestable in plain JUnit.

**Current fields** (all use `by mutableStateOf`):
```kotlin
var incorrectAttempts: Int by mutableStateOf(0)
var hintAnswer: String? by mutableStateOf(null)
var showIncorrectFeedback: Boolean by mutableStateOf(false)
var remainingAttempts: Int by mutableStateOf(maxAttempts)
var isPaused: Boolean by mutableStateOf(false)
var voiceTriggerToken: Int by mutableStateOf(0)
```

**Fix — use MutableStateFlow internally, preserve var API externally:**

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CardSessionStateMachine(
    val maxAttempts: Int = 3,
    val answerProvider: (SessionCard) -> String
) {
    private val _incorrectAttempts = MutableStateFlow(0)
    var incorrectAttempts: Int
        get() = _incorrectAttempts.value
        set(value) { _incorrectAttempts.value = value }
    val incorrectAttemptsFlow: StateFlow<Int> = _incorrectAttempts.asStateFlow()

    private val _hintAnswer = MutableStateFlow<String?>(null)
    var hintAnswer: String?
        get() = _hintAnswer.value
        set(value) { _hintAnswer.value = value }
    val hintAnswerFlow: StateFlow<String?> = _hintAnswer.asStateFlow()

    private val _showIncorrectFeedback = MutableStateFlow(false)
    var showIncorrectFeedback: Boolean
        get() = _showIncorrectFeedback.value
        set(value) { _showIncorrectFeedback.value = value }
    val showIncorrectFeedbackFlow: StateFlow<Boolean> = _showIncorrectFeedback.asStateFlow()

    private val _remainingAttempts = MutableStateFlow(maxAttempts)
    var remainingAttempts: Int
        get() = _remainingAttempts.value
        set(value) { _remainingAttempts.value = value }
    val remainingAttemptsFlow: StateFlow<Int> = _remainingAttempts.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    var isPaused: Boolean
        get() = _isPaused.value
        set(value) { _isPaused.value = value }
    val isPausedFlow: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _voiceTriggerToken = MutableStateFlow(0)
    var voiceTriggerToken: Int
        get() = _voiceTriggerToken.value
        set(value) { _voiceTriggerToken.value = value }
    val voiceTriggerTokenFlow: StateFlow<Int> = _voiceTriggerToken.asStateFlow()
```

**Why this approach:** External API is unchanged (`stateMachine.incorrectAttempts = 5` still works). Internally uses StateFlow which works in plain JUnit. The `*Flow` properties are exposed for reactive observation but are optional.

**Consumer updates:**

Search DailyPracticeSessionProvider.kt and VerbDrillCardSessionProvider.kt for:
- `stateMachine.incorrectAttempts` → NO change (getter still works)
- `stateMachine.incorrectAttempts = x` → NO change (setter still works)
- `by stateMachine.xxx` (Compose observation) → if found, switch to `stateMachine.xxxFlow.collectAsState()` — but ONLY if you find this pattern

If consumers only read/write directly (no Compose delegation), ZERO consumer changes needed.

**Remove Compose imports:**
```kotlin
// REMOVE these from CardSessionStateMachine.kt:
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

**Verification:**
1. Build
2. Tests
3. `grep "mutableStateOf" CardSessionStateMachine.kt` — must return ZERO
4. `grep "MutableStateFlow" CardSessionStateMachine.kt` — must return 6

---

### Agent T2: AudioCoordinator — injectable constructor defaults

**Fixes:** CRITICAL-TEST-2

**Files you OWN:**
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt`

**Files you MUST NOT touch:** TrainingViewModel.kt, GrammarMateApp.kt, any screen files, any data files.

**Problem:** SoundPool, TtsEngine, AsrEngine, TtsModelManager, AsrModelManager all instantiated inline in constructor body. Cannot inject fakes for testing.

**Fix — add provider lambdas with defaults:**

```kotlin
class AudioCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val configStore: AppConfigStore,
    // New: injectable providers with exact-current-behavior defaults
    soundPoolProvider: (Context) -> SoundPool = { ctx ->
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
    },
    ttsEngineProvider: (Application) -> TtsEngine = { app ->
        TtsProvider.getInstance(app).ttsEngine
    },
    ttsModelManagerProvider: (Context) -> TtsModelManager = { ctx ->
        TtsModelManager(ctx)
    },
    asrModelManagerProvider: (Context) -> AsrModelManager = { ctx ->
        AsrModelManager(ctx)
    },
    asrEngineProvider: (Context) -> AsrEngine? = { ctx ->
        try { AsrEngine(ctx) } catch (e: Exception) {
            Log.e("AudioCoordinator", "ASR engine creation failed", e)
            null
        }
    }
) {
```

Then replace inline instantiation:
```kotlin
// BEFORE:
private val soundPool = SoundPool.Builder()...build()
val ttsEngine = TtsProvider.getInstance(appContext).ttsEngine
val ttsModelManager = TtsModelManager(appContext)
val asrModelManager = AsrModelManager(appContext)
val asrEngine: AsrEngine? = try { AsrEngine(appContext) } catch ...

// AFTER:
private val soundPool = soundPoolProvider(appContext)
val ttsEngine = ttsEngineProvider(appContext)
val ttsModelManager = ttsModelManagerProvider(appContext)
val asrModelManager = asrModelManagerProvider(appContext)
val asrEngine: AsrEngine? = asrEngineProvider(appContext)
```

Also move sound loading after soundPool:
```kotlin
private val successSoundId = soundPool.load(appContext, R.raw.voicy_correct_answer, 1)
private val errorSoundId = soundPool.load(appContext, R.raw.voicy_bad_answer, 1)
```
These stay as-is since `soundPool` is now from the provider.

**Why this approach:**
- Zero behavioral change (defaults replicate exact current behavior)
- No consumer changes needed (TrainingViewModel, GrammarMateApp don't pass these params)
- Tests can inject no-op providers: `AudioCoordinator(..., soundPoolProvider = { mockSoundPool })`
- Low risk: if defaults are wrong, build fails immediately

**IMPORTANT:** Do NOT add the soundPool.load() calls inside the provider lambda. They must stay in the class body because they need the soundPool instance AND the context.

**Verification:**
1. Build
2. Tests
3. `git diff AudioCoordinator.kt` — only constructor signature + field initialization changed
4. Confirm `val ttsEngine = ttsEngineProvider(appContext)` pattern (not direct instantiation)

---

### WAVE 3 CHECKPOINT

After both agents complete:
1. `git diff --name-only` — expect 3-4 files
2. Build
3. Tests
4. Special check: CardSessionStateMachine has ZERO `mutableStateOf` imports
5. Report: "Wave 3 complete. 9/11 CRITICALs fixed. Remaining: ARCH-1 (research) + XC-1 (research)."

---

## WAVE 4: Research (READ-ONLY, 2 agents parallel)

This wave does NOT change code. It produces recommendation documents for user approval.

### Agent R1: Research — VerbDrillVM + VocabDrillVM architecture decision

**Investigates:** CRITICAL-ARCH-1

**Files to READ (do NOT modify):**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` (scan for drill-related state/methods)
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (how drill VMs are created/destroyed)
- `app/src/main/java/com/alexpo/grammermate/AppContainer.kt` (what stores each VM accesses)

**Questions to answer:**

1. **State overlap analysis:**
   - What state does each drill VM own?
   - Is it a subset of TrainingUiState or completely independent?
   - Do any fields appear in BOTH TrainingViewModel AND a drill VM?

2. **Store overlap analysis:**
   - What stores does each drill VM access through AppContainer?
   - Do any overlap with TrainingViewModel's stores?
   - Could concurrent access to shared stores cause issues?

3. **Lifecycle analysis:**
   - How does GrammarMateApp create/destroy drill VMs?
   - What's their `ViewModelStoreOwner` scope? (Activity? Navigation graph?)
   - When a drill screen is closed, is the VM destroyed or cached?

4. **Cross-VM communication:**
   - How does state sync back? (`vm.refreshVocabMasteryCount()` etc.)
   - Is sync one-directional or bidirectional?
   - What breaks if sync is missed?

5. **Evaluate three options:**

   **Option A: Merge into feature helpers**
   - `VerbDrillRunner` in `feature/training/` owned by TrainingViewModel
   - `VocabDrillRunner` in `feature/vocab/` owned by TrainingViewModel
   - State merged into TrainingViewModel's `combine()`
   - GrammarMateApp: `vm.verbDrill.xxx()` instead of `verbDrillVm.xxx()`
   - Estimate: lines added to TVM, files touched, risk level

   **Option B: Document as exceptions**
   - Update CLAUDE.md to say drill VMs are allowed exceptions
   - Add rationale: independent state, different lifecycle, zero state overlap
   - Estimate: 0 code risk, 1 doc file

   **Option C: Shared DrillViewModelBase**
   - Extract common pattern to base class
   - Keep separate VMs but share lifecycle/store-access pattern
   - Estimate: moderate risk, reduces duplication

**Output format:**
```
## CRITICAL-ARCH-1 Research Report

### State Overlap
[table of overlapping vs independent fields]

### Store Overlap
[table of shared stores]

### Lifecycle
[how VMs are scoped]

### Cross-VM Communication
[what sync mechanisms exist]

### Recommendation: Option A / B / C

### Reasoning
[3-5 sentences]

### Migration Plan (if A or C)
[step-by-step, files touched, estimated lines, risk assessment]

### Cost Estimate
[time + blast radius]
```

---

### Agent R2: Research — Backup encryption design

**Investigates:** CRITICAL-XC-1

**Files to READ (do NOT modify):**
- `app/src/main/java/com/alexpo/grammermate/data/BackupManager.kt`
- `app/src/main/java/com/alexpo/grammermate/data/BackupRestorer.kt`
- `app/src/main/AndroidManifest.xml` (allowBackup, permissions)
- `app/build.gradle.kts` (check for existing crypto dependencies)

**Questions to answer:**

1. **Data sensitivity inventory:**
   - List every store file included in backup
   - For each: what data it contains, how sensitive (PII, preferences, progress)
   - Which files contain user-generated content vs app state?

2. **Current backup locations:**
   - Where are backups stored? (Downloads/BaseGrammy/, internal, SAF?)
   - What format? (single ZIP? individual YAML?)
   - Who has access? (app sandbox? public directory? MediaStore?)

3. **Android backup infrastructure:**
   - What does `android:allowBackup="true"` actually expose?
   - Should we add `android:fullBackupContent` rules?
   - What does `adb backup` capture?

4. **Evaluate four options:**

   **Option A: Password-based encryption (PBKDF2 + AES-GCM)**
   - User sets password in Settings
   - Backups encrypted with AES-256-GCM, key from PBKDF2
   - Can use `javax.crypto` (built into Android, no dependencies)
   - Pros: portable across devices
   - Cons: user must remember password, no recovery

   **Option B: Android Keystore per-device key**
   - Key generated in Keystore, backups auto-encrypted
   - Pros: transparent to user
   - Cons: can't restore on different device

   **Option C: Hybrid (Keystore for auto, password for export)**
   - Internal auto-backups: Keystore
   - User-initiated export: password
   - Pros: best of both
   - Cons: most complex

   **Option D: Restrict access, no encryption**
   - Move backups from Downloads/ to internal storage
   - Disable `allowBackup`
   - Use SAF for user-controlled export only
   - Pros: simplest
   - Cons: doesn't protect against root/adb

5. **Library check:**
   - Can we use `javax.crypto` alone for AES-GCM? (Yes on Android API 24+)
   - Do we need Bouncy Castle / Tink / SQLCipher?
   - What's minimum viable crypto for YAML text?

**Output format:**
```
## CRITICAL-XC-1 Research Report

### Data Sensitivity Inventory
[table: store file | data type | sensitivity level]

### Current Backup Infrastructure
[locations, formats, access patterns]

### Android Backup Exposure
[what allowBackup captures, adb backup scope]

### Recommendation: Option A / B / C / D

### Reasoning
[3-5 sentences]

### Implementation Sketch
[which files change, crypto API calls, estimated effort]

### Trade-offs
[what we gain vs what we lose]

### Cost Estimate
[time + complexity]
```

---

### WAVE 4 CHECKPOINT

After both research agents complete:
1. Review both reports
2. Present options to user
3. User decides on approach
4. Create implementation task files if needed
5. No build/test needed (read-only)

---

## FINAL CHECKLIST

After all 4 waves:

- [ ] Wave 1: 7 store files mutexed (VerbDrill, WordMastery, Streak, Progress, DrillProgress, Mastery clear(), VocabProgress clear())
- [ ] Wave 2: StoryQuizScreen crash fixed, VocabDrillSheet uses SharedReportSheet, signing config added, CI pipeline created, backup validation added
- [ ] Wave 3: CardSessionStateMachine uses StateFlow (zero Compose imports), AudioCoordinator has injectable providers
- [ ] Wave 4: Two research reports produced, user decision recorded
- [ ] Build passes
- [ ] All tests pass
- [ ] `git diff --stat` reviewed — no unexpected files changed
- [ ] Update CHANGELOG.md with re-audit fixes
- [ ] Update task README.md
