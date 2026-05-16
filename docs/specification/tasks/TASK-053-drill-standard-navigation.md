# TASK-053: Drill Sub-mode Standard Navigation

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/drill-standard-navigation (from main)
**Spec:** 12-training-card-session.md §12.5.5, 08-training-viewmodel.md §2.10
**UC:** UC-69 (AC3, AC11), UC-70 (AC1-AC10)
**Scenario:** scenario-16-drill-sublesson.md

---

## Problem

Drill sub-mode (isDrillMode) uses a separate navigation mechanism from all other training modes. Instead of loading all cards into `sessionCards` and using standard `navigateNext()`/`navigatePrev()`, it loads 1 card at a time via `loadDrillCard()` and advances via `advanceDrillCard()`. This violates Design Principle DP-04 (Universal Card Engine) which states all modes use the same navigation.

The spec has been updated (scenario-16, UC-69 AC11, 08-training-viewmodel deprecation notes) to describe the target state. This task implements the migration.

## Changes

### Fix 1: Load all drill cards into sessionCards at startDrill
**Discrepancy:** Code loads 1 card | **UC:** UC-69 AC3 | **Spec:** 12-training-card-session.md §12.5.5

Modify `SessionRunner.startDrill()` to load ALL `lesson.drillCards` into `sessionCards` instead of calling `loadDrillCard(startCardIndex)`. Set:
- `sessionCards = drillCards` (all cards)
- `currentIndex = startCardIndex`
- `currentCard = drillCards[startCardIndex]`
- `subLessonTotal = drillCards.size`
- `drillCardIndex = startCardIndex` (kept for progress tracking)

**Files:** `feature/training/SessionRunner.kt` — `startDrill()` method

**Verification:** After startDrill, `sessionCards.size == drillCards.size`

### Fix 2: Use standard navigateNext/navigatePrev for drill
**Discrepancy:** Uses advanceDrillCard | **UC:** UC-70 AC1, AC3 | **Spec:** 08-training-viewmodel.md §2.10

Ensure `navigateNext()` and `navigatePrev()` in SessionRunner work correctly when `isDrillMode == true`. Since all cards are now in `sessionCards`, standard navigation should work. Add drill-specific handling:
- `navigateNext()`: on last card in drill mode, call `finishDrill()` instead of sub-lesson completion
- Sync `drillCardIndex` with `currentIndex` on each navigation
- Save `drillProgressStore.saveDrillProgress()` on each card advance

**Files:** `feature/training/SessionRunner.kt` — `navigateNext()`, `navigatePrev()`

**Verification:** Next/Prev arrows work in drill mode, advancing through all drill cards

### Fix 3: Wire finishDrill into navigateNext last-card boundary
**Discrepancy:** N/A | **UC:** UC-70 AC4 | **Spec:** scenario-16-drill-sublesson.md Step 4

In `navigateNext()`, add a check: if `isDrillMode && isOnLastCard`, call `finishDrill()` instead of normal sub-lesson completion. The existing `nextCardInternal()` already has a last-card check at line ~447 that skips drill mode (`!state.drill.isDrillMode`). Update this to handle drill's own completion.

**Files:** `feature/training/SessionRunner.kt` — `navigateNext()`, `nextCardInternal()`

**Verification:** On last drill card, Next triggers completion and returns to LESSON

### Fix 4: Deprecate and remove advanceDrillCard / loadDrillCard
**Discrepancy:** N/A | **UC:** UC-69 AC11 | **Spec:** 08-training-viewmodel.md §2.10 deprecation notes

After Fixes 1-3, `advanceDrillCard()` and `loadDrillCard()` are no longer needed for normal drill flow. Keep `loadDrillCard()` as internal helper if needed for resume, but remove `advanceDrillCard()` from public API. Update callers in `TrainingViewModel`:
- `submitDrillAnswer()` — no longer calls `advanceDrillCard()`
- `flagBadSentence()` / `hideCurrentCard()` — `BadSentenceResult.AdvanceDrillCard` can be replaced with `SkipToNextCard` (standard skip)

**Files:** `feature/training/SessionRunner.kt`, `ui/TrainingViewModel.kt`, `feature/progress/BadSentenceResult.kt`

**Verification:** Grep for `advanceDrillCard` → zero results (or only in deprecated comment)

### Fix 5: Sync drillCardIndex with currentIndex
**Discrepancy:** N/A | **UC:** UC-70 AC6 | **Spec:** scenario-16-drill-sublesson.md Step 3

Since standard navigation uses `currentIndex`, ensure `drillCardIndex` stays in sync for progress persistence. In `navigateNext()` and `navigatePrev()`, after updating `currentIndex`, also update `drillCardIndex = currentIndex` and call `drillProgressStore.saveDrillProgress()`.

**Files:** `feature/training/SessionRunner.kt`

**Verification:** Exit drill mid-session → re-enter → resumes at correct card

### Fix 6: Update scenario-16 SESSION_SIZE reference
**Discrepancy:** Hardcoded "10" | **UC:** N/A | **Spec:** scenario-16-drill-sublesson.md

In `docs/specification/scenario-16-drill-sublesson.md` the comparison table has `10 (sub-lesson size)`. Update to `SESSION_SIZE (default 10)`.

**Files:** `docs/specification/scenario-16-drill-sublesson.md` line 109

**Verification:** No hardcoded session sizes in scenario-16

---

## Verification Checklist
1. Drill loads ALL cards into sessionCards at session start
2. Next/Prev arrows navigate through all drill cards using standard navigation
3. On last card, Next triggers drill completion (finishDrill)
4. On first card, Prev stays on first card
5. drillCardIndex stays synced with currentIndex for progress persistence
6. Drill progress saved on exit, resumed correctly on re-entry
7. Green visual theme applied correctly
8. advanceDrillCard() removed from public API
9. `assembleDebug` passes
10. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- VerbDrill or VocabDrill (separate screens)
- DailyPractice
- Training normal/boss/elite modes
- BadSentenceStore or HiddenCardStore
- Mastery/flower/streak calculations
- AppConfigStore or SESSION_SIZE (TASK-050)

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after each fix
3. **Per-task verification:** each item from the Verification Checklist
4. **Cross-task regression:** verify normal training, boss battle, and daily practice navigation still work
5. **UC/AC spot-check:** UC-69 and UC-70 ACs hold
6. **Spec sync:** update CHANGELOG + trace-index

## Dependency
Independent from TASK-050/051. Can run in parallel.

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Load all drill cards | | |
| | Fix 2: Standard navigation | | |
| | Fix 3: finishDrill on last card | | |
| | Fix 4: Remove advanceDrillCard | | |
| | Fix 5: Sync drillCardIndex | | |
| | Fix 6: SESSION_SIZE reference | | |
