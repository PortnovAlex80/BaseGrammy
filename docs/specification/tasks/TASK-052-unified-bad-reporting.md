# TASK-052: Unified Bad Sentence Reporting Across All Modes

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/unified-bad-reporting (from main)
**Spec:** 23-screen-elements.md (SH-01, VD-32, DP-18, VOC-41), 08-training-viewmodel.md
**UC:** UC-73 (all ACs)
**Scenario:** scenario-01 (training), scenario-07 (verb drill), scenario-08 (vocab drill), scenario-16 (drill sub-mode)

---

## Problem

Bad sentence reporting is inconsistent across training modes. The "Hide card" button appears in SharedReportSheet for all modes, but is a no-op in VerbDrill, DailyPractice (translate/verb blocks and vocab flashcards), and VocabDrill. HiddenCardStore is only checked during card loading in Training mode — VerbDrill and VocabDrill never exclude hidden cards. BadSentenceHelper is only used by TrainingViewModel; VerbDrillVM and VocabDrillVM call badSentenceStore directly. Export format is inconsistent: VocabDrill uses single-pack export while all others use unified.

This blocks TASK-051 (Fire Streak) which relies on accurate bad sentence tracking for "засчитанная УЕ" calculation (completed cards minus bad sentences).

## Changes

### Fix 1: Make hide card work in VerbDrill
**Discrepancy:** Hide card is no-op | **UC:** UC-73 AC2, AC3 | **Spec:** 23-screen-elements.md VD-32

Replace `VerbDrillCardSessionProvider.hideCurrentCard()` no-op with a real implementation that calls `HiddenCardStore.hideCard(cardId)`. Add `hiddenCardStore` dependency to `VerbDrillViewModel`. Check hidden cards in `VerbDrillViewModel.loadCards()` — exclude cards with IDs in `hiddenCardStore.getHiddenCardIds()`.

**Files:** `ui/VerbDrillCardSessionProvider.kt`, `ui/VerbDrillViewModel.kt`

**Verification:** In VerbDrill, flag a card -> tap Hide card -> exit session -> re-enter -> hidden card not shown

### Fix 2: Make hide card work in DailyPractice
**Discrepancy:** Hide card is no-op | **UC:** UC-73 AC2 | **Spec:** 23-screen-elements.md DP-18

Replace the `onHideCard = { /* no-op */ }` in DailyPracticeScreen (translate/verb blocks and vocab flashcard block) with real hide implementation calling `HiddenCardStore.hideCard()`.

**Files:** `ui/DailyPracticeScreen.kt`

**Verification:** In Daily Practice, tap report -> hide card -> card is excluded from future sessions

### Fix 3: Make hide card work in VocabDrill
**Discrepancy:** Hide card only dismisses sheet | **UC:** UC-73 AC2, AC3 | **Spec:** 23-screen-elements.md VOC-41

Replace the `onHideCard = { showReportSheet = false }` no-op in VocabDrillScreen with real implementation. Add `hiddenCardStore` dependency to `VocabDrillViewModel`. Exclude hidden cards in `VocabDrillViewModel.filteredWords()` — filter out words whose IDs are in `hiddenCardStore.getHiddenCardIds()`.

**Files:** `ui/VocabDrillScreen.kt`, `ui/VocabDrillViewModel.kt`

**Verification:** In VocabDrill, flag a word -> tap Hide card -> exit -> re-enter -> hidden word not shown

### Fix 4: Unify BadSentenceHelper usage
**Discrepancy:** Direct badSentenceStore calls bypass helper | **UC:** UC-73 AC5 | **Spec:** 08-training-viewmodel.md

Extract bad sentence operations from `VerbDrillViewModel` and `VocabDrillViewModel` to use `BadSentenceHelper` (or a shared interface). All flag/unflag/export operations go through the helper. Eliminate direct `badSentenceStore` calls from these ViewModels.

**Files:** `ui/VerbDrillViewModel.kt`, `ui/VocabDrillViewModel.kt`, `feature/progress/BadSentenceHelper.kt`

**Verification:** Grep for direct `badSentenceStore` calls in VerbDrillVM and VocabDrillVM -> zero results

### Fix 5: Unify export to always use exportUnified()
**Discrepancy:** VocabDrill uses single-pack export | **UC:** UC-73 AC4 | **Spec:** 23-screen-elements.md VOC-41

Change `VocabDrillViewModel.exportBadSentences()` from `badSentenceStore.exportToTextFile(packId)` to `badSentenceStore.exportUnified()`.

**Files:** `ui/VocabDrillViewModel.kt`

**Verification:** In VocabDrill, export bad sentences -> file contains all packs, not just current pack

### Fix 6: Add SharedReportSheet to all modes consistently
**Discrepancy:** Different report sheet variants per mode | **UC:** UC-73 AC8 | **Spec:** 23-screen-elements.md SH-01

Ensure all modes use `SharedReportSheet` with the same 5 options. Remove any mode-specific report sheet composables. All callbacks (flag, unflag, hide, export, copy, share QR) wired through to the unified BadSentenceHelper.

**Files:** `ui/VerbDrillScreen.kt`, `ui/DailyPracticeScreen.kt`, `ui/VocabDrillScreen.kt`

**Verification:** Grep for VerbDrillReportSheet, DailyReportSheet -> zero results; only SharedReportSheet used

---

## Verification Checklist
1. Hide card works in all 5 modes (Training, VerbDrill, Daily translate, Daily vocab, VocabDrill)
2. Hidden cards excluded from future sessions in all modes
3. All modes use SharedReportSheet with identical options
4. All modes use exportUnified() for export
5. BadSentenceHelper used by all ViewModels
6. No direct badSentenceStore calls in VerbDrillVM/VocabDrillVM
7. `assembleDebug` passes
8. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- BadSentenceStore data format or migration
- Streak/fire calculation (TASK-051)
- TrainingScreen (already works correctly)
- Card session state management
- Navigation/session flow

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after each fix
3. **Per-task verification:** each item from the Verification Checklist
4. **Cross-task regression:** verify Training mode reporting still works, verify export format correct
5. **UC/AC spot-check:** UC-73 ACs hold after all fixes
6. **Spec sync:** update CHANGELOG + trace-index

## Dependency
This task MUST complete before TASK-051 Fix 3 (Засчитанная УЕ completion check) for accurate streak calculation.

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: VerbDrill hide card | | |
| | Fix 2: DailyPractice hide card | | |
| | Fix 3: VocabDrill hide card | | |
| | Fix 4: Unify BadSentenceHelper | | |
| | Fix 5: Unify export | | |
| | Fix 6: SharedReportSheet everywhere | | |
