# Verb Drill Mode — Implementation Plan

**Date:** 2026-05-09
**Spec:** `docs/superpowers/specs/2026-05-09-verb-drill-design.md`
**Branch:** `feature/verb-drill`
**Approach:** Isolated (new VerbDrillViewModel + VerbDrillScreen, no changes to TrainingViewModel)

---

## Step 1: Data Models

**Files created:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillProgress.kt`

**What:**

Define the two core data classes the rest of the feature depends on.

`VerbDrillCard`:
```kotlin
data class VerbDrillCard(
    val id: String,          // deterministic: "{group}_{tense}_{index}"
    val promptRu: String,    // Russian prompt
    val answer: String,      // Italian target sentence
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null
)
```

`VerbDrillProgress`:
```kotlin
data class VerbDrillComboProgress(
    val group: String,        // group filter (empty string = all)
    val tense: String,        // tense filter (empty string = all)
    val totalCards: Int,
    val everShownCardIds: Set<String>,
    val todayShownCardIds: Set<String>,
    val lastDate: String      // "2026-05-09" for daily reset check
)
```

Also add `VerbDrillSessionState` data class to hold the active session cards and current index:
```kotlin
data class VerbDrillSessionState(
    val cards: List<VerbDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)
```

And `VerbDrillUiState` for the screen:
```kotlin
data class VerbDrillUiState(
    val availableTenses: List<String> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val selectedTense: String? = null,   // null = "all"
    val selectedGroup: String? = null,   // null = "all"
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: VerbDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true
)
```

**Verification:** Build compiles. No logic yet — pure data classes.

---

## Step 2: CSV Parser

**File created:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCsvParser.kt`

**What:**

Parse verb drill CSV files. Format:
```
Title line (first non-blank line)
RU;IT;Verb;Tense;Group
...data rows...
```

Parser logic:
1. Read first non-blank line as title (skip it for card parsing).
2. Read second non-blank line as header to detect available columns. Expected columns: `RU`, `IT`, `Verb`, `Tense`, `Group`. `RU` and `IT` are required; others optional.
3. Map column indices from header.
4. For each subsequent data row: split by `;` (reuse `CsvParser.parseLine`-style logic — handle quoted fields), extract fields by column index.
5. Generate deterministic ID: `"{group}_{tense}_{rowIndex}"` (using optional values, falling back to empty string).
6. Return `Pair<String?, List<VerbDrillCard>>` (title + cards).

Reuse the semicolon parsing logic from `CsvParser.kt` (the `parseLine` method). Either extract it to a shared utility or duplicate the small function. Given the "isolated approach" and to avoid touching existing files, duplicate it as a private function in `VerbDrillCsvParser`.

**Verification:** Build compiles. Write a quick manual test in the parser's companion to verify a sample string parses correctly (or just trust the build).

---

## Step 3: Progress Store

**File created:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

**What:**

YAML-backed progress store. Uses `AtomicFileWriter` for all writes.

File location: `context.filesDir/grammarmate/verb_drill_progress.yaml`

YAML structure:
```yaml
combo:
  regular_are|Presente:
    totalCards: 247
    everShownCardIds:
      - regular_are_Presente_0
      - regular_are_Presente_1
    todayShownCardIds:
      - regular_are_Presente_0
    lastDate: "2026-05-09"
```

Methods:
- `loadProgress(): Map<String, VerbDrillComboProgress>` — read YAML, parse into map keyed by `"{group}|{tense}"`. On load: compare each entry's `lastDate` with today's date (`java.time.LocalDate.now().toString()`). If different, clear `todayShownCardIds` and update `lastDate`.
- `saveProgress(progress: Map<String, VerbDrillComboProgress>)` — serialize to YAML, write via `AtomicFileWriter`.
- `getComboProgress(key: String): VerbDrillComboProgress?` — convenience getter.
- `upsertComboProgress(key: String, progress: VerbDrillComboProgress)` — update single entry and save.

Use `org.yaml.snakeyaml.Yaml` for serialization (already a project dependency). Follow the pattern from `ProgressStore.kt` or `MasteryStore.kt`.

**Verification:** Build compiles. Store reads/writes a YAML file correctly.

---

## Step 4: Manifest Extension + LessonStore Integration

**Files modified:**
- `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt`
- `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt`

**What:**

### 4a. Add `type` field to `LessonPackLesson`

In `LessonPackManifest.kt`, add a `type` field:
```kotlin
data class LessonPackLesson(
    val lessonId: String,
    val order: Int,
    val title: String?,
    val file: String,
    val drillFile: String? = null,
    val type: String = "standard"  // NEW: "standard" or "verb_drill"
)
```

Update `LessonPackManifest.fromJson` to read `type` from the JSON entry:
```kotlin
val type = entry.optString("type", "standard").trim()
```

Default `"standard"` ensures backward compatibility — existing packs without `type` continue working.

### 4b. Handle `verb_drill` type in LessonStore import

In `LessonStore.kt`, in the `importPackFromTempDir` method's lesson loop (around line 294):

```kotlin
lessonEntries.forEach { entry ->
    val sourceFile = File(packDir, entry.file)
    if (!sourceFile.exists()) error("Missing lesson file: ${entry.file}")
    if (entry.type == "verb_drill") {
        importVerbDrillFile(languageId, sourceFile, entry.lessonId)
    } else {
        val drillSourceFile = entry.drillFile?.let { File(packDir, it) }
        importLessonFromFile(languageId, sourceFile, entry.title, entry.lessonId, drillSourceFile = drillSourceFile)
    }
}
```

Add `importVerbDrillFile` private method:
```kotlin
private fun importVerbDrillFile(languageId: String, sourceFile: File, lessonId: String) {
    val verbDrillDir = File(baseDir, "verb_drill")
    verbDrillDir.mkdirs()
    val targetFile = File(verbDrillDir, "${languageId}_${lessonId}.csv")
    sourceFile.inputStream().use { input ->
        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
    }
}
```

Also add a public accessor for verb drill CSV files:
```kotlin
fun getVerbDrillFiles(languageId: String): List<File> {
    val verbDrillDir = File(baseDir, "verb_drill")
    if (!verbDrillDir.exists()) return emptyList()
    return verbDrillDir.listFiles()
        ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
        ?: emptyList()
}
```

**Verification:** Build compiles. Existing packs import unchanged. A pack with `type: "verb_drill"` entries extracts CSVs to the `verb_drill/` directory.

---

## Step 5: VerbDrillViewModel

**File created:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`

**What:**

AndroidViewModel that owns all verb drill logic. Pattern follows `TrainingViewModel` structure but is completely separate.

Constructor receives `Application` context. Internally creates:
- `VerbDrillStore(application)`
- `LessonStore(application)` (for `getVerbDrillFiles`)

Exposed state: `StateFlow<VerbDrillUiState>`

Key methods:

### `init` / `loadCards()`
- Call `lessonStore.getVerbDrillFiles(languageId)` for each file.
- Parse each with `VerbDrillCsvParser.parse`.
- Accumulate all cards into a single `List<VerbDrillCard>`.
- Extract distinct tense values and group values for dropdown population.
- Load progress from `VerbDrillStore`.
- Update UI state with available tenses/groups and progress for current selection.

### `selectTense(tense: String?)` / `selectGroup(group: String?)`
- Update selected filter in UI state.
- Recalculate progress display for the selected combo.

### `startSession()`
- Filter all cards by selected tense/group.
- Exclude cards already in `todayShownCardIds` for this combo.
- Pick up to 10 random cards from remaining.
- If 0 remain → set `allDoneToday = true`.
- Create `VerbDrillSessionState` with selected cards.

### `submitAnswer(input: String)`
- Compare input against current card's answer (normalize whitespace, case-insensitive).
- If correct: increment `correctCount`, mark card as shown.
- If incorrect: increment `incorrectCount`.
- In both cases: add card ID to `everShownCardIds` and `todayShownCardIds` for the active combo.
- Persist immediately via `VerbDrillStore.upsertComboProgress`.
- Advance `currentIndex`.
- If all cards done → set `isComplete = true`.

### `nextBatch()`
- Same as `startSession()` but picks the next 10 from the remaining pool.

### `exitSession()`
- Set `session = null` in UI state. Return to selection screen.

### Progress key construction
```kotlin
private fun comboKey(group: String?, tense: String?): String {
    return "${group ?: ""}|${tense ?: ""}"
}
```

**Verification:** Build compiles. ViewModel can be instantiated without crash.

---

## Step 6: VerbDrillScreen (Compose UI)

**File created:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`

**What:**

Two Compose screens:

### `VerbDrillSelectionScreen`
- Dropdowns for tense and group (only shown if corresponding data exists).
- Progress bar: `everShownCount / totalCards` for selected combo.
- Today count display.
- "Start" button (or "Continue" if `todayShownCount > 0`).
- "All done today" message if `allDoneToday == true`.

### `VerbDrillSessionScreen`
- Reuse the training input pattern from `GrammarMateApp.kt`:
  - Card prompt display (Russian text).
  - Text input field with submit button.
  - Answer feedback (correct/incorrect with expected answer shown).
  - TTS button (optional — reuse `TtsEngine` if straightforward, or skip for MVP).
- After 10 cards: congratulations overlay (reuse existing congratulations pattern from GrammarMateApp).
- "Next" / "Exit" buttons after completion.

Both screens take the `VerbDrillViewModel` as a parameter and collect its state flow.

Follow the existing Compose patterns from `GrammarMateApp.kt`: same Material 3 styling, same card/input layout patterns.

**Verification:** Build compiles. Screens render without crash when navigated to.

---

## Step 7: Roadmap Integration

**Files modified:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`

**What:**

### 7a. Add `VERB_DRILL` to `LessonTileState`

```kotlin
private enum class LessonTileState {
    SEED,
    SPROUT,
    FLOWER,
    LOCKED,
    UNLOCKED,
    EMPTY,
    VERB_DRILL  // NEW
}
```

### 7b. Add `VERB_DRILL` to `AppScreen`

```kotlin
private enum class AppScreen {
    HOME,
    LESSON,
    ELITE,
    VOCAB,
    STORY,
    TRAINING,
    LADDER,
    VERB_DRILL  // NEW
}
```

### 7c. Detect verb drill lessons in tile building

In the roadmap section, after building standard lesson tiles, check if any imported pack has `verb_drill` type lessons. If yes, append a `VERB_DRILL` tile to the tile list.

This requires reading the installed pack manifests to check for `type: "verb_drill"` entries. Add a helper method in `LessonStore`:
```kotlin
fun hasVerbDrillLessons(languageId: String): Boolean
```

In `buildLessonTiles` (or in the calling code), if `hasVerbDrillLessons` returns true, add a special tile:
```kotlin
LessonTileUi(index = tiles.size, lessonId = "verb_drill", state = LessonTileState.VERB_DRILL)
```

### 7d. Render the VERB_DRILL tile

In the tile rendering section of `GrammarMateApp.kt`, add a case for `VERB_DRILL`:
- Use a distinct icon (e.g., FitnessCenter icon from Material Icons, or a verb-specific emoji).
- Always show as "unlocked" (no lock state).
- On tap: navigate to `AppScreen.VERB_DRILL`.

### 7e. Wire up VerbDrillScreen navigation

In the main `when` block that renders screens, add:
```kotlin
AppScreen.VERB_DRILL -> {
    val verbDrillVm: VerbDrillViewModel by viewModel()
    VerbDrillSelectionScreen(
        state = verbDrillVm.uiState.collectAsState().value,
        onSelectTense = verbDrillVm::selectTense,
        onSelectGroup = verbDrillVm::selectGroup,
        onStart = verbDrillVm::startSession,
        onBack = { /* navigate back to HOME */ }
    )
}
```

When a session is active (`state.session != null`), render `VerbDrillSessionScreen` instead.

**Verification:** Build compiles. A `VERB_DRILL` tile appears on the roadmap when verb drill packs are installed. Tapping it opens the drill screen.

---

## Step 8: Build Verification

**What:**

Full build and smoke test:

1. Run the full Gradle build:
   ```bash
   java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
   ```

2. Verify the APK builds without errors.

3. Verify no regressions: existing lesson import, training, roadmap still work (manual check on device/emulator if possible).

4. If verb drill pack is installed, verify:
   - `VERB_DRILL` tile appears on roadmap
   - Tapping opens selection screen with dropdowns
   - Selecting filters and starting a session works
   - Progress is persisted and survives app restart
   - Daily reset works (change device date, verify today cards reset)

**If build fails:** fix compilation errors iteratively. Common issues will be missing imports, incorrect ViewModel constructor signature, or Compose preview issues.

---

## Dependency Order

```
Step 1 (models)
    |
    v
Step 2 (parser) ──> Step 3 (store)
    |                    |
    v                    v
Step 4 (manifest + LessonStore)
    |
    v
Step 5 (ViewModel) ── uses parser, store, LessonStore
    |
    v
Step 6 (Compose UI)
    |
    v
Step 7 (roadmap integration)
    |
    v
Step 8 (build verification)
```

Steps 2 and 3 can be done in parallel (both depend only on Step 1).
Steps 5 and 6 can be done in parallel (5 provides the ViewModel, 6 renders it), though 6 needs 5's public API defined.

---

## Files Summary

### New files (6)
| # | File | Step |
|---|------|------|
| 1 | `data/VerbDrillCard.kt` | 1 |
| 2 | `data/VerbDrillProgress.kt` | 1 |
| 3 | `data/VerbDrillCsvParser.kt` | 2 |
| 4 | `data/VerbDrillStore.kt` | 3 |
| 5 | `ui/VerbDrillViewModel.kt` | 5 |
| 6 | `ui/VerbDrillScreen.kt` | 6 |

### Modified files (3)
| # | File | Step | Change |
|---|------|------|--------|
| 1 | `data/LessonPackManifest.kt` | 4 | Add `type` field to `LessonPackLesson` |
| 2 | `data/LessonStore.kt` | 4 | Handle `verb_drill` type, add `getVerbDrillFiles()`, `hasVerbDrillLessons()` |
| 3 | `ui/GrammarMateApp.kt` | 7 | Add `VERB_DRILL` tile state, screen navigation, tile rendering |

### Untouched
- `TrainingViewModel.kt` — zero changes
- `CsvParser.kt` — zero changes (parser logic duplicated in `VerbDrillCsvParser`)
- Existing packs — `type` defaults to `"standard"`
