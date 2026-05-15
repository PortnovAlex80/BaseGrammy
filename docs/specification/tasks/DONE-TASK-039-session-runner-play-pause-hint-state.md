# TASK-039: Fix SessionRunner Play/Pause/Hint State Transitions

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-session-runner-states (from main)
**Spec:** 08-training-viewmodel.md §5, scenario-01 §3, scenario-05 §4
**UC:** UC-06 (AC3, AC4), UC-08 (AC2)
**Scenario:** scenario-01-training-flow.md, scenario-05-input-modes.md
**User Journey:** user-journey-models.md Steps 4.2-4.5, Discrepancies BUG-NAV-002, BUG-NAV-003, BUG-NAV-005, STRUCT-002

---

## Problem

SessionRunner's play/pause/hint state machine has multiple flaws causing degraded user experience during card-by-card training:

1. **Play from HINT_SHOWN (BUG-NAV-003):** When user presses Play while hint is shown (3 wrong attempts), `togglePause()` calls `startSession()` which sets `sessionState = ACTIVE` and clears `inputText`, but does NOT clear `answerText` (hint text). The hint card persists visually while the session is ACTIVE and the Check button re-enables. User can submit a new answer while the old hint text is still displayed.

2. **No visual cue after auto-advance (BUG-NAV-002):** After a correct answer on a mid-card, `nextCardInternal()` advances to the next card instantly. There is no visual transition feedback — no brief highlight, no pulsing Next indicator. The card content changes silently, and in VOICE mode where auto-submit fires, the user may not realize they're on a new card.

3. **Next on last card (BUG-NAV-005):** Pressing Next on the last card uses `coerceAtMost(lastIndex)` — the index stays on the last card with no visual feedback that the session is complete. The only exit path is the Stop/Exit button, which is non-obvious.

4. **Dead AFTER_CHECK state (STRUCT-002):** `SessionState.AFTER_CHECK` is defined in Models.kt but never set anywhere in the codebase. Its existence adds confusion about valid state transitions.

Root cause: The state machine in SessionRunner was not fully implemented. `HINT_SHOWN` has incomplete cleanup, `AFTER_CHECK` was planned but never wired, and the Next button lacks boundary handling.

## Changes

### Fix 1: Clear hint state on Play from HINT_SHOWN
**Discrepancy:** BUG-NAV-003 | **UC:** UC-06 AC3 | **Spec:** 08-training-viewmodel.md §5.2

In `SessionRunner.startSession()`, after setting `sessionState = ACTIVE` and clearing `inputText`, also clear `answerText = ""` and `incorrectAttemptsForCard = 0`. This ensures the hint card disappears when the user presses Play to resume after seeing the hint.

**Files:** `feature/training/SessionRunner.kt` — `startSession()` method (~line 112)

**Verification:**
- Get 3 wrong answers on a card (hint appears)
- Press Play button
- Confirm hint text disappears, session becomes ACTIVE, input field is empty

### Fix 2: Remove dead AFTER_CHECK state
**Discrepancy:** STRUCT-002 | **UC:** N/A | **Spec:** Models.kt

Remove `AFTER_CHECK` from the `SessionState` enum in Models.kt. This state was never implemented and its presence causes confusion about valid state transitions. All current transitions go through ACTIVE → HINT_SHOWN → ACTIVE directly.

**Files:** `data/Models.kt` — `SessionState` enum (~line 107-112)

**Verification:** Build succeeds with no references to `AFTER_CHECK` anywhere in the codebase

### Fix 3: Add completion signal when Next pressed on last card
**Discrepancy:** BUG-NAV-005 | **UC:** UC-08 AC2 | **Spec:** scenario-01 §3.3

In `SessionRunner.nextCardInternal()`, when the calculated next index equals `lastIndex` and the session was already on the last card (i.e., user pressed Next on last card), either:
- Show a visual "Session complete" indicator, OR
- Auto-trigger the sub-lesson completion flow (same as answering last card correctly)

Preferred approach: auto-trigger sub-lesson completion to match the behavior when the user answers the last card correctly.

**Files:** `feature/training/SessionRunner.kt` — `nextCardInternal()` method (~line 404)

**Verification:** Navigate to last card in a sub-lesson, press Next, confirm session ends and navigates to LESSON

---

## Verification Checklist
1. After 3 wrong answers, hint appears. Press Play → hint disappears, session ACTIVE, input empty
2. Correct answer on mid-card → auto-advance to next card with clean state
3. `AFTER_CHECK` enum value removed, build succeeds
4. Grep for `AFTER_CHECK` returns zero results across entire codebase
5. Pressing Next on last card triggers session completion and navigation to LESSON
6. Normal training flow (start → answer cards → finish) still works end-to-end
7. Boss battle play/pause still works (boss uses same SessionRunner)

## Scope Boundaries
**Do NOT touch:**
- VerbDrillCardSessionProvider (uses CardSessionStateMachine, not SessionState enum)
- DailyPracticeCoordinator (separate state management)
- VocabDrillViewModel (no play/pause)
- GrammarMateApp.kt navigation logic

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify TrainingScreen flow, boss battle flow, settings pause/resume
5. **UC/AC spot-check:** read UC-06 AC3, AC4 from 22-use-case-registry.md, confirm ACs hold
6. **Spec sync:** update 08-training-viewmodel.md to remove AFTER_CHECK references

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Clear hint state on Play | | |
| | Fix 2: Remove dead AFTER_CHECK | | |
| | Fix 3: Completion on Next at last card | | |
