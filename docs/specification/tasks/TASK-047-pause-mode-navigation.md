# TASK-047: Navigation Arrows Always Work, But Trigger PAUSE Mode

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/pause-mode-navigation (from main)
**Spec:** 08-training-viewmodel.md §5, scenario-01 §3, 12-training-card-session.md
**UC:** UC-06 (AC2, AC4)
**Scenario:** scenario-01-training-flow.md, scenario-05-input-modes.md
**User Journey:** user-journey-models.md Step 4.5
**Note:** Partially reverses TASK-044 (Next button guard). Next is re-enabled but triggers PAUSE.

---

## Problem

After TASK-044, the Next button is disabled during ACTIVE session to prevent card skipping. However, the user wants to browse cards freely (forward/back arrows) WITHOUT triggering the timer or voice recognition. The correct UX is:

- **Navigation arrows (← →) = PREVIEW mode.** Always enabled. Pressing any arrow forces PAUSE state. User browses cards in paused mode, seeing prompts without timer pressure.
- **Play button = WORK mode.** Pressing Play activates ACTIVE state: timer runs, voice recognition launches, answers are accepted.
- **The session alternates between PAUSE (browsing) and ACTIVE (answering).**

This gives the user control: browse cards at their pace, then press Play to engage with a specific card.

## Changes

### Fix 1: Re-enable Next button, add PAUSE trigger
**Discrepancy:** Reverses TASK-044 Fix 1 | **UC:** UC-06 AC4 | **Spec:** scenario-01 §3.3

In `ui/screens/TrainingScreen.kt`, revert the Next button disabled guard from TASK-044. The Next button should be enabled whenever `hasCards`. However, pressing Next during ACTIVE session should:
1. Trigger `pauseSession()` — set sessionState to PAUSED, pause timer
2. Then advance to next card via `nextCard()`
3. Session stays PAUSED until user presses Play

The same applies to Previous (←) button if it exists.

**Files:** `ui/screens/TrainingScreen.kt` — NavigationRow composable

**Verification:** During ACTIVE session, press Next → timer pauses, card advances, Play icon shown

### Fix 2: Update SessionRunner to support navigate-and-pause
**Discrepancy:** N/A | **UC:** UC-06 AC2 | **Spec:** 08-training-viewmodel.md §5

Add a method to SessionRunner (or modify existing nextCard/prevCard) that:
1. Pauses the session first (stops timer, clears voice state)
2. Advances/retreats to the target card
3. Leaves the session in PAUSED state

The existing `nextCard()` should still work as before (resume on advance). Add `navigateNext()` and `navigatePrev()` that pause-first.

**Files:** `feature/training/SessionRunner.kt`, `TrainingViewModel.kt`

**Verification:** In ACTIVE session, call navigateNext → session PAUSED, new card loaded, Play icon visible

### Fix 3: Apply to VerbDrill and DailyPractice (DP-01)
**Discrepancy:** N/A | **UC:** UC-25, UC-20 | **Spec:** DP-01

Per Design Principle DP-01, the same navigate-and-pause behavior applies to VerbDrill and DailyPractice card blocks. Update:
- VerbDrill navigation controls (custom controls or DefaultNavigationControls)
- DailyPracticeSessionProvider navigation

**Files:** `ui/screens/VerbDrillScreen.kt`, `feature/daily/DailyPracticeSessionProvider.kt`

**Verification:** In VerbDrill ACTIVE session, press Next → PAUSED, new card, Play visible

---

## Verification Checklist
1. Next button enabled in ALL states (ACTIVE, PAUSED, HINT_SHOWN)
2. Pressing Next during ACTIVE → session PAUSES, card advances, Play icon shown
3. Pressing Play after navigate-and-pause → session resumes ACTIVE, timer/voice starts
4. Regular answer submission still works: correct answer → auto-advance → stays ACTIVE
5. VerbDrill Next has same navigate-and-pause behavior
6. DailyPractice Next has same navigate-and-pause behavior
7. Boss battle: Next still works (verify boss progress tracking)
8. Prev button (if exists) has same behavior: always enabled, triggers PAUSE

## Scope Boundaries
**Do NOT touch:**
- VocabDrillScreen (different interaction model)
- GrammarMateApp.kt navigation
- Mastery/flower calculation
- Streak logic (separate TASK-046)

## Regression Plan
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** each item above
4. **Cross-task regression:** submit answer flow, boss battle, daily practice blocks, voice mode auto-launch
5. **UC/AC spot-check:** UC-06 AC2, AC4

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Re-enable Next, add PAUSE trigger | | |
| | Fix 2: Navigate-and-pause in SessionRunner | | |
| | Fix 3: Apply to VerbDrill + DailyPractice | | |
