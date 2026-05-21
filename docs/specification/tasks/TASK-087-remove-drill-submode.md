# TASK-087: Remove Drill Sub-mode Completely

**Status:** OPEN
**Created:** 2026-05-21
**Branch:** feature/remove-drill-submode (from main)
**Spec:** 01-models-and-state.md, 02-data-stores.md, 08-training-viewmodel.md, 15-lesson-content-and-packs.md, 22-use-case-registry.md
**UC:** UC-69, UC-70 (deprecated), UC-71 AC5, UC-72 AC8/AC9, UC-73 AC1
**Scenario:** scenario-16-drill-sublesson.md (removed)

---

## Problem

Drill sub-mode is deeply integrated into the codebase but creates unnecessary complexity. The application fundamentally has only TWO modes:
1. **Sentence training** (SessionCard-based: SentenceCard + VerbDrillCard) with mastery/flower system
2. **Word training** (VocabWord-based) with Anki-like spaced repetition

Drill sub-mode was a lesson-scoped drill practice that doesn't fit cleanly into either category. The user wants to:
1. Remove ALL drill sub-mode code from the application
2. Migrate existing drill content to a standalone package `EN_WORD_ORDER_A1_DRILLS`
3. Clean up original `EN_WORD_ORDER_A1` package by removing `drillFile` references
4. Keep Verb Practice untouched (it's an independent feature)
5. Keep VocabDrill untouched (separate word training mode)
6. Force update packages (discard existing user progress - acceptable since app is in testing)

**Root cause:** Drill sub-mode adds complexity without providing unique value. The same practice can be achieved through standalone drill packages.

---

## Changes

### Fix 1: Remove PracticeType.SUB_DRILL enum value
**Discrepancy:** PracticeType enum contains SUB_DRILL value | **UC:** UC-71 AC5 | **Spec:** 01-models-and-state.md

Remove `SUB_DRILL` from the `PracticeType` enum in `Models.kt`. This reduces practice types from 4 to 3:
- TRANSLATION (sentence training)
- VERB (verb drill)  
- VOCAB (vocab drill)

**Files:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt` — `PracticeType` enum

**Verification:** 
- `PracticeType.entries.size == 3`
- No references to `PracticeType.SUB_DRILL` in codebase
- Fire streak max reduced from 4 to 3 (`todayFireCount` max value)

### Fix 2: Remove DrillProgressStore completely
**Discrepancy:** DrillProgressStore exists for drill progress | **UC:** UC-69, UC-70 | **Spec:** 02-data-stores.md §2.4

Delete the entire `DrillProgressStore.kt` file and remove all references to it:
- Remove `drillProgressStore` field from `TrainingViewModel`
- Remove all `drillProgressStore.getDrillProgress()`, `saveDrillProgress()`, `hasProgress()`, `clearDrillProgress()` calls
- Remove `drill_progress_{lessonId}.yaml` file format from backup/restore logic

**Files:** 
- `app/src/main/java/com/alexpo/grammermate/data/DrillProgressStore.kt` (DELETE)
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — remove drillProgressStore field and usage

**Verification:**
- `DrillProgressStore.kt` file does not exist
- No compilation errors related to drillProgressStore
- Backup manager no longer references drill_progress files

### Fix 3: Remove TrainingScreenMode.DRILL enum value
**Discrepancy:** TrainingScreenMode contains DRILL value | **UC:** UC-69 | **Spec:** 01-models-and-state.md

Remove `DRILL` from the `TrainingScreenMode` enum in `Models.kt`. This eliminates drill mode as a distinct screen mode.

**Files:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt` — `TrainingScreenMode` enum

**Verification:**
- `TrainingScreenMode.entries` does not contain `DRILL`
- No references to `TrainingScreenMode.DRILL` in codebase

### Fix 4: Remove all drill methods from SessionRunner
**Discrepancy:** SessionRunner contains drill-specific methods | **UC:** UC-69, UC-70 | **Spec:** 08-training-viewmodel.md §2.10

Remove the following drill methods from `SessionRunner.kt`:
- `showDrillStartDialog(lessonId)`
- `startDrill(resume)`
- `dismissDrillDialog()`
- `loadDrillCard(cardIndex, activate)`
- `advanceDrillCard()`
- `finishDrill(lessonId)`
- `exitDrillMode()`

Remove drill-related logic from:
- `buildSessionCards()` — remove `isDrillMode` early return
- `recordCardShowForMastery()` — remove drill mode check
- `flagBadSentence()` — remove drill mode handling

**Files:** `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt`

**Verification:**
- No drill methods exist in SessionRunner
- `buildSessionCards()` does not check `isDrillMode`
- Navigation flow does not reference drill completion

### Fix 5: Remove DrillState from TrainingUiState
**Discrepancy:** TrainingUiState contains drill state fields | **UC:** UC-69 | **Spec:** 01-models-and-state.md

Remove the following fields from `TrainingUiState` data class:
- `isDrillMode: Boolean = false`
- `drillCardIndex: Int = 0`
- `drillTotalCards: Int = 0`
- `drillShowStartDialog: Boolean = false`
- `drillHasProgress: Boolean = false`

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — `TrainingUiState` data class

**Verification:**
- `TrainingUiState` does not contain drill fields
- No UI state updates reference drill fields

### Fix 6: Remove drillFile field from LessonPackManifest
**Discrepancy:** LessonPackLesson contains drillFile field | **UC:** UC-69 | **Spec:** 15-lesson-content-and-packs.md, data/LessonPackManifest.kt

Remove the `drillFile` field from the `LessonPackLesson` data class in `LessonPackManifest.kt`. Update pack import logic to ignore drillFile in manifests.

**Files:** 
- `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt` — `LessonPackLesson` data class
- `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` — `importLessonFromFile()` method

**Verification:**
- `LessonPackLesson` does not contain `drillFile` field
- Pack importer ignores drillFile if present in old manifests
- No drill CSV files copied to `grammarmate/lessons/{languageId}/lesson_{lessonId}_drill.csv`

### Fix 7: Remove drillCards field from Lesson data class
**Discrepancy:** Lesson contains drillCards field | **UC:** UC-69 | **Spec:** 01-models-and-state.md

Remove the `drillCards: List<SentenceCard> = emptyList()` field from the `Lesson` data class in `Models.kt`.

**Files:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt` — `Lesson` data class

**Verification:**
- `Lesson` data class does not contain `drillCards` field
- No code references `lesson.drillCards`

### Fix 8: Remove drill UI components and dialogs
**Discrepancy:** UI contains drill-specific components | **UC:** UC-69 | **Spec:** 23-screen-elements.md

Remove the following UI components:
- `DrillStartDialog` (dialog for starting drill session)
- Drill tile on LessonRoadmapScreen (LR-08)
- Drill-specific visual styling (green accents on prompt/tense labels)
- Drill navigation controls

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt` — remove drill tile
- `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` — remove drill styling
- Any drill dialog composables in UI files

**Verification:**
- No drill dialog composables exist
- LessonRoadmapScreen does not show drill tiles
- TrainingScreen does not apply drill-specific styling

### Fix 9: Update fire streak logic to remove SUB_DRILL
**Discrepancy:** Fire streak records SUB_DRILL practice type | **UC:** UC-71 AC5, UC-72 | **Spec:** 22-use-case-registry.md

Update `StreakManager` and related code to remove `SUB_DRILL` from practice type tracking:
- Remove `PracticeType.SUB_DRILL` from recording logic
- Update `todayFireCount` max from 4 to 3
- Remove drill sub-mode from streak display logic

**Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/progress/StreakManager.kt`
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` — streak indicator

**Verification:**
- `StreakData.todayFireCount` max is 3
- No SUB_DRILL recording in session completion logic
- Streak indicator shows max 3 fires for all packs

### Fix 10: Remove drill from bad sentence reporting
**Discrepancy:** UC-73 mentions drill mode in reporting | **UC:** UC-73 AC1 | **Spec:** 22-use-case-registry.md

Ensure `SharedReportSheet` and bad sentence logic work correctly without drill mode references. Update UC-73 to remove drill from the list of supported modes.

**Files:** 
- `app/src/main/java/com/alexpo/grammermate/ui/components/SharedReportSheet.kt` (verify no drill-specific logic)
- `docs/specification/22-use-case-registry.md` — update UC-73

**Verification:**
- Bad sentence reporting works in Training, VerbDrill, DailyPractice, VocabDrill
- UC-73 AC1 no longer mentions drill mode
- No drill-specific conditional logic in report sheet

### Fix 11: Create migrator for EN_WORD_ORDER_A1_DRILLS package
**Discrepancy:** Need to migrate drill content to standalone package | **UC:** N/A | **Spec:** New package requirement

Create a new standalone drill package `EN_WORD_ORDER_A1_DRILLS` with:
- Single large lesson containing ALL drill cards from original `EN_WORD_ORDER_A1` package
- Package manifest with standard lesson structure (no drillFile references)
- All drill CSV content consolidated into one main lesson CSV

**Files:** 
- Create new package ZIP: `EN_WORD_ORDER_A1_DRILLS.zip`
- Update `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` — add to defaultPacks if needed

**Verification:**
- New package `EN_WORD_ORDER_A1_DRILLS` installs successfully
- Package contains one lesson with all drill cards
- Lesson functions as standard sentence training (no drill mode)
- All original drill content is accessible

### Fix 12: Clean EN_WORD_ORDER_A1 package manifest
**Discrepancy:** Original package contains drillFile references | **UC:** N/A | **Spec:** 15-lesson-content-and-packs.md

Update the `EN_WORD_ORDER_A1` package manifest to remove all `drillFile` references from lesson entries. Clean up any remaining drill CSV files from the package.

**Files:** 
- Update `EN_WORD_ORDER_A1.zip` manifest
- Remove `lesson_XX_drill.csv` files from package

**Verification:**
- `EN_WORD_ORDER_A1` manifest has no drillFile fields
- Package contains no drill CSV files
- Package installs and functions correctly

### Fix 13: Force update default packages on app start
**Discrepancy:** Existing users have old packages with drill content | **UC:** N/A | **Spec:** Migration requirement

Implement one-time force update of default packages to ensure all users get the clean package structure. This is acceptable since app is in testing phase.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` — `seedDefaultPacksIfNeeded()` method
- Consider migration logic to detect and replace old packages

**Verification:**
- On app update, old packages are replaced with new versions
- User progress is discarded (acceptable for testing phase)
- New packages install without drillFile references

---

## Verification Checklist

1. **Code cleanup**: No references to `SUB_DRILL`, `isDrillMode`, `drillProgressStore`, `drillFile` remain in codebase
2. **Build check**: `assembleDebug` completes without errors
3. **Tests pass**: `test` suite passes (no drill-specific test failures)
4. **New package**: `EN_WORD_ORDER_A1_DRILLS` package contains all drill content as one lesson
5. **Clean package**: `EN_WORD_ORDER_A1` package has no drillFile references
6. **Fire streak**: Max 3 fires for all practice types (TRANSLATION, VERB, VOCAB)
7. **Verb Practice**: Still works independently (no regression)
8. **VocabDrill**: Still works independently (no regression)
9. **UI cleanup**: No drill tiles, dialogs, or styling visible in app
10. **Data stores**: No `drill_progress_*.yaml` files created or backed up

---

## Scope Boundaries

**Do NOT touch:**
- **Verb Practice (VerbDrillScreen)** — This is an independent feature that remains
- **VocabDrill (VocabDrillScreen)** — Separate word training mode that stays
- **Daily Practice** — Unrelated to drill sub-mode
- **Boss Battle** — Unrelated feature
- **Mastery/Flower system** — Works for sentence training only (no drill changes needed)
- **WordMasteryStore** — Vocab drill uses separate store
- **TTS/ASR systems** — Unaffected by drill removal

**MIGRATE, don't delete:**
- Drill card content from `EN_WORD_ORDER_A1` → New `EN_WORD_ORDER_A1_DRILLS` package
- User progress is discarded (acceptable for testing phase)

**DELETE completely:**
- Drill sub-mode code (all files, methods, UI components)
- DrillProgressStore and all progress tracking
- Drill sub-mode from documentation (marked as deprecated/removed)

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain assembleDebug` — must pass with no errors
2. **Tests:** `java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain test` — must pass, no new failures
3. **Per-task verification:** Check each item from the Verification Checklist above
4. **Cross-task regression:** Verify that unrelated features still work:
   - Verb Practice launches and functions correctly
   - VocabDrill launches and functions correctly  
   - Daily Practice works for all 3 blocks
   - Boss battles work as expected
   - Fire streak shows max 3 fires
   - Package import/export works
5. **UC/AC spot-check:** Read affected UC entries from `22-use-case-registry.md`, confirm ACs hold for remaining modes
6. **Spec sync:** All drill references marked as deprecated/removed in spec files
7. **Package testing:** Install both new packages and verify functionality

---

## Git

One commit per fix or one combined. Use descriptive commit messages:

```
Remove drill sub-mode: enum values and data models

- Remove PracticeType.SUB_DRILL from enum
- Remove TrainingScreenMode.DRILL from enum
- Remove drillCards field from Lesson data class
- Remove drillFile field from LessonPackLesson
- Update fire streak max from 4 to 3

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

```
Remove drill sub-mode: DrillProgressStore and progress tracking

- Delete DrillProgressStore.kt entirely
- Remove drillProgressStore from TrainingViewModel
- Remove drill progress from backup/restore logic

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

```
Remove drill sub-mode: SessionRunner drill methods

- Delete all drill methods from SessionRunner
- Remove drill logic from buildSessionCards
- Remove drill checks from navigation flow

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

```
Remove drill sub-mode: UI components and dialogs

- Remove DrillStartDialog
- Remove drill tile from LessonRoadmapScreen
- Remove drill styling from TrainingScreen
- Remove DrillState from TrainingUiState

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

```
Migrate drill content to standalone package

- Create EN_WORD_ORDER_A1_DRILLS package with all drill content
- Clean EN_WORD_ORDER_A1 package (remove drillFile references)
- Force update default packages on app start

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Remove PracticeType.SUB_DRILL | | |
| | Fix 2: Remove DrillProgressStore | | |
| | Fix 3: Remove TrainingScreenMode.DRILL | | |
| | Fix 4: Remove drill methods from SessionRunner | | |
| | Fix 5: Remove DrillState from TrainingUiState | | |
| | Fix 6: Remove drillFile from LessonPackManifest | | |
| | Fix 7: Remove drillCards from Lesson | | |
| | Fix 8: Remove drill UI components | | |
| | Fix 9: Update fire streak logic | | |
| | Fix 10: Remove drill from bad sentence reporting | | |
| | Fix 11: Create EN_WORD_ORDER_A1_DRILLS package | | |
| | Fix 12: Clean EN_WORD_ORDER_A1 package | | |
| | Fix 13: Force update packages | | |
