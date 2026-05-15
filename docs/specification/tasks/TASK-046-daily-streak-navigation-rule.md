# TASK-046: Daily Streak Only Counts When Lesson Completed Via Correct Answers

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/streak-navigation-rule (from main)
**Spec:** 08-training-viewmodel.md §6, 03-algorithms-and-calculators.md
**UC:** UC-02 (AC1, AC2)
**Scenario:** scenario-03-mastery-flower.md, scenario-04-spaced-repetition.md
**User Journey:** user-journey-models.md Journey 4, Design Principle DP-01

---

## Problem

Daily streak (number of consecutive days with practice) should only increment when the user actually completes a lesson by answering cards correctly. Currently, if a user navigates through cards using forward/back arrows without submitting correct answers, the streak may still increment. This inflates the streak metric and removes the incentive to actually practice.

The correct behavior: a "practice day" only counts when the user has completed a lesson/session where cards were answered via correct submission (Check button or auto-submit in VOICE mode). Navigation via arrows alone does NOT constitute practice.

Cards flagged as "bad sentence" are excluded from the completion check — they don't need to be answered correctly for the lesson to count.

## Changes

### Fix 1: Define "practice day" criteria
**Discrepancy:** N/A (new rule) | **UC:** UC-02 AC1 | **Spec:** 03-algorithms-and-calculators.md

Define what constitutes a completed practice session:
- At least N cards answered correctly via submit (not navigation) in a single session
- Bad sentence cards excluded from the count
- Navigation-only progression does NOT count

The streak store should only be updated when these criteria are met, not on every session start or card navigation.

**Files:** `data/StreakStore.kt` or equivalent, `feature/progress/ProgressTracker.kt`

**Verification:** Navigate through all cards via arrows only, exit session → daily streak should NOT increment

### Fix 2: Guard streak update with completion check
**Discrepancy:** N/A | **UC:** UC-02 AC2 | **Spec:** 08-training-viewmodel.md §6

Find where the daily streak is updated (likely in `finishSession()`, `saveProgress()`, or `updateStreak()`). Add a check:
- Count how many cards in the session were answered correctly (via submitAnswer, not nextCard)
- If count > 0 (at least one card answered correctly) → allow streak update
- If count == 0 (user only navigated) → skip streak update

**Files:** `TrainingViewModel.kt` — session finish methods, `feature/progress/ProgressTracker.kt`

**Verification:** Complete a lesson normally (answer cards correctly) → streak increments

---

## Verification Checklist
1. Navigate through lesson via arrows only, exit → streak does NOT increment
2. Complete lesson with correct answers → streak DOES increment
3. Partial completion (some correct, some skipped) → streak increments (at least 1 correct)
4. All cards flagged as bad → streak still increments if session was completed
5. Daily Practice completion → streak increments (same rules apply)
6. Boss battle completion → verify streak behavior matches regular training
7. Streak counter on HomeScreen updates correctly

## Scope Boundaries
**Do NOT touch:**
- Navigation logic (arrows, buttons)
- Card session state management
- Mastery calculation
- Flower state calculation
- GrammarMateApp.kt

## Regression Plan
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** each item from Verification Checklist
4. **Cross-task regression:** session finish flow, flower states, SRS intervals, HomeScreen display
5. **UC/AC spot-check:** UC-02 AC1, AC2

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Define practice day criteria | | |
| | Fix 2: Guard streak update | | |
