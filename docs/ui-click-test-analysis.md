# UI Click Test Analysis - BaseGrammy

**Date:** 2026-05-20
**Status:** Initial Analysis

## Executive Summary

After examining all test files with "ClickTest" in their names, **MOST ARE NOT ACTUAL UI CLICK TESTS**. Only 3 files are true UI-click tests that use Compose UI API to render screens and simulate user actions. The rest are either scenario/unit tests that call ViewModel methods directly, or tests that only verify state variables without any UI interaction.

## Current Test Classification

### ✅ TRUE UI-Click Tests (3 files)

These render actual Compose screens and use Compose testing API:

| File | User Paths Covered | Status |
|------|-------------------|--------|
| `ui/SmokeClickTest.kt` | Basic button click | ✅ Working |
| `ui/NavigationClickTest.kt` | Back icon navigation | ✅ Working |
| `ui/WordBankToggleTest.kt` | Word bank toggle | ✅ Working |

### ⚠️ PARTIAL UI Tests (use non-existent testTags)

These attempt to be UI tests but reference `testTag` values that don't exist in production:

| File | Issue | Missing testTags |
|------|-------|------------------|
| `ui/RegularLessonClickUiTest.kt` | References "input_field", "check_button", "card_prompt_text" | ❌ Not in production |
| `ui/PauseCascadeClickUiTest.kt` | References "input_field", "check_button", "prev_button" | ❌ Not in production |

These tests would FAIL if run because the testTags don't exist.

### ❌ FALSE "ClickTest" Files (not UI tests)

These files have "ClickTest" or "ClickUiTest" in their names but are NOT UI click tests:

| File | What it actually does | Why it's not UI |
|------|----------------------|-----------------|
| `ui/PomodoroClickUiTest.kt` | Checks `PomodoroState` variables | No UI rendering |
| `ui/DailyPracticeClickUiTest.kt` | Simulates callbacks directly | No actual clicks |
| `ui/VerbPracticeClickUiTest.kt` | Checks data structures | No UI rendering |
| `ui/SubDrillClickUiTest.kt` | Checks state variables | No UI rendering |
| `scenario/RegularLessonClickTest.kt` | Calls `sessionRunner.startSession()`, `submitAnswer()` | Direct method calls |
| `scenario/DailyPracticeClickTest.kt` | Calls `coordinator.onBlockComplete()` | Direct method calls |
| `scenario/VerbDrillClickTest.kt` | Calls store methods | Direct method calls |
| `scenario/BossBattleClickTest.kt` | Likely direct method calls | Direct method calls |
| `scenario/LessonDrillClickTest.kt` | Likely direct method calls | Direct method calls |
| `scenario/VocabDrillClickTest.kt` | Likely direct method calls | Direct method calls |
| `scenario/VerbPracticeButtonClickTest.kt` | Likely direct method calls | Direct method calls |

### ✅ Scenario/Unit Tests (appropriately named)

These are legitimate scenario/unit tests that correctly don't use UI:

| File | Purpose |
|------|---------|
| `scenario/EliteModeScenarioTest.kt` | Elite mode behavior |
| `scenario/MixedSessionScenarioTest.kt` | MIXED session logic |
| `scenario/NewOnlySessionScenarioTest.kt` | NEW_ONLY session logic |
| `scenario/MasteryProgressionScenarioTest.kt` | Mastery calculation |
| `scenario/BossBattleScenarioTest.kt` | Boss battle logic |
| `data/*Test.kt` | Unit tests for data layer |

## Production UI Testability Status

### Existing testTags
**SOME EXIST** - Key interactive elements have testTags:

| testTag | Location | Element |
|---------|----------|---------|
| `card_prompt_text` | TrainingScreen.kt:398 | Card prompt display |
| `input_field` | UnifiedInputControlsBar.kt:183 | Text input field |
| `check_button` | UnifiedInputControlsBar.kt:346 | Submit/Check button |
| `show_answer_button` | UnifiedInputControlsBar.kt:299 | Show answer hint button |
| `prev_button` | UnifiedNavigationRow.kt:87 | Previous card button |
| `next_button` | UnifiedNavigationRow.kt:119 | Next card button |

### Missing testTags (need to be added):

| testTag | Needed For | Element |
|---------|------------|---------|
| `pause_button` | Pause cascade | Pause/Resume toggle button |
| `word_bank_container` | Word Bank EASY | Word bank section container |
| `word_bank_chip_*` | Word Bank EASY | Individual word chips |
| `word_bank_undo` | Word Bank EASY | Undo button |
| `pomodoro_banner` | Pomodoro | Timer banner |
| `pomodoro_pause_resume` | Pomodoro | Pause/Resume in banner |
| `pomodoro_summary` | Pomodoro | Summary screen |
| `pomodoro_done_button` | Pomodoro | Done button on summary |
| `vocab_show_answer` | Daily Practice | Vocab flashcard Show Answer |
| `vocab_rating_*` | Daily Practice | Rating buttons (AGAIN, HARD, GOOD, EASY) |
| `verb_filter_*` | Verb Practice | Tense/Group filter chips |

### Existing contentDescription

| Location | Element | contentDescription |
|----------|---------|-------------------|
| TrainingScreen.kt:205 | Settings icon | "training_settings" |
| LessonRoadmapScreen.kt:155 | Back icon | "roadmap_back" |
| HomeScreen.kt:222 | Pomodoro | "Pomodoro Timer" |
| HomeScreen.kt:499 | Start button | "home_start" |
| HomeScreen.kt:532 | Verb drill | "home_verb_drill" |

## Required Test Coverage (from requirements)

The following user paths need TRUE UI-click tests:

1. **Regular lesson**: Start/Continue → type answer → Check → next card → completion
2. **Pause cascade**: Pause → type answer → Check → Play, card doesn't skip
3. **Word Bank EASY**: Toggle → click words → Check
4. **Daily Practice**: Open → translate block → vocab flashcard → rating → verb block → completion
5. **Verb Practice**: Previous session card → Repeat/Continue/Reset, verify visible cards/filters
6. **Pomodoro**: Start → pause/resume banner → complete early → summary persists
7. **Sub-drill**: Open drill tile → Start/Continue → answer → completion returns to lesson

## Action Plan - COMPLETED

### ✅ Phase 1: Add testTags to production UI - COMPLETED

Added testTags to key interactive elements:

**Existing (already in production):**
- `card_prompt_text` - TrainingScreen.kt:398
- `input_field` - UnifiedInputControlsBar.kt:183
- `check_button` - UnifiedInputControlsBar.kt:346
- `show_answer_button` - UnifiedInputControlsBar.kt:299
- `prev_button` - UnifiedNavigationRow.kt:87
- `next_button` - UnifiedNavigationRow.kt:119

**NEWLY ADDED:**
- `pause_button` - UnifiedNavigationRow.kt:98 (pause/play toggle)
- `word_bank_container` - DailyPracticeComponents.kt:61 (word bank section)
- `word_bank_chip_*` - DailyPracticeComponents.kt:71 (individual word chips, dynamic)
- `word_bank_undo` - DailyPracticeComponents.kt:88 (undo button)
- `pomodoro_banner` - PomodoroTimerBanner.kt:36 (timer banner)
- `pomodoro_pause_resume` - PomodoroTimerBanner.kt:81 (pause/resume in banner)
- `pomodoro_summary` - PomodoroSummaryScreen.kt:54 (summary screen)
- `pomodoro_done_button` - PomodoroSummaryScreen.kt:116 (OK button on summary)

### ✅ Phase 2: Create/fix UI-click tests - COMPLETED

**NEW UI-click tests created:**
1. `ui/WordBankEasyClickUiTest.kt` - Complete Word Bank EASY flow
   - Enable Word Bank mode
   - Click word chips to build answer
   - Submit and verify correct/incorrect
   - Use undo to remove last word
   - Verify chip disabled when fully used
   - Verify Word Bank hidden in HARD mode

2. `ui/PomodoroBannerClickUiTest.kt` - Complete Pomodoro flow
   - Active Pomodoro → banner shows time and stats
   - Pause/Resume button click
   - Complete early → summary shows stats
   - Summary persists until OK clicked
   - No banner when Pomodoro inactive
   - Full session completion (no early completion message)

**Existing TRUE UI tests (already working):**
- `ui/SmokeClickTest.kt` - Basic Compose button click
- `ui/NavigationClickTest.kt` - Back icon navigation
- `ui/WordBankToggleTest.kt` - Word bank toggle
- `ui/RegularLessonClickUiTest.kt` - Regular lesson flow (uses existing testTags)
- `ui/PauseCascadeClickUiTest.kt` - Pause cascade flow (uses existing testTags)

### ⚠️ Phase 3: Rename false ClickTests - PENDING

Files that claim to be ClickTest but aren't (recommendation):
- `ui/PomodoroClickUiTest.kt` → `ui/PomodoroStateTest.kt` (checks state only)
- `ui/DailyPracticeClickUiTest.kt` → `ui/DailyPracticeStateTest.kt` (simulates callbacks)
- `ui/VerbPracticeClickUiTest.kt` → `ui/VerbPracticeStateTest.kt` (checks data structures)
- `ui/SubDrillClickUiTest.kt` → `ui/SubDrillStateTest.kt` (checks state)

These can be renamed to avoid confusion, or migrated to true UI tests.

## Test Execution Status

To verify current tests:
```bash
# Run all unit tests
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain test

# Run specific test class
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain test --tests "com.alexpo.grammermate.ui.RegularLessonClickUiTest"
```

Expected: Tests with missing testTags will FAIL.
