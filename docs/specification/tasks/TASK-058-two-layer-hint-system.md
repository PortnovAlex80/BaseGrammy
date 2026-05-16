# TASK-058: Two-Layer Hint System — Difficulty + Scheduler

**Status:** OPEN
**Created:** 2026-05-17
**Branch:** feature/two-layer-hints (from feature/pomodoro-timer)
**Spec:** 21-product-roadmap.md#2, 03-algorithms-and-calculators.md#3.7, 01-models-and-state.md#HintLevel, 12-training-card-session.md#TCS-02, 08-training-viewmodel.md#2.17
**UC:** UC-84 AC1-AC5, UC-85 AC1-AC8, UC-86 AC1-AC5
**Scenario:** scenario-01-training-flow, scenario-05-input-modes

---

## Problem

The `HintLevel` enum (EASY/MEDIUM/HARD) is fully plumbed (defined, persisted in config.yaml, settings UI with 3 FilterChips, threaded to all screens) but has ZERO behavioral logic. Parenthetical hints like `(dire)` and `(verità)` in Russian prompts are always stripped unconditionally. Word Bank is always available. Boss battle ignores difficulty. The scheduler has no concept of progressive hint removal based on card encounter count.

The spec defines a two-layer hint system where scheduler encounter count and user difficulty setting combine (more restrictive wins). Neither layer is implemented.

## Changes

### Fix 1: Implement two-layer hint calculation algorithm
**Discrepancy:** N/A | **UC:** UC-84, UC-85 | **Spec:** 03-algorithms-and-calculators.md#3.7

Create a `HintCalculator` helper class in `feature/training/` that:
1. Takes `promptRu`, `encounterCount`, `hintLevel`, `sessionOffset` as inputs
2. Calculates scheduler fraction: 1st=1.0, 2nd=0.5, 3rd+=0.0
3. Calculates user fraction: EASY=1.0, MEDIUM=0.5, HARD=0.0
4. Combines: min(scheduler, user)
5. Returns filtered prompt: all hints / 50% hints (even-indexed with offset) / no hints

The 50% algorithm: number all parenthetical matches in the string, show where `(index + offset) % 2 == 0`, strip others. Offset is randomized once per session.

**Files:** `app/src/main/java/com/alexpo/grammermate/feature/training/HintCalculator.kt` (NEW)

**Verification:** Unit tests: EASY+1st=all, HARD+1st=none, MEDIUM+2nd=50%, EASY+3rd=none, HARD+3rd=none.

### Fix 2: Wire HintCalculator into card display pipeline
**Discrepancy:** N/A | **UC:** UC-84 AC1-AC5, UC-85 AC1-AC8 | **Spec:** 12-training-card-session.md#TCS-02, 08-training-viewmodel.md

Replace the unconditional `Regex("\\s*\\([^)]+\\)")` stripping in:
- `TrainingCardSession.kt` (Default Card Content, ~line 261)
- `TrainingScreen.kt` (drill mode ~line 202, normal mode ~line 246)
- `VerbDrillScreen.kt` (prompt display)
- `DailyPracticeScreen.kt` (prompt display)
- `VocabDrillScreen.kt` (prompt display)

With calls to `HintCalculator.calculateEffectiveHints()` passing the current `hintLevel` and card's encounter count.

The `sessionOffset` is generated once when a session starts (in TrainingViewModel or equivalent) and stored in CardSessionState as a new field `hintSessionOffset: Int`.

**Files:** 
- `app/src/main/java/com/alexpo/grammermate/ui/components/TrainingCardSession.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/screens/VerbDrillScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/screens/DailyPracticeScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/screens/VocabDrillScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt` (CardSessionState: add hintSessionOffset field)

**Verification:** Switch Settings to HARD → all parenthetical hints disappear. Switch to EASY → hints appear. MEDIUM → roughly half visible.

### Fix 3: Gate Word Bank by HintLevel
**Discrepancy:** N/A | **UC:** UC-85 AC4-AC5 | **Spec:** 21-product-roadmap.md#2.2

In the input mode logic, when `hintLevel >= MEDIUM`, disable Word Bank:
- `UnifiedInputControlsBar` or equivalent: add check `hintLevel != HintLevel.HARD && hintLevel != HintLevel.MEDIUM` for Word Bank button visibility/availability
- If user was in WORD_BANK mode and difficulty changes to MEDIUM/HARD, auto-switch to VOICE
- SessionRunner's `updateWordBank()`: skip word bank generation when MEDIUM/HARD

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/components/UnifiedInputControlsBar.kt` (or wherever word bank button is)
- `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt`

**Verification:** Set MEDIUM → Word Bank button gone. Set EASY → Word Bank button back.

### Fix 4: Boss battle forces HARD
**Discrepancy:** N/A | **UC:** UC-86 AC1-AC5 | **Spec:** 21-product-roadmap.md#2.4

In BossBattleRunner, when starting a boss session:
- Set `hintLevel = HintLevel.HARD` for the session (override, don't change user's saved preference)
- Ensure Word Bank is unavailable
- Ensure parenthetical hints are stripped
- Do NOT persist this change to AppConfigStore — only for the boss session duration

**Files:** `app/src/main/java/com/alexpo/grammermate/feature/boss/BossBattleRunner.kt`

**Verification:** Start boss battle → no parenthetical hints, no Word Bank. After boss → user's setting restored.

### Fix 5: Track card encounter count for scheduler layer
**Discrepancy:** N/A | **UC:** UC-84 AC4 | **Spec:** 03-algorithms-and-calculators.md#3.7

Add encounter count tracking to the card/session data:
- When a card is first shown in a session, increment its encounter count
- Store per-card encounter count (in progress data or a separate store)
- Reset encounter count only when the card's lesson is fully completed or reset (NOT on app restart)
- Pass encounter count to HintCalculator when displaying the card

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt` or new `CardEncounterStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt` (add encounterCount to SentenceCard or equivalent)

**Verification:** Card shown 1st time → all hints (EASY). Same card 2nd time in MIXED → 50% hints. Same card 3rd time → no hints.

---

## Verification Checklist
1. EASY + 1st encounter: all parenthetical hints visible, Word Bank available
2. MEDIUM + 1st encounter: 50% hints visible, no Word Bank
3. HARD + 1st encounter: no hints, no Word Bank, keyboard works
4. EASY + 2nd encounter: 50% hints (scheduler stricter)
5. EASY + 3rd encounter: no hints (scheduler already removed)
6. MEDIUM + 2nd encounter: 50% hints
7. Boss battle: no hints, no Word Bank regardless of user setting
8. After boss battle: user's original setting restored
9. Session offset randomization: different sessions show different 50% subsets
10. Keyboard always available at all levels
11. Settings change persists across app restart
12. Tense labels, verb chips, POS badges always visible regardless of level
13. Eye/show-answer always works regardless of level
14. Mastery/SRS counting unaffected by difficulty level

## Scope Boundaries
**Do NOT touch:**
- StreakStore / StreakManager (unrelated)
- FlowerCalculator (unrelated)
- LessonStore / pack import (unrelated)
- TTS/ASR engines (unrelated)
- GrammarMateApp navigation structure (unrelated)
- CardDifficultyRating enum (separate concept — Pomodoro card rating)
- AppConfigStore persistence format (just use existing hintLevel field)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each of the 14 verification checklist items
4. **Cross-task regression:**
   - Training flow works at all difficulty levels
   - Word Bank works on EASY, hidden on MEDIUM/HARD
   - Verb drill, vocab drill, daily practice all respect hint level
   - Boss battle shows no hints
   - Settings selector persists and applies immediately
   - Existing streak/flower/mastery systems unaffected
5. **UC/AC spot-check:** read UC-84, UC-85, UC-86 from `22-use-case-registry.md`, confirm all ACs hold
6. **Spec sync:** specs already updated

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: HintCalculator algorithm | | |
| | Fix 2: Wire into display pipeline | | |
| | Fix 3: Gate Word Bank by HintLevel | | |
| | Fix 4: Boss battle forces HARD | | |
| | Fix 5: Card encounter count tracking | | |
