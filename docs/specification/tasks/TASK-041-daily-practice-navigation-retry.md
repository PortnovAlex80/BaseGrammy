# TASK-041: Fix Daily Practice Navigation and Retry Behavior

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-daily-practice-nav-retry (from main)
**Spec:** 09-daily-practice.md §5, scenario-06 §3-6, 07-app-router.md §4
**UC:** UC-20 (AC2, AC5), UC-21 (AC1)
**Scenario:** scenario-06-daily-practice.md
**User Journey:** user-journey-models.md Steps 6.1-6.6, Discrepancies BUG-NAV-008, BUG-NAV-009, BUG-NAV-010, BUG-NAV-011

---

## Problem

Daily Practice has four navigation and retry issues:

1. **Silent failure on daily start (BUG-NAV-008):** When user taps Daily Practice, `vm.startDailyPractice(level)` runs on IO dispatcher. If the coroutine fails silently (exception swallowed), the loading dialog clears but no navigation occurs. User is stuck on HOME with no error feedback.

2. **No retry after wrong answer display (BUG-NAV-009):** In regular training, users get 3 attempts before the hint appears. In Daily Practice, after wrong answers lead to hint display, there is no retry mechanism — the user must advance. This is inconsistent with the regular training experience and the DailyPracticeSessionProvider which has partial retry logic.

3. **Completion sparkle race condition (BUG-NAV-010):** When daily session completes, `endSession()` sets `active=false, finishedToken=true`. The `onComplete` callback immediately calls `cancelDailySession()` which may trigger navigation to HOME before the "Daily practice complete!" sparkle animation renders. User sees abrupt HOME return.

4. **System back exits without confirmation (BUG-NAV-011):** The BackHandler for DAILY_PRACTICE route in GrammarMateApp.kt navigates directly to HOME. Spec 19.5 and 23-screen-elements.md DP-30 say an exit confirmation dialog should appear. The in-screen back arrow correctly shows the dialog, but the system back button bypasses it entirely.

Root cause: Daily Practice was built with its own state management (DailyPracticeCoordinator) that diverged from the training flow's retry conventions. The BackHandler was written as a simple navigate without matching the in-screen behavior.

## Changes

### Fix 1: Add error feedback on daily start failure
**Discrepancy:** BUG-NAV-008 | **UC:** UC-20 AC2 | **Spec:** 07-app-router.md §4.2

In GrammarMateApp.kt, wrap the `vm.startDailyPractice(level)` call in a try-catch. On failure: clear `isLoadingDaily`, show a Snackbar or Toast with "Failed to start daily practice. Please try again." Do NOT navigate to DAILY_PRACTICE on failure.

**Files:** `ui/GrammarMateApp.kt` — daily practice start block (~lines 235-249)

**Verification:**
- Force a failure condition (e.g., corrupt lesson data)
- Tap Daily Practice
- Confirm error message appears, loading clears, user stays on HOME

### Fix 2: Add retry capability in daily practice answer flow
**Discrepancy:** BUG-NAV-009 | **UC:** N/A (behavioral parity) | **Spec:** 09-daily-practice.md §5

This is a design decision — daily practice may intentionally have no retry to keep sessions short. If retry is desired, add attempt tracking to `DailyPracticeSessionProvider` matching the training flow's 3-attempt-then-hint pattern. If not desired, document this as intentional in spec.

**Decision needed from user:** Should daily practice have retry (3 attempts like training) or is single-attempt intentional?

**Files (if retry added):** `feature/daily/DailyPracticeSessionProvider.kt`, `ui/screens/DailyPracticeScreen.kt`

**Verification:** Wrong answer in daily practice → user can retry up to 3 times before hint appears

### Fix 3: Delay navigation for completion sparkle
**Discrepancy:** BUG-NAV-010 | **UC:** UC-21 AC1 | **Spec:** scenario-06 §6

Add a delay (e.g., 1500ms) after `endSession()` before the `onComplete` callback triggers navigation. During this delay, the completion sparkle and stats screen should be visible. Alternatively, move navigation from `onComplete` to a "Done" button on the completion screen.

Preferred approach: Move navigation to a "Done" button on the DailyPracticeCompletionScreen. Remove the auto-navigation from `onComplete`.

**Files:** `ui/screens/DailyPracticeScreen.kt` — completion flow (~lines 121-134), `ui/GrammarMateApp.kt` — onComplete callback (~line 542-544)

**Verification:** Complete all 3 blocks → completion screen shows with sparkle → user taps "Done" → navigates HOME

### Fix 4: Add exit confirmation for system back on DAILY_PRACTICE
**Discrepancy:** BUG-NAV-011 | **UC:** UC-20 AC5 | **Spec:** 23-screen-elements.md DP-30

Change the BackHandler for `Routes.DAILY_PRACTICE` in GrammarMateApp.kt from direct navigation to showing the exit confirmation dialog (same dialog shown by the in-screen back arrow).

**Files:** `ui/GrammarMateApp.kt` — BackHandler DAILY_PRACTICE block (~line 427-429)

**Verification:** Press system back during daily practice → exit confirmation dialog appears (not direct HOME navigation)

---

## Verification Checklist
1. Daily practice start failure shows error message, user stays on HOME
2. Daily practice start success navigates to DAILY_PRACTICE normally
3. Wrong answer retry behavior matches training flow (if implemented) or is documented as intentional
4. Completion sparkle is visible for full duration before navigation
5. System back during daily practice shows exit confirmation dialog
6. In-screen back arrow during daily practice shows exit confirmation dialog (unchanged)
7. All 3 blocks (Translate → Vocab → Verbs) complete successfully end-to-end

## Scope Boundaries
**Do NOT touch:**
- TrainingViewModel (not involved in daily practice)
- SessionRunner (not used by daily practice)
- VerbDrillScreen, VocabDrillScreen
- Boss battle flow

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from Verification Checklist
4. **Cross-task regression:** HOME navigation, DailyResumeDialog, block transitions, settings during daily practice
5. **UC/AC spot-check:** read UC-20 AC2, AC5 from 22-use-case-registry.md
6. **Spec sync:** update scenario-06 discrepancy status

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Error feedback on start failure | | |
| | Fix 2: Retry in daily practice (pending decision) | | |
| | Fix 3: Delay navigation for completion sparkle | | |
| | Fix 4: Exit confirmation for system back | | |
