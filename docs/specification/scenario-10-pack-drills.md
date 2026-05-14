# Scenario 10: Pack-Scoped Drill Isolation

**Date:** 2026-05-12
**Branch:** feature/daily-cursors
**Scenario:** User has multiple packs installed -> switches active pack -> drills update -> progress is isolated.

---

## Step 1: Active Pack Concept

**How is the active pack determined? Where stored?**

The active pack ID is persisted in `TrainingProgress.activePackId` (`data/Models.kt:134`), saved to `grammarmate/progress.yaml` via `ProgressStore` (`ProgressStore.kt:50,98`).

Resolution logic in `TrainingViewModel` init (`TrainingViewModel.kt:183-199`):
1. Read `savedPackId` from `progress.activePackId`
2. Read all installed pack IDs from `packs.yaml`
3. If `savedPackId` is non-null AND exists in installed packs, use it
4. Otherwise derive from `selectedLessonId` via `lessonStore.getPackIdForLesson(lessonId)`
5. Store result as `initialActivePackId` in `TrainingUiState.activePackId`

On language switch (`selectLanguage`, line 389): derives new `activePackId` from the first lesson's pack or first installed pack for the language.
On lesson switch (`selectLesson`, line 476): derives from `lessonStore.getPackIdForLesson(lessonId)`.
On force reload (line 290-298): keeps existing `activePackId` if pack still exists, otherwise re-derives.

**Expected:** Active pack is a single persisted value, resolved on startup and updated on language/lesson changes.
**Actual:** Matches spec. No discrepancy.

---

## Step 2: Pack A Has Verb/Vocab Drill, Pack B Doesn't -- Tile Visibility

**Code path:**

`GrammarMateApp.kt:168-173` computes `hasVerbDrill` and `hasVocabDrill`:
```kotlin
val hasVerbDrill = remember(state.activePackId, state.selectedLanguageId) {
    state.activePackId?.let { lessonStore.hasVerbDrill(it, state.selectedLanguageId) } ?: false
}
```

`LessonStore.hasVerbDrill(packId, languageId)` (`LessonStore.kt:903-905`) checks if `grammarmate/drills/{packId}/verb_drill/` contains CSV files matching `{languageId}_*`.

`GrammarMateApp.kt:945-963`: Drill tiles rendered only when `hasVerbDrill || hasVocabDrill` is true.

**Expected:** When active pack changes to one without drills, tiles disappear.
**Actual:** Matches spec. The `remember` keys include `state.activePackId`, so switching packs triggers recomputation. Only the ACTIVE pack is checked (not all installed packs).

**No discrepancy.**

---

## Step 3: Switch Pack A to Pack B -- VerbDrillViewModel.reloadForPack()

**Code path:**

`GrammarMateApp.kt:455-462`:
```kotlin
AppScreen.VERB_DRILL -> {
    val verbDrillVm = viewModel<VerbDrillViewModel>()
    val activePackId = state.activePackId
    if (activePackId != null) {
        verbDrillVm.reloadForPack(activePackId)
    } else {
        verbDrillVm.reloadForLanguage(state.selectedLanguageId)
    }
}
```

`VerbDrillViewModel.reloadForPack(packId)` (`VerbDrillViewModel.kt:115-121`):
1. Guard: if `currentPackId == packId && allCards.isNotEmpty()`, return early (no-op)
2. Sets `currentPackId = packId`
3. Creates new `VerbDrillStore(application, packId = packId)` -- replaces the old store
4. Calls `loadCards()` which loads from `lessonStore.getVerbDrillFiles(currentPackId, lang)`
5. Loads `progressMap` from the new store (reads from `drills/{packId}/verb_drill_progress.yaml`)

**What resets:**
- `verbDrillStore` -- new instance scoped to new packId
- `allCards` -- reloaded from new pack's verb_drill directory
- `progressMap` -- loaded from new pack's progress YAML
- `currentPackId` -- updated to new packId
- `packIdForCardId` -- rebuilt with new pack's cards
- `activePackIds` -- rebuilt
- `tenseInfoMap` -- reloaded for current language

**What does NOT reset:**
- `_uiState` session state -- if a session was active, it's overwritten by `isLoading = true` update, but old session remains in state until new cards load

**Expected:** Switching packs fully reloads verb drill data from the new pack.
**Actual:** Matches spec. No discrepancy. The guard `currentPackId == packId && allCards.isNotEmpty()` correctly prevents redundant reloads.

---

## Step 4: Switch Pack A to Pack B -- VocabDrillViewModel.reloadForPack()

**Code path:**

`GrammarMateApp.kt:468-482`:
```kotlin
AppScreen.VOCAB_DRILL -> {
    val vocabDrillVm = viewModel<VocabDrillViewModel>()
    val packId = state.activePackId
    if (packId != null) {
        vocabDrillVm.reloadForPack(packId, state.selectedLanguageId)
    } else {
        vocabDrillVm.reloadForLanguage(state.selectedLanguageId)
    }
}
```

`VocabDrillViewModel.reloadForPack(packId, languageId)` (`VocabDrillViewModel.kt:50-58`):
1. Guard: if same pack, same language, and words loaded, return early
2. Sets `activePackId = packId`
3. Creates new `WordMasteryStore(getApplication(), packId = packId)`
4. Calls `loadWords(languageId)` which loads from `lessonStore.getVocabDrillFiles(pack, lang)`
5. Loads `masteryMap` from the new mastery store

**What resets:**
- `masteryStore` -- new instance scoped to new packId
- `allWords` -- reloaded from new pack's vocab_drill directory
- `masteryMap` -- loaded from `drills/{packId}/word_mastery.yaml`
- `activePackId` -- updated

**Expected:** Switching packs fully reloads vocab drill data from the new pack.
**Actual:** Matches spec. No discrepancy.

---

## Step 5: VerbDrillStore Instances -- TrainingViewModel Creates Fresh Per Call

**Locations where VerbDrillStore is instantiated:**

| Location | Code | packId | Instance Lifetime |
|----------|------|--------|-------------------|
| `VerbDrillViewModel` init (line 44) | `VerbDrillStore(application)` | null (global) | VM lifetime |
| `VerbDrillViewModel.reloadForPack()` (line 118) | `VerbDrillStore(application, packId = packId)` | pack-scoped | Until next reload |
| `TrainingViewModel` init background (line 336) | `VerbDrillStore(getApplication(), packId = packId)` | pack-scoped | Method call |
| `TrainingViewModel.startDailyPractice()` (line 1482) | `VerbDrillStore(getApplication(), packId = packId)` | pack-scoped | Method call |
| `TrainingViewModel.persistDailyVerbProgress()` (line 1629) | `VerbDrillStore(getApplication(), packId = packId)` | pack-scoped | Method call |
| `TrainingViewModel.repeatDailyBlock()` (line 1558) | `VerbDrillStore(getApplication(), packId = packId)` | pack-scoped | Method call |

**Risk assessment:**

TrainingVM creates a NEW VerbDrillStore on every method call (lines 336, 1482, 1558, 1629). Each instance reads/writes the same file (`drills/{packId}/verb_drill_progress.yaml`). Since all instances use the same `packId` (from `state.activePackId`), they all target the same YAML file.

The VerbDrillViewModel caches `progressMap` in memory. If the user does daily practice (which writes via TrainingVM's fresh store) and then navigates to the standalone VerbDrill screen, the ViewModel's cached `progressMap` is STALE until `reloadForPack()` is called.

However, `reloadForPack()` IS called every time the user navigates to `AppScreen.VERB_DRILL` (`GrammarMateApp.kt:459`). The guard `currentPackId == packId && allCards.isNotEmpty()` could skip the reload if the user returns to the same pack. This means the progress map would NOT refresh.

**DISCREPANCY FOUND:** After daily practice writes verb progress, navigating to the standalone VerbDrill screen may show stale progress counts if the guard in `reloadForPack()` returns early (same packId, cards already loaded). The in-memory `progressMap` is not refreshed.

**Severity:** MODERATE. Stale progress display in the standalone VerbDrill screen after daily practice. Self-corrects if the user switches packs and back, or changes language.

---

## Step 6: WordMasteryStore Instances -- 3+ Instances with Different File Paths

**Locations where WordMasteryStore is instantiated:**

| Location | Code | packId | File Path |
|----------|------|--------|-----------|
| `TrainingViewModel` init (line 103) | `WordMasteryStore(application)` | null | `grammarmate/word_mastery.yaml` (GLOBAL) |
| `VocabDrillViewModel` init (line 29) | `WordMasteryStore(application)` | null | `grammarmate/word_mastery.yaml` (GLOBAL) |
| `VocabDrillViewModel.reloadForPack()` (line 56) | `WordMasteryStore(getApplication(), packId = packId)` | pack-scoped | `grammarmate/drills/{packId}/word_mastery.yaml` |
| `TrainingViewModel` init background (line 337) | `WordMasteryStore(getApplication(), packId = packId)` | pack-scoped | `grammarmate/drills/{packId}/word_mastery.yaml` |
| `TrainingViewModel.startDailyPractice()` (line 1483) | `WordMasteryStore(getApplication(), packId = packId)` | pack-scoped | `grammarmate/drills/{packId}/word_mastery.yaml` |
| `TrainingViewModel.rateVocabCard()` (line 1698) | `WordMasteryStore(getApplication(), packId = packId)` | pack-scoped | `grammarmate/drills/{packId}/word_mastery.yaml` |
| `TrainingViewModel.repeatDailyBlock()` (line 1559) | `WordMasteryStore(getApplication(), packId = packId)` | pack-scoped | `grammarmate/drills/{packId}/word_mastery.yaml` |

**DISCREPANCY FOUND (CRITICAL):** `TrainingViewModel.wordMasteryStore` (line 103) is created WITHOUT a packId. This means it reads/writes to the GLOBAL file `grammarmate/word_mastery.yaml`, while all other instances write to pack-scoped files like `grammarmate/drills/{packId}/word_mastery.yaml`.

The TrainingVM init (line 103) global instance is the one used for:
- `vocabMasteredCount` display on HomeScreen (line 256: `wordMasteryStore.getMasteredCount()`)
- `selectLanguage()` (line 453: `wordMasteryStore.getMasteredCount()`)
- `refreshVocabMasteryCount()` (line 3157-3159: `wordMasteryStore.getMasteredCount()`)

But actual mastery writes go to PACK-SCOPED stores via:
- `VocabDrillViewModel` (pack-scoped after `reloadForPack()`)
- `TrainingViewModel.rateVocabCard()` (creates fresh pack-scoped store per call)
- `DailySessionComposer` (receives pack-scoped store from TrainingVM)

**The global file `grammarmate/word_mastery.yaml` is NEVER WRITTEN TO by any code path in normal operation.** The HomeScreen `vocabMasteredCount` will always show 0.

**Severity:** HIGH. HomeScreen vocab mastered count is always 0 (or shows stale legacy data if the global file exists from before pack-scoping was added).

---

## Step 7: Home Screen vocabMasteredCount -- Which Store Instance?

**Code path:**

1. Init: `TrainingViewModel.kt:256` -- `vocabMasteredCount = wordMasteryStore.getMasteredCount()` -- reads from GLOBAL store (no packId)
2. On language switch: `TrainingViewModel.kt:453` -- same global store
3. On return from VocabDrill: `GrammarMateApp.kt:479` calls `vm.refreshVocabMasteryCount()` -> `TrainingViewModel.kt:3157` -- same global store

**Actual mastery writes go to:** `drills/{packId}/word_mastery.yaml` (pack-scoped)

**Expected:** Count reflects words mastered in the active pack.
**Actual:** Count always reads from `grammarmate/word_mastery.yaml` (global, never written to). Always 0 in practice.

**DISCREPANCY:** Confirmed from Step 6. `refreshVocabMasteryCount()` reads from the wrong file.

---

## Step 8: Daily Practice Verb Block -- Which VerbDrillStore Writes Progress?

**Code path for daily practice verb card:**

1. `DailyPracticeSessionProvider.nextCard()` calls `onCardAdvanced(blockCards[currentIndex])`
2. `TrainingViewModel.recordDailyCardPracticed()` -- increments answered count
3. `TrainingViewModel.persistDailyVerbProgress(card)` (`TrainingViewModel.kt:1627-1643`):
   - Creates fresh `VerbDrillStore(getApplication(), packId = packId)` (line 1629)
   - Uses combo key `"|${card.tense ?: ""}"` (empty group, just tense)
   - Writes via `store.upsertComboProgress(comboKey, updated)`

**File written:** `grammarmate/drills/{activePackId}/verb_drill_progress.yaml`

**Expected:** Writes to active pack's verb drill progress file.
**Actual:** Correct. Uses `activePackId` to scope the store. No discrepancy in file targeting.

**NOTE:** The combo key format differs from standalone VerbDrill (`group|tense`) because daily practice uses empty group (`|tense`). This means daily practice progress is stored in separate keys from standalone drill progress. Not a bug per se, but progress data is fragmented.

---

## Step 9: Daily Practice Vocab Block -- Which WordMasteryStore Writes Progress?

**Code path for daily practice vocab card:**

1. `TrainingViewModel.rateVocabCard(rating)` (`TrainingViewModel.kt:1693-1720`):
   - Creates fresh `WordMasteryStore(getApplication(), packId = packId)` (line 1698)
   - Reads current mastery via `store.getMastery(wordId)`
   - Writes updated mastery via `store.upsertMastery(updated)`

**File written:** `grammarmate/drills/{activePackId}/word_mastery.yaml`

**Expected:** Writes to active pack's mastery file.
**Actual:** Correct. Uses `activePackId` to scope the store.

**But:** The count displayed on HomeScreen reads from a DIFFERENT store instance (global, no packId -- see Step 7). So the write is correct, but the read (display) is wrong.

---

## Step 10: Two Packs for Same Language -- Drill Merging

**How are drills handled when two Italian packs are installed?**

Each pack has its own isolated drill directory:
- `grammarmate/drills/PACK_A/verb_drill/`
- `grammarmate/drills/PACK_B/verb_drill/`

Drills are NEVER merged across packs. All drill access methods require a `packId` parameter:
- `LessonStore.hasVerbDrill(packId, languageId)` checks one pack only
- `LessonStore.getVerbDrillFiles(packId, languageId)` loads from one pack only
- `VerbDrillStore` is constructed with a specific `packId`

Only ONE pack is active at a time (`activePackId`). The user sees drills for the active pack only.

The only place where multiple packs are scanned is in `VocabDrillViewModel.loadWords()` when no pack is active (`VocabDrillViewModel.kt:85-88`):
```kotlin
lessonStore.getInstalledPacks().flatMap { installedPack ->
    lessonStore.getVocabDrillFiles(installedPack.packId, lang)
}
```
This is the legacy fallback path (no active pack), which merges vocab from all packs.

**Expected:** Drills are per-pack, not merged.
**Actual:** Matches spec for the normal (pack-scoped) path. Legacy path merges all packs, but this path is only reached when `activePackId` is null, which is unlikely in normal operation.

**No discrepancy in normal operation.**

---

## Step 11: Pack Removal -- Drill Progress Files

**Code path:**

`LessonStore.removeInstalledPackData(packId)` (`LessonStore.kt:219-249`):
1. Reads manifest to get lesson IDs and language ID
2. Deletes each lesson via `deleteLesson(languageId, lessonId)`
3. Removes pack entry from `packs.yaml`
4. Calls `deletePackDrills(packId)` (line 229) -- deletes `grammarmate/drills/{packId}/` recursively

`deletePackDrills()` (`LessonStore.kt:244-249`):
```kotlin
private fun deletePackDrills(packId: String) {
    val drillsDir = File(baseDir, "drills/$packId")
    if (drillsDir.exists()) {
        drillsDir.deleteRecursively()
    }
}
```

This deletes:
- `drills/{packId}/verb_drill/` (CSV files)
- `drills/{packId}/vocab_drill/` (CSV files)
- `drills/{packId}/verb_drill_progress.yaml` (progress)
- `drills/{packId}/word_mastery.yaml` (progress)

**Expected:** All drill data and progress for the pack are deleted.
**Actual:** Matches spec. `deleteRecursively()` removes the entire `drills/{packId}/` tree. No discrepancy.

---

## Step 12: Pack Import -- When Are Drill Files Copied?

**Code path:**

During `importPackFromTempDir()` (`LessonStore.kt:301-360`):
1. Standard lessons imported (line 321-329)
2. `importPackDrills(packDir, manifest)` called (line 332)
3. Stories and vocab imported (line 334-335)

`importPackDrills()` (`LessonStore.kt:807-824`):
- For each file in `manifest.verbDrill.files[]`: copies to `drills/{packId}/verb_drill/{filename}`
- For each file in `manifest.vocabDrill.files[]`: copies to `drills/{packId}/vocab_drill/{filename}`
- Missing files are silently skipped (`return@forEach`)

**Expected:** Drill files copied to pack-scoped directories during import.
**Actual:** Matches spec. Note that progress files (`verb_drill_progress.yaml`, `word_mastery.yaml`) are NOT created during import -- they are created on first write by VerbDrillStore/WordMasteryStore.

**No discrepancy.**

---

## Step 13: hasVerbDrill / hasVocabDrill Checks -- Active Pack or All Packs?

**Code path:**

`GrammarMateApp.kt:168-173`:
```kotlin
val hasVerbDrill = remember(state.activePackId, state.selectedLanguageId) {
    state.activePackId?.let { lessonStore.hasVerbDrill(it, state.selectedLanguageId) } ?: false
}
```

If `activePackId` is null, `hasVerbDrill` defaults to `false`.

`LessonStore.hasVerbDrill(packId, languageId)` (`LessonStore.kt:903-905`):
```kotlin
fun hasVerbDrill(packId: String, languageId: String): Boolean {
    return getVerbDrillFiles(packId, languageId).isNotEmpty()
}
```

Checks ONLY the given pack. Does NOT scan all packs.

**Expected:** Checks only the active pack.
**Actual:** Matches spec. Only the active pack is checked. If `activePackId` is null, no drill tiles are shown.

**No discrepancy.**

---

## Step 14: Drill File Loading -- From Assets or Internal Storage?

**Code path:**

1. `LessonStore.getVerbDrillFiles(packId, languageId)` (`LessonStore.kt:830-836`) reads from `File(baseDir, "drills/$packId/verb_drill")` -- internal storage.

2. `VerbDrillStore.loadAllCardsForPack(targetPackId, languageId)` (`VerbDrillStore.kt:92-105`) reads from `File(baseDir, "drills/$targetPackId/verb_drill")` -- internal storage.

3. `VocabDrillViewModel.loadWords()` reads from files returned by `lessonStore.getVocabDrillFiles(pack, lang)` -- internal storage.

4. `DailySessionComposer` reads verb drill cards via `VerbDrillStore.loadAllCardsForPack()` -- internal storage.

Assets are only used during import:
- `importPackFromAssetsInternal()` reads from `context.assets.open(assetPath)`
- After extraction, files are copied to internal storage

**Expected:** Runtime drill loading reads from internal storage (previously imported files).
**Actual:** Matches spec. Assets are only used during import. All runtime reads target `context.filesDir/grammarmate/drills/{packId}/`.

**No discrepancy.**

---

## Step 15: Cross-Pack Progress Leak

**Can pack A's drill state appear in pack B's drill?**

**Verb drill progress:**
- `VerbDrillStore` is constructed with a `packId` parameter
- File path: `drills/{packId}/verb_drill_progress.yaml`
- Each pack has its own progress file
- `persistDailyVerbProgress()` uses `activePackId` from UI state
- `VerbDrillViewModel` uses `currentPackId` set by `reloadForPack()`

**Vocab mastery progress:**
- `WordMasteryStore` is constructed with a `packId` parameter
- File path: `drills/{packId}/word_mastery.yaml`
- Each pack has its own mastery file
- `rateVocabCard()` uses `activePackId` from UI state
- `VocabDrillViewModel` uses `activePackId` set by `reloadForPack()`

**Potential leak vectors analyzed:**
1. Store instances are correctly scoped to `activePackId` in all pack-scoped code paths
2. File paths are constructed from `packId` parameter -- different packIds produce different paths
3. No code reads from "all packs" for progress (only for drill CSV files in legacy fallback)

**Expected:** No cross-pack progress leak.
**Actual:** No cross-pack progress leak. Progress files are correctly isolated by `packId`.

**No discrepancy.**

---

## Summary of Findings

### CRITICAL Issues

| # | Issue | Files | Impact |
|---|-------|-------|--------|
| C1 | `vocabMasteredCount` on HomeScreen always reads from GLOBAL `word_mastery.yaml` (no packId), while all writes go to pack-scoped files | `TrainingViewModel.kt:103,256,3157` | HomeScreen always shows 0 mastered words. The count never updates. |
| C2 | `TrainingVM.wordMasteryStore` is created without `packId` at line 103, used for all mastery count reads | `TrainingViewModel.kt:103` | Same as C1 -- wrong file is read. |

### MODERATE Issues

| # | Issue | Files | Impact |
|---|-------|-------|--------|
| M1 | VerbDrillViewModel caches `progressMap` in memory; stale after daily practice writes to same file via TrainingVM | `VerbDrillViewModel.kt:54,160,413` and `TrainingViewModel.kt:1629` | After daily practice, standalone VerbDrill screen shows stale progress until forced reload. The guard in `reloadForPack()` (same packId, cards loaded) prevents refresh. |
| M2 | Verb drill progress combo key differs between daily practice (`\|tense`) and standalone drill (`group\|tense`) | `TrainingViewModel.kt:1630` vs `VerbDrillViewModel.kt:394` | Progress data is fragmented; cards shown in daily practice don't update the same keys as standalone drill. |
| M3 | VerbDrillStore and WordMasteryStore are created fresh on every method call in TrainingVM (4-5 instances per daily session) | `TrainingViewModel.kt:336,1482,1558,1629,1698` | No data corruption risk (all target same file), but unnecessary object creation and disk I/O. |

### No Discrepancy (Spec-Compliant)

| Step | Feature | Status |
|------|---------|--------|
| 1 | Active pack resolution and persistence | Compliant |
| 2 | Drill tile visibility based on active pack | Compliant |
| 3 | VerbDrillViewModel.reloadForPack() resets correctly | Compliant |
| 4 | VocabDrillViewModel.reloadForPack() resets correctly | Compliant |
| 10 | Two same-language packs don't merge drills | Compliant |
| 11 | Pack removal deletes drill progress files | Compliant |
| 12 | Pack import copies drill files to scoped directories | Compliant |
| 13 | hasVerbDrill/hasVocabDrill checks only active pack | Compliant |
| 14 | Drill files loaded from internal storage, not assets | Compliant |
| 15 | No cross-pack progress leak | Compliant |

---

## Recommended Fixes

### Fix C1/C2: Update `refreshVocabMasteryCount()` and init to use pack-scoped store

```kotlin
// TrainingViewModel.kt line 3157
fun refreshVocabMasteryCount() {
    val packId = _uiState.value.activePackId
    val store = if (packId != null) {
        WordMasteryStore(getApplication(), packId = packId)
    } else {
        wordMasteryStore  // global fallback
    }
    val count = store.getMasteredCount()
    _uiState.update { it.copy(vocabMasteredCount = count) }
}
```

Same fix needed at line 256 (init) and line 453 (selectLanguage).

### Fix M1: Force progress refresh in VerbDrillViewModel when navigating to VerbDrill screen

Option A: Remove the `allCards.isNotEmpty()` guard from `reloadForPack()` so progress is always reloaded:
```kotlin
fun reloadForPack(packId: String) {
    if (currentPackId == packId) {
        // Just refresh progress map, don't reload cards
        progressMap = verbDrillStore.loadProgress()
        updateProgressDisplay()
        return
    }
    currentPackId = packId
    verbDrillStore = VerbDrillStore(application, packId = packId)
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch { loadCards() }
}
```

### Fix M2: Harmonize combo key format

Use `group|tense` in both daily practice and standalone drill, or use a single canonical format.

### Fix M3: Cache VerbDrillStore and WordMasteryStore instances in TrainingViewModel

Instead of creating fresh instances per method call, store them as class members and update when `activePackId` changes.
