# TASK-050: Unify Session Size Parameter (Учебная единица)

**Status:** DONE
**Created:** 2026-05-16
**Branch:** feature/unify-session-size (from main)
**Spec:** 01-models-and-state.md §glossary, 06-infrastructure.md §AppConfig, 09-daily-practice.md §9.1-9.5, 10-verb-drill.md §5, 11-vocab-drill.md §11.5
**UC:** UC-02, UC-21, UC-26, UC-30
**Scenario:** scenario-01, scenario-06, scenario-07, scenario-08
**User Journey:** user-journey-models.md (all journeys)
**Priority:** Architectural prerequisite for TASK-051 (Fire Streak)

---

## Problem

Four independent locations define the same concept (session/block size = 10 cards) with different names and mechanisms:

1. **`TrainingConfig.SUB_LESSON_SIZE_DEFAULT = 10`** — compile-time constant, used by CardProvider, SessionRunner, TrainingViewModel, MixedReviewScheduler
2. **`DailySessionComposer.CARDS_PER_BLOCK = 10`** — compile-time constant with aliases SENTENCE_COUNT, VOCAB_COUNT, VERB_COUNT
3. **`VerbDrillViewModel` lines 326, 328** — hardcoded `.take(10)`
4. **`VocabDrillViewModel` line 230** — hardcoded `.take(10)`

Additionally, `AppConfigStore` has no session size field — the value cannot be changed at runtime without recompilation.

Spec files contain 17 stale references to "5 cards" for the vocab block (code changed to 10 long ago, specs not updated).

The new term "учебная единица" (Learning Unit) standardizes this: one session of SESSION_SIZE cards, configurable via AppConfig.

## Changes

### Fix 1: Add sessionSize to AppConfig
**Discrepancy:** N/A (new parameter) | **UC:** UC-02 | **Spec:** 06-infrastructure.md §AppConfig

Add `sessionSize: Int = 10` to `AppConfig` data class in `AppConfigStore.kt`. Add serialization in `save()` and deserialization in `load()`. Add to seed `config.yaml` in assets. Validate bounds: `SESSION_SIZE_MIN (6) <= sessionSize <= SESSION_SIZE_MAX (12)`.

**Files:**
- `data/AppConfigStore.kt` — AppConfig data class, save(), load()
- `assets/grammarmate/config.yaml` — add `sessionSize: 10`

**Verification:** Load config with sessionSize=8 → all modes use 8-card sessions. Load with sessionSize=12 → all modes use 12-card sessions.

### Fix 2: Thread sessionSize to TrainingConfig consumers
**Discrepancy:** N/A | **UC:** UC-02 | **Spec:** 01-models-and-state.md, 03-algorithms-and-calculators.md

Replace `TrainingConfig.SUB_LESSON_SIZE_DEFAULT` with value from AppConfig. Keep `SUB_LESSON_SIZE_MIN` and `SUB_LESSON_SIZE_MAX` as bounds. Thread through:
- `TrainingViewModel` reads `config.sessionSize`, passes to `CardProvider` and `SessionRunner`
- `CardProvider` uses it for sub-lesson sizing
- `SessionRunner` uses it for session sizing
- `MixedReviewScheduler` receives it as constructor parameter (already does)

**Files:**
- `ui/TrainingViewModel.kt` — load sessionSize from config, pass to CardProvider/SessionRunner
- `feature/training/CardProvider.kt` — constructor param subLessonSize reads from config
- `feature/training/SessionRunner.kt` — constructor param reads from config
- `data/MixedReviewScheduler.kt` — already receives as constructor param, no change needed
- `data/TrainingConfig.kt` — SUB_LESSON_SIZE_DEFAULT becomes fallback default, not the source of truth

**Verification:** Change sessionSize to 8 in config → training sub-lesson has 8 cards.

### Fix 3: Thread sessionSize to DailySessionComposer
**Discrepancy:** N/A | **UC:** UC-21 | **Spec:** 09-daily-practice.md §9.1-9.5

Replace `DailySessionComposer.CARDS_PER_BLOCK = 10` with value from AppConfig. Thread through:
- `DailySessionComposer` receives sessionSize as constructor parameter
- `DailyPracticeSessionProvider` reads it from DailySessionComposer
- All block builders (buildSentenceBlock, buildVocabBlock, buildVerbBlock) use the parameter instead of constant

**Files:**
- `feature/daily/DailySessionComposer.kt` — constructor param replaces CARDS_PER_BLOCK constant
- `feature/daily/DailyPracticeSessionProvider.kt` — reads from DailySessionComposer
- `feature/daily/DailyPracticeCoordinator.kt` — passes sessionSize when creating DailySessionComposer

**Verification:** Change sessionSize to 8 → all 3 daily blocks have 8 cards each.

### Fix 4: Thread sessionSize to VerbDrillViewModel
**Discrepancy:** N/A | **UC:** UC-26 AC4 | **Spec:** 10-verb-drill.md §5

Replace hardcoded `.take(10)` with `.take(sessionSize)` in VerbDrillViewModel. Load sessionSize from AppConfig.

**Files:**
- `ui/VerbDrillViewModel.kt` — lines 326, 328: `.take(10)` → `.take(sessionSize)`

**Verification:** Change sessionSize to 8 → verb drill batch has 8 cards.

### Fix 5: Thread sessionSize to VocabDrillViewModel
**Discrepancy:** N/A | **UC:** UC-30 AC4 | **Spec:** 11-vocab-drill.md §11.5

Replace hardcoded `.take(10)` with `.take(sessionSize)` in VocabDrillViewModel. Load sessionSize from AppConfig.

**Files:**
- `ui/VocabDrillViewModel.kt` — line 230: `.take(10)` → `.take(sessionSize)`

**Verification:** Change sessionSize to 8 → vocab drill batch has 8 cards.

---

## Verification Checklist
1. Default sessionSize=10 → all modes behave exactly as before (no regression)
2. sessionSize=8 → training sub-lesson has 8 cards, daily blocks have 8 cards each, verb drill batch=8, vocab drill batch=8
3. sessionSize=6 (minimum) → all modes work with 6 cards
4. sessionSize=12 (maximum) → all modes work with 12 cards
5. sessionSize=0 or negative → clamped to MIN (6), no crash
6. sessionSize=100 → clamped to MAX (12), no crash
7. ELITE_SIZE_MULTIPLIER still works: elite session = ceil(sessionSize * 1.25) cards
8. MixedReviewScheduler still works: reviewSlots = sessionSize/2, currentSlots = sessionSize - reviewSlots
9. Daily practice cursor advancement uses sessionSize (not hardcoded 10)
10. Boss battle card pool unaffected (uses MAIN_POOL_SIZE=150)
11. `assembleDebug` passes
12. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- ELITE_SIZE_MULTIPLIER (1.25x) — multiplies on top of sessionSize, leave untouched
- MAIN_POOL_SIZE (150) — unrelated to session size
- Spaced repetition interval ladder
- Mastery/flower calculation logic
- GrammarMateApp.kt navigation
- Data persistence format (YAML files)
- Boss battle logic
- CardSessionStateModel interface

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after all fixes
3. **Per-task verification:** each item from Verification Checklist
4. **Cross-task regression:** training flow, daily practice 3 blocks, verb drill batch, vocab drill batch, mixed review scheduling, elite/daily sizing
5. **UC/AC spot-check:** UC-02 AC1, UC-21 AC2, UC-26 AC4, UC-30 AC4
6. **Spec sync:** verify 01-models-and-state.md glossary, 06-infrastructure.md AppConfig table match implementation

## Git
One commit per fix. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Add sessionSize to AppConfig | | |
| | Fix 2: Thread to TrainingConfig consumers | | |
| | Fix 3: Thread to DailySessionComposer | | |
| | Fix 4: Thread to VerbDrillViewModel | | |
| | Fix 5: Thread to VocabDrillViewModel | | |
