# TASK-043: Fix VocabDrill Mastery Indicator and Exit Cleanup

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-vocab-mastery-indicator (from main)
**Spec:** 11-vocab-drill.md §5, scenario-08 §4
**UC:** UC-30 (AC3)
**Scenario:** scenario-08-vocab-drill.md
**User Journey:** user-journey-models.md Steps 8.1-8.3, Discrepancies BUG-NAV-015, BUG-NAV-016

---

## Problem

VocabDrill has two issues with mastery display and exit behavior:

1. **isLearned threshold mismatch (BUG-NAV-015):** Code uses `LEARNED_THRESHOLD = 3` (step >= 3 = learned). This is the correct threshold per spec 11-vocab-drill.md. However, the card back mastery indicator shows "Learned" label only at step >= 9 (full mastery). The data model says the word is learned at step 3, but the UI doesn't reflect this until step 9. This creates user confusion — words are "learned" in the data but the UI still shows them as unlearned.

2. **Unnecessary refresh on exit (BUG-NAV-016):** `vm.refreshVocabMasteryCount()` is called on every back press from VOCAB_DRILL, even if no cards were actually practiced. This triggers unnecessary file I/O (reading word_mastery.yaml). Not harmful but wasteful.

Root cause: The mastery indicator was hardcoded to step 9 instead of using the LEARNED_THRESHOLD constant. The exit refresh was added as a safety measure but is overly aggressive.

## Changes

### Fix 1: Align UI mastery indicator with LEARNED_THRESHOLD
**Discrepancy:** BUG-NAV-015 | **UC:** UC-30 AC3 | **Spec:** 11-vocab-drill.md §5

In VocabDrillScreen.kt, find the mastery indicator rendering on the card back. Change the "Learned" label condition from `step >= 9` to `step >= LEARNED_THRESHOLD` (where LEARNED_THRESHOLD = 3). Import the constant from VocabWord.kt or define it locally if not accessible.

Visual states should be:
- step 0-2: "Learning" (neutral color)
- step 3-9: "Learned" (green, with step indicator)
- step 9: "Mastered" (gold/special color, optional enhancement)

**Files:** `ui/screens/VocabDrillScreen.kt` — card back mastery indicator rendering

**Verification:** 
- Rate a word as "Good" 3 times (step reaches 3)
- Card back should show "Learned" label
- Previously it would show "Learning" until step 9

### Fix 2: Skip refresh when no cards practiced
**Discrepancy:** BUG-NAV-016 | **UC:** N/A | **Spec:** N/A

Add a dirty flag to VocabDrillViewModel that tracks whether any ratings were submitted during the session. Only call `refreshVocabMasteryCount()` when the flag is true. Reset the flag after refresh.

**Files:** `ui/GrammarMateApp.kt` — VOCAB_DRILL back handler (~line 362-364), `ui/screens/VocabDrillScreen.kt` or VocabDrillViewModel

**Verification:** 
- Enter VocabDrill, immediately press back → no refreshVocabMasteryCount call
- Enter VocabDrill, rate a card, press back → refreshVocabMasteryCount called

---

## Verification Checklist
1. Word at step 3 shows "Learned" on card back
2. Word at step 0-2 shows "Learning" on card back
3. Word at step 9 shows "Mastered" (if implemented) or "Learned"
4. Exit without practicing: no refreshVocabMasteryCount I/O
5. Exit after practicing: refreshVocabMasteryCount called, HOME mastery count updated
6. Full VocabDrill session runs correctly (select → cards → completion)

## Scope Boundaries
**Do NOT touch:**
- WordMasteryStore logic
- AnswerRating / interval ladder calculations
- VocabDrillViewModel session management
- Other screens' exit handlers

## Regression Plan
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** check each item above
4. **Cross-task regression:** HOME screen mastery count display, VocabDrill selection screen stats
5. **Spec sync:** confirm 11-vocab-drill.md LEARNED_THRESHOLD documentation

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Align mastery indicator with LEARNED_THRESHOLD | | |
| | Fix 2: Skip refresh when no cards practiced | | |
