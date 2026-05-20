# TASK-076: Verb Drill Session Persistence Lifecycle

**Status:** OPEN
**Created:** 2026-05-20
**Branch:** feature/verb-drill-session-persistence (from develop)
**Spec:** 10-verb-drill.md#10.6.2 (VD-50), 10-verb-drill.md#10.6.10 (Completion Screen), 10-verb-drill.md#10.6.13 (Exit Navigation)
**UC:** US-10.20 (UC-74), UC-64
**Scenario:** scenario-07-verb-drill.md
**Related:** TASK-075 (Fix 3/5 overlap — resume loads next cards)

---

## Problem

`saveLastSessionState()` in VerbDrillViewModel is **dead code**. It's called from `exitSession()` (VM:613), but `exitSession()` is only invoked through `verbDrillExit` lambda (GrammarMateApp:494) — the Back button on the VerbDrillScreen selection screen where `session == null`. Nobody calls `verbDrillVm.exitSession()` when the user exits from TrainingScreen during or after a card session.

**Root cause chain:**
1. User completes 10 cards in TrainingScreen → "Отлично" screen shows
2. GrammarMateApp calls `vm.exitVerbDrillSession()` (TrainingViewModel:720) — this clears the card session but does NOT call `verbDrillVm.exitSession()`
3. `verbDrillVm.saveLastSessionState()` never fires → `verb_drill_last_session.yaml` never written
4. Next entry to VerbDrillScreen → `loadLastSession()` returns null → `lastSessionContext` stays null → SessionCard never shows

**Additional bug:** `lastSessionContext` in VerbDrillUiState is only SET (VM:200), never CLEARED back to null. No `else` branch in `checkForLastSessionAndShowDialog()`. Once set, it shows stale data even after the session file is deleted.

## Changes

### Fix 1: Save last session state when card session ends
**Discrepancy:** Code vs spec 10.6.2 | **UC:** UC-74 AC1, AC6 | **Spec:** 10-verb-drill.md#10.6.2

Call `verbDrillVm.saveLastSessionState()` from GrammarMateApp when the Verb Drill card session completes or is exited. The VerbDrillViewModel must be accessible from the TrainingScreen context.

**Behavior:**
- Session completes (all 10 cards) → "Отлично" screen → save filters + todayShownCardIds to disk
- User presses Back mid-session → save filters + todayShownCardIds to disk
- User presses "Выход" from completion screen → save then navigate HOME
- User presses "Ещё" from completion screen → save then load next batch (same filters)

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — wire verbDrillVm into TrainingScreen composable, call save on session end
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` — extract `saveLastSessionState()` to public or add a public bridge method

**Verification:** After completing a 10-card session, file `drills/{packId}/verb_drill_last_session.yaml` exists on disk with correct filters and todayShownCardIds.

### Fix 2: "Ещё" button loads next batch in-place
**Discrepancy:** Code vs spec 10.6.10 | **UC:** UC-64 | **Spec:** 10-verb-drill.md#10.6.10

Current behavior: `onVerbDrillMore` (GrammarMateApp:454-458) calls `vm.exitVerbDrillSession()` then `onNavigate(Routes.VERB_DRILL)` — navigates back to selection screen.

Required behavior: "Ещё" should load the next 10 cards with same filters WITHOUT leaving TrainingScreen. The VerbDrillViewModel should prepare the next batch, then update the card session in TrainingViewModel.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — change `onVerbDrillMore` lambda
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` — `nextBatch()` already calls `startSession()`, needs to return cards
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` — add method to replace cards in active card session

**Verification:** Press "Ещё" → TrainingScreen stays, new 10 cards appear with same tense/group filters. Progress bar updates.

### Fix 3: "Выход" button navigates to HOME with state saved
**Discrepancy:** Code vs spec 10.6.13 | **UC:** UC-64 | **Spec:** 10-verb-drill.md#10.6.13

Current behavior: Exit from completion screen already goes to HOME (GrammarMateApp:438-440 when `!hasActiveCard`). But state is NOT saved because `verbDrillVm.exitSession()` is not called.

Required behavior: Save state to disk, then navigate to HOME. On next entry, SessionCard shows with "Continue" option.

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — save state before navigating HOME

**Verification:** Complete session → "Выход" → HOME → re-enter Verb Drill → SessionCard visible with filters from last session.

### Fix 4: Clear lastSessionContext when no session exists
**Discrepancy:** Code bug | **UC:** UC-74 AC3 | **Spec:** 10-verb-drill.md#10.6.2

Add `else` branch to `checkForLastSessionAndShowDialog()`:

```kotlin
if (lastSession != null) {
    _uiState.update { it.copy(lastSessionContext = lastSession) }
} else {
    _uiState.update { it.copy(lastSessionContext = null) }  // CLEAR stale data
}
```

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` — `checkForLastSessionAndShowDialog()` method

**Verification:** Complete session → exit → re-enter → start fresh from selection screen → SessionCard NOT visible.

### Fix 5: Click tests for session persistence scenarios
**Discrepancy:** No test coverage | **UC:** UC-74 all ACs | **Spec:** 10-verb-drill.md#10.6.2

Write click-based integration tests in `VerbDrillClickTest.kt` (NOT mock-based):

1. **complete_and_more**: Complete 10 cards → "Ещё" → verify next 10 cards load with same filters
2. **complete_and_exit_resume**: Complete 10 cards → "Выход" → re-enter → SessionCard shows → "Continue" → verify filters restored
3. **mid_session_back**: Complete 3 cards → Back → re-enter → SessionCard shows → "Continue" → verify cards exclude already shown
4. **start_fresh_clears_session**: After resume → "Start Fresh" → verify SessionCard gone, filters cleared

**Files:**
- `app/src/test/java/com/alexpo/grammermate/scenario/VerbDrillClickTest.kt` — add test methods

**Verification:** All 4 click tests pass.

---

## Verification Checklist
1. File `drills/{packId}/verb_drill_last_session.yaml` written after completing a 10-card session
2. File contains: selectedTense, selectedGroup, sortByFrequency, todayShownCardIds
3. "Ещё" button loads next batch in TrainingScreen without navigating away
4. "Выход" button navigates to HOME with state saved
5. Re-entering VerbDrill from HOME → SessionCard shows with last session info
6. "Continue" in SessionCard → restores filters, loads next cards excluding todayShownCardIds
7. `lastSessionContext` cleared to null when no last session file exists
8. "Start Fresh" clears SessionCard and resets filters
9. All 4 click tests pass
10. `assembleDebug` passes
11. Existing `test --tests "*VerbDrill*"` passes

## Scope Boundaries
**Do NOT touch:**
- Daily Practice VERBS block (separate mechanism, different ViewModel)
- TrainingViewModel core logic (only add card session replacement method)
- VerbDrillStore persistence layer (already works — file writes/reads are correct)
- VocabDrill (completely separate)
- Theme, strings, resources (no UI text changes)
- VerbDrillCsvParser (no content changes)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test --tests "*VerbDrill*"` — must pass
3. **Click tests:** New test methods in VerbDrillClickTest.kt — all pass
4. **Manual:**
   - Home → Verb Practice → select filters → complete 10 cards → "Ещё" → verify same filters
   - Home → Verb Practice → complete 10 cards → "Выход" → Home → Verb Practice → SessionCard visible
   - SessionCard "Continue" → filters restored, new cards
   - SessionCard "Start Fresh" → filters cleared, no SessionCard
5. **Cross-task regression:** Verify Daily Practice VERBS block still works
6. **UC/AC spot-check:** UC-74 all ACs, UC-64 exit to HOME
7. **Spec sync:** Update CHANGELOG.md

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Save state on session end | | |
| | Fix 2: "Ещё" loads in-place | | |
| | Fix 3: "Выход" saves and exits | | |
| | Fix 4: Clear lastSessionContext | | |
| | Fix 5: Click tests | | |
