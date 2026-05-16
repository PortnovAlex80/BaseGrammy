# TASK-056: Pomodoro Timer — Focused Training Sessions

**Status:** DONE
**Created:** 2026-05-16
**Branch:** feature/pomodoro-timer (from main)
**Spec:** 2026-05-16-pomodoro-timer-design, 08-training-viewmodel, 19-screen-catalog, 22-use-case-registry#Domain26, 23-screen-elements#PM
**UC:** UC-75 AC1-5, UC-76 AC1-4, UC-77 AC1-5, UC-78 AC1-5, UC-79 AC1-3, UC-80 AC1-4, UC-81 AC1-3, UC-82 AC1-3, UC-83 AC1-4
**Scenario:** scenario-01 (training flow, extended with Pomodoro overlay)
**Design:** docs/superpowers/specs/2026-05-16-pomodoro-timer-design.md

---

## Problem

GrammarMate lacks a structured, time-boxed practice mode. Users who want focused sessions have no way to set a time target, track session productivity, or receive structured feedback. The Pomodoro technique (short focused work intervals) aligns perfectly with the app's deliberate practice methodology — building automatic grammar skills through concentrated repetition.

The feature adds:
1. A tomato icon on HomeScreen that opens a timer selector
2. A countdown timer overlay during regular training sessions
3. Anki-style difficulty ratings after each card (Again/Hard/Good/Easy)
4. A session summary screen when the timer expires
5. Integration with the existing fire streak system

## Changes

### Fix 1: Add Pomodoro Data Model Types
**Discrepancy:** N/A (new feature) | **UC:** UC-75, UC-76, UC-77, UC-78 | **Spec:** 2026-05-16-pomodoro-design#3

Add to `data/Models.kt`:
- `PomodoroPreset` enum: QUICK(5), FOCUS(15), CLASSIC(20)
- `CardDifficultyRating` enum: AGAIN, HARD, GOOD, EASY
- `PomodoroSessionStats` data class: cardsShown, cardsCorrect, cardsIncorrect, difficultyRatings map, wordsPerMinute, durationMinutes, completedAtMs
- `PomodoroState` data class: isActive, isPaused, isComplete, selectedDurationMinutes, remainingSeconds, totalSeconds, stats, showRatingPrompt, showExitConfirm
- Add `pomodoro: PomodoroState = PomodoroState()` field to `TrainingUiState`

**Files:** `data/Models.kt`

**Verification:** Build compiles. No existing code broken (all new types have defaults).

### Fix 2: Create PomodoroSettingsStore
**Discrepancy:** N/A | **UC:** UC-75 AC3 | **Spec:** pomodoro-design#3

New file `data/PomodoroSettingsStore.kt`:
- Simple YAML store using AtomicFileWriter
- File: `grammarmate/pomodoro_settings.yaml`
- Methods: `load(): Int` (returns last duration, default 20), `save(durationMinutes: Int)`
- Thread-safe via ReentrantLock

**Files:** `data/PomodoroSettingsStore.kt` (new)

**Verification:** Unit test: save 15 → load returns 15. Load without prior save returns 20.

### Fix 3: Add Tomato Icon Asset
**Discrepancy:** N/A | **UC:** UC-75 AC1 | **Spec:** pomodoro-design#4.1, #6

- Download CC0 tomato SVG from SVG Repo
- Convert to Android Vector Drawable XML via Android Studio
- Save as `res/drawable/ic_tomato.xml`

**Files:** `res/drawable/ic_tomato.xml` (new)

**Verification:** Icon renders in Compose via `painterResource(R.drawable.ic_tomato)`.

### Fix 4: Create PomodoroHelper
**Discrepancy:** N/A | **UC:** UC-76, UC-77, UC-78, UC-79, UC-81 | **Spec:** pomodoro-design#5.1

New file `feature/pomodoro/PomodoroHelper.kt`:
- Plain class implementing domain logic, takes `TrainingStateAccess`
- Methods: `startPomodoro(duration)`, `pausePomodoro()`, `resumePomodoro()`, `cancelPomodoro()`, `completePomodoro()`, `recordDifficultyRating(rating)`, `tick()`
- Timer runs via coroutine in ViewModel scope, 1-second tick
- `tick()` decrements remainingSeconds, checks for completion at 0
- `completePomodoro()` calculates final stats from CardSessionState, sets pomodoro.isComplete
- `recordDifficultyRating()` updates PomodoroSessionStats.difficultyRatings map
- Handles pause on lifecycle stop (via separate lifecycle-aware calls from ViewModel)

**Files:** `feature/pomodoro/PomodoroHelper.kt` (new)

**Verification:** Unit test: startPomodoro(1) → tick 60 times → isComplete becomes true. pausePomodoro() stops tick decrement.

### Fix 5: Create PomodoroSelectorSheet
**Discrepancy:** N/A | **UC:** UC-75 AC1-3 | **Spec:** pomodoro-design#4.2

New file `ui/components/PomodoroSelectorSheet.kt`:
- ModalBottomSheet composable
- Props: `showSheet: Boolean`, `onDismiss: () -> Unit`, `onStart: (Int) -> Unit`, `lastDuration: Int`
- Content: tomato icon 48sp, "Pomodoro Training" title, 3 preset cards in Row, custom stepper, "Start" FilledButton
- Selected preset has primary container fill + border
- Custom stepper: +/- buttons, range 1-60, value in center

**Files:** `ui/components/PomodoroSelectorSheet.kt` (new)

**Verification:** Manual: sheet appears, presets selectable, custom stepper works, Start fires callback with duration.

### Fix 6: Create PomodoroTimerBanner
**Discrepancy:** N/A | **UC:** UC-76 AC1-4 | **Spec:** pomodoro-design#4.3

New file `ui/components/PomodoroTimerBanner.kt`:
- Card composable, 48dp height, warm tomato-tinted background
- Layout: [🍅 icon + MM:SS countdown] [📊 N cards] [✅ N% + ⏸/▶ button]
- MM:SS uses `fontFeatureSettings = "tnum"` for stable digit width
- Pause/play button toggles based on isPaused state

**Files:** `ui/components/PomodoroTimerBanner.kt` (new)

**Verification:** Manual: banner shows correct countdown, stats update, pause/play works.

### Fix 7: Create DifficultyRatingRow
**Discrepancy:** N/A | **UC:** UC-77 AC1-5 | **Spec:** pomodoro-design#4.4

New file `ui/components/DifficultyRatingRow.kt`:
- Row of 4 FilterChip composables
- Colors: AGAIN=red, HARD=amber, GOOD=green, EASY=blue
- Auto-selects GOOD after 3 seconds via LaunchedEffect with delay(3000)
- Callback: `onRatingSelected: (CardDifficultyRating) -> Unit`
- On selection: records rating, triggers next card navigation

**Files:** `ui/components/DifficultyRatingRow.kt` (new)

**Verification:** Manual: chips appear after card result, tapping advances, auto-select after 3s works.

### Fix 8: Create PomodoroSummaryScreen
**Discrepancy:** N/A | **UC:** UC-78 AC1-5, UC-82 AC1-3, UC-83 AC1-4 | **Spec:** pomodoro-design#4.5

New file `ui/components/PomodoroSummaryScreen.kt`:
- Full-screen overlay composable replacing training content
- Sections: circular progress ring (Canvas, 120dp), stats grid (2x2), difficulty breakdown bars, fire indicator, "Done" button
- "Early completion!" label when session ended before timer
- "Done" button callback: `onDone: () -> Unit`

**Files:** `ui/components/PomodoroSummaryScreen.kt` (new)

**Verification:** Manual: summary shows correct stats, circular ring reflects time, bars show difficulty distribution.

### Fix 9: Update HomeScreen — Add Tomato Icon
**Discrepancy:** N/A | **UC:** UC-75 AC1 | **Spec:** pomodoro-design#4.1, 23-screen-elements#PM-01

Modify `ui/screens/HomeScreen.kt`:
- Add `showPomodoroSheet: Boolean` local state
- Add tomato IconButton in header Row between language selector and settings gear
- Render PomodoroSelectorSheet when showPomodoroSheet is true
- Add `onStartPomodoro: (Int) -> Unit = {}` parameter to HomeScreen composable
- When "Start" callback fires: set showPomodoroSheet = false, call onStartPomodoro(duration)

**Files:** `ui/screens/HomeScreen.kt`

**Verification:** Tomato icon visible in header, tapping opens sheet, Start triggers navigation to training.

### Fix 10: Update TrainingViewModel — Pomodoro Integration
**Discrepancy:** N/A | **UC:** UC-75, UC-76, UC-78, UC-79, UC-81 | **Spec:** pomodoro-design#5.3

Modify `ui/TrainingViewModel.kt`:
- Create PomodoroHelper instance in init
- Add `startPomodoro(durationMinutes: Int)` — starts timer + starts/resumes training session
- Add `pausePomodoro()` / `resumePomodoro()` — delegates to helper
- Add `cancelPomodoro()` — resets PomodoroState, stops timer
- Add `rateCardDifficulty(rating: CardDifficultyRating)` — delegates to helper
- Add `completePomodoroSession()` — called by helper on timer expire, calculates stats, pauses session
- Start 1-second timer coroutine when Pomodoro active, cancel on complete/cancel
- On lifecycle onStop: pause Pomodoro if active
- On lifecycle onStart: resume Pomodoro if was active before stop
- Extend CardSessionContract (via TrainingCardSessionProvider) with optional difficulty rating callbacks when Pomodoro is active

**Files:** `ui/TrainingViewModel.kt`, `ui/TrainingCardSessionProvider.kt`

**Verification:** Timer counts down, pause/resume works, completion triggers summary, lifecycle events handled.

### Fix 11: Update TrainingScreen — Timer Banner and Summary
**Discrepancy:** N/A | **UC:** UC-76, UC-78, UC-80 | **Spec:** pomodoro-design#5.4

Modify `ui/screens/TrainingScreen.kt`:
- When `state.pomodoro.isActive && !state.pomodoro.isComplete`: render PomodoroTimerBanner above TrainingCardSession
- When `state.pomodoro.isComplete`: render PomodoroSummaryScreen instead of TrainingCardSession
- When `state.pomodoro.isActive && state.pomodoro.showRatingPrompt`: render DifficultyRatingRow in result area
- Back handler: if Pomodoro active, show exit confirmation dialog instead of exiting

**Files:** `ui/screens/TrainingScreen.kt`

**Verification:** Banner visible during Pomodoro, summary appears on completion, difficulty row shows after each card.

### Fix 12: Update GrammarMateApp — Wire Callbacks
**Discrepancy:** N/A | **UC:** UC-75 AC4 | **Spec:** pomodoro-design#5.4

Modify `ui/GrammarMateApp.kt`:
- Add `onStartPomodoro` callback to HomeScreen call site
- Callback: `vm.startPomodoro(durationMinutes)` + navigate to training route
- Ensure Pomodoro state is properly passed through state flow

**Files:** `ui/GrammarMateApp.kt`

**Verification:** Tapping Start in Pomodoro sheet navigates to training with timer running.

---

## Verification Checklist
1. HomeScreen shows tomato icon between language selector and settings gear
2. Tapping tomato opens bottom sheet with 3 presets + custom option
3. Starting Pomodoro navigates to training with visible timer countdown
4. Timer decrements every second while training is active
5. Difficulty rating chips appear after each card result during Pomodoro
6. Auto-select "Good" after 3 seconds without user input
7. Pause button pauses both timer and training
8. Timer expiry pauses session and shows summary screen
9. Summary displays: time ring, cards shown, correct rate, WPM, difficulty breakdown
10. "Done" button on summary returns to HomeScreen
11. Back press during Pomodoro shows confirmation dialog
12. App backgrounding pauses timer, foregrounding resumes it
13. Fire streak records correctly when Pomodoro session qualifies
14. Existing training without Pomodoro works unchanged (no regression)

## Scope Boundaries
**Do NOT touch:**
- Daily Practice session flow or screens
- Verb Drill or Vocab Drill ViewModels
- Existing CardSessionContract interface (only extend, don't modify existing methods)
- StreakStore or PracticeType enum (reuse existing TRANSLATION type)
- SpacedRepetitionConfig or interval ladder
- FlowerCalculator or flower state logic
- TTS/ASR engine code
- BackupManager
- Any screen not listed in the Changes section

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `java -cp "gradle/wrapper/..." org.gradle.wrapper.GradleWrapperMain assembleDebug` — must pass
2. **Tests:** `java -cp "gradle/wrapper/..." org.gradle.wrapper.GradleWrapperMain test` — no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:**
   - Regular training (non-Pomodoro) works unchanged
   - HomeScreen avatar/language/settings still work
   - Fire streak recording on normal sessions still works
   - TrainingCardSession renders correctly when Pomodoro is inactive
   - Boss battle flow unaffected
   - Daily practice flow unaffected
5. **UC/AC spot-check:** verify UC-75 through UC-83 ACs hold against implementation
6. **Spec sync:** if code diverged from spec, update spec + CHANGELOG

## Git
One commit per wave or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Data model types | | |
| | Fix 2: PomodoroSettingsStore | | |
| | Fix 3: Tomato icon asset | | |
| | Fix 4: PomodoroHelper | | |
| | Fix 5: PomodoroSelectorSheet | | |
| | Fix 6: PomodoroTimerBanner | | |
| | Fix 7: DifficultyRatingRow | | |
| | Fix 8: PomodoroSummaryScreen | | |
| | Fix 9: HomeScreen tomato icon | | |
| | Fix 10: TrainingViewModel integration | | |
| | Fix 11: TrainingScreen rendering | | |
| | Fix 12: GrammarMateApp wiring | | |
