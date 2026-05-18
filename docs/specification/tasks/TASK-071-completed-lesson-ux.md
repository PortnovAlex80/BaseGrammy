# TASK-071: Completed Lesson UX — CompletionCard, Review Mode, Boss Threshold

**Status:** OPEN
**Created:** 2026-05-18
**Branch:** feature/completed-lesson-ux (from develop)
**Spec:** 19-screen-catalog.md#Screen3, 23-screen-elements.md#Section7, 08-training-viewmodel.md
**UC:** UC-89 (AC1–AC7), UC-90 (AC1–AC7), UC-91 (AC1–AC4)
**Scenario:** scenario-01-training-flow.md, scenario-11-navigation.md

---

## Problem

When a user completes all sub-lessons in a lesson, the LessonRoadmapScreen shows an empty grid with only Boss tiles and a "Continue Lesson" button that opens the last sub-lesson. There is no feedback that the lesson is complete, no way to review the lesson content with a chosen difficulty, and no navigation shortcut to the next lesson. Additionally, for lessons with fewer than 15 sub-lessons, the Boss tile is permanently locked because the unlock threshold is hardcoded at 15.

## Changes

### Fix 1: Boss unlock threshold — min(15, subLessonCount)
**UC:** UC-91 AC1–AC4 | **Spec:** 23-screen-elements.md#LR-09, 19-screen-catalog.md#Screen3-business-rules

Change boss unlock condition from `completedSubLessonCount >= 15` to `completedSubLessonCount >= min(15, subLessonCount)`. Update lock message to use dynamic threshold: "Пройдите минимум N упражнений" where N = min(15, subLessonCount).

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt` — bossUnlocked condition
- String resource for dynamic lock message (or format string)

**Verification:** Lesson with < 15 sub-lessons → boss unlocks after all sub-lessons completed. Lesson with > 15 → boss unlocks at 15. testMode still bypasses.

### Fix 2: CompletionCard on LessonRoadmapScreen
**UC:** UC-89 AC1, AC5, AC6, AC7 | **Spec:** 19-screen-catalog.md#Screen3, 23-screen-elements.md#LR-14

When `completedSubLessonCount >= subLessonCount`, replace the empty sub-lesson grid with a CompletionCard showing:
- Current flower state (BLOOM/WILTING/etc.) as visual indicator
- Message: "Все упражнения пройдены!"
- "Повторить" button (→ DifficultySelectionDialog)
- "Следующий урок" button (→ next lesson roadmap), hidden if no next lesson exists
- Boss/Drill tiles remain visible below the card
- Bottom button text changes from "Начать урок" to "Повторить урок"

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt` — new CompletionCard composable, conditional rendering
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — wiring for Review and NextLesson callbacks

**Verification:** Completed lesson shows CompletionCard. Non-completed lesson shows normal grid. No regression in sub-lesson tile rendering.

### Fix 3: DifficultySelectionDialog
**UC:** UC-90 AC1, AC3, AC7 | **Spec:** 19-screen-catalog.md#D18, 23-screen-elements.md#LR-15

New dialog with three difficulty rows:
- EASY: "Подсказки и Word Bank" — word bank + parenthetical hints visible
- MEDIUM: "Стандартный режим" — no word bank, parenthetical hints shown
- HARD: "Без подсказок" — no hints, no word bank, no tense labels
- Cancel button → close dialog, stay on LessonRoadmapScreen

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt` — DifficultySelectionDialog composable
- String resources for dialog text

**Verification:** Dialog shows 3 options. Cancel closes. Selecting option starts review session.

### Fix 4: Review session launch with HintLevel
**UC:** UC-90 AC2, AC4, AC5, AC6 | **Spec:** 08-training-viewmodel.md

New ViewModel method `startReview(hintLevel: HintLevel)` that:
- Loads ALL lesson cards (shuffled)
- Sets HintLevel to chosen difficulty
- Starts TRAINING session in ACTIVE state
- Mastery tracking works normally (VOICE/KEYBOARD count)

Wiring: "Повторить" button → DifficultySelectionDialog → onConfirm → vm.startReview(hintLevel) → navigate to TRAINING.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt` — startReview method
- `app/src/main/java/com/alexpo/grammermate/feature/training/CardProvider.kt` — build review card set
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — callback wiring

**Verification:** Review session loads all lesson cards. HintLevel affects UI (word bank, hints, tense labels). Mastery tracked.

### Fix 5: "Следующий урок" navigation
**UC:** UC-89 AC3, AC4 | **Spec:** 19-screen-catalog.md#Screen3, 23-screen-elements.md#LR-17

"Следующий урок" button navigates to the next lesson's LessonRoadmapScreen:
- Compute next lesson ID from lessons list order
- Call vm.selectLesson(nextLessonId) → screen = LESSON
- Hidden when current lesson is the last one

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — onNextLesson callback
- `app/src/main/java/com/alexpo/grammermate/ui/screens/LessonRoadmapScreen.kt` — button visibility

**Verification:** Tapping "Следующий урок" opens next lesson roadmap. Last lesson hides the button.

---

## Verification Checklist
1. Lesson with all sub-lessons completed → CompletionCard shown (UC-89 AC1)
2. "Повторить" button → DifficultySelectionDialog opens (UC-89 AC2)
3. "Следующий урок" → navigates to next lesson roadmap (UC-89 AC3)
4. Last lesson → "Следующий урок" button hidden (UC-89 AC4)
5. Boss tiles visible below CompletionCard (UC-89 AC5)
6. Bottom button text = "Повторить урок" when completed (UC-89 AC6)
7. DifficultySelectionDialog shows EASY/MEDIUM/HARD (UC-90 AC1)
8. Review session applies chosen HintLevel (UC-90 AC2, AC4, AC5)
9. All lesson cards loaded and shuffled in review (UC-90 AC3)
10. Mastery tracked in review session (UC-90 AC6)
11. Cancel closes dialog without navigation (UC-90 AC7)
12. Boss unlocks at min(15, subLessonCount) (UC-91 AC1)
13. Lesson with 10 sub-lessons → boss at 10 (UC-91 AC2)
14. testMode bypasses boss lock (UC-91 AC3)
15. Dynamic lock message shows correct threshold (UC-91 AC4)

## Scope Boundaries
**Do NOT touch:**
- TrainingViewModel.kt (use SessionRunner/CardProvider helpers instead)
- TrainingScreen.kt (review mode uses existing training UI)
- Boss battle logic (only unlock threshold changes)
- Daily Practice flow
- Verb Drill / Vocab Drill ViewModels
- Any data store files (no persistence changes needed)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from Verification Checklist above
4. **Cross-task regression:**
   - HomeScreen → lesson tile → LessonRoadmapScreen flow unchanged
   - Non-completed lesson → sub-lesson grid renders normally
   - Boss battle start/complete flow unchanged (only unlock threshold)
   - Back navigation from TrainingScreen → LessonRoadmapScreen unchanged
5. **UC/AC spot-check:** UC-01 (start sub-lesson), UC-05 (complete sub-lesson), UC-17 (start boss), UC-35 (navigate to training) — all still pass
6. **Spec sync:** fill trace-index.md Phase 8 line numbers after implementation

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Boss unlock threshold | | |
| | Fix 2: CompletionCard | | |
| | Fix 3: DifficultySelectionDialog | | |
| | Fix 4: Review session launch | | |
| | Fix 5: Next lesson navigation | | |
