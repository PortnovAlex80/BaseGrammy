# TASK-070: Daily Practice Cursor Stuck + Tablet Lesson Boundary

**Status:** IN PROGRESS (Fix 1 done)
**Created:** 2026-05-18
**Branch:** feature/daily-cursor-stuck (from develop)
**Spec:** 09-daily-practice.md#9.3.5, #9.6.4, #9.8.7
**UC:** UC-24 AC1-AC3, UC-21 AC2, UC-61 AC1
**Scenario:** scenario-06-daily-practice.md

---

## Problem

### Bug 1: Cursor never advances (root cause confirmed)

Daily practice cursor (`DailyCursorState`) is permanently stuck at `sentenceOffset=0, currentLessonIndex=0`. After completing all 3 blocks, the user always gets the same 10 sentence cards from lesson 0 and the same tense set (Presente only).

**Root cause:** `recordDailyCardPracticed()` is never called for TRANSLATE and VERBS blocks.

Two parallel session systems exist:
- **System A** (`DailyPracticeSessionProvider`) — has `onCardAdvanced` callback, filters WORD_BANK, calls `recordDailyCardPracticed()`. **Dead code — never instantiated.**
- **System B** (`SessionRunner.startCardSession()`) — actually runs TRANSLATE/VERBS blocks via `DAILY_TRANSLATE`/`DAILY_VERBS` screen modes. Has **zero awareness** of `dailyPracticeAnsweredCounts`.

Since System B handles all TRANSLATE/VERBS execution, `dailyPracticeAnsweredCounts` stays empty. In `cancelDailySession()`:
```kotlin
val sentenceCount = dailyPracticeAnsweredCounts[TRANSLATE] ?: 0  // always 0
val verbCount = dailyPracticeAnsweredCounts[VERBS] ?: 0          // always 0
// allSentencePracticed = (0 >= 10) = false → cursor never advances
```

### Bug 2: Tablet floating bug — cards from next lesson

On tablets (not mobile), daily practice occasionally gives sentence cards from the next lesson instead of the current one. `buildSentenceBlock()` does NOT cross lesson boundaries (uses `drop(offset).take(sessionSize)` on a single lesson). The issue is upstream — likely in how `prebuildSession()` receives its `lessonLevel` parameter or how `resolveProgressLessonInfo()` fallback resolves level from regular training progress rather than `DailyCursorState`.

Needs investigation of the level resolution chain: `TrainingViewModel.init` → `prebuildSession(lessonLevel=?)` vs `startDailyPractice(effectiveLevel = cursor.currentLessonIndex + 1)`.

---

## Changes

### Fix 1: Wire `recordDailyCardPracticed` into TRANSLATE/VERBS card flow
**Discrepancy:** Code vs Spec 09#9.3.5 | **UC:** UC-24 AC1 | **Spec:** 09-daily-practice.md#9.3.5, #9.6.4

Add a callback from `SessionRunner` (or from `TrainingViewModel` wrapper) that calls `recordDailyCardPracticed(blockType)` for each card answered in VOICE or KEYBOARD mode during `DAILY_TRANSLATE` and `DAILY_VERBS` sessions.

**Approach options (pick simplest that works):**

**Option A — TrainingViewModel intercept (recommended):**
In `TrainingViewModel`, when `nextCard()` is called and `screenMode` is `DAILY_TRANSLATE` or `DAILY_VERBS`, check if the current input mode is not WORD_BANK, then call `recordDailyCardPracticed()` with the appropriate block type.

Files:
- `feature/training/SessionRunner.kt` — expose a callback or hook for "card advanced in non-WORD_BANK mode"
- `ui/TrainingViewModel.kt` — wire the callback to `recordDailyCardPracticed()`
- `feature/daily/DailyPracticeCoordinator.kt` — `recordDailyCardPracticed()` already exists (lines 406-421)

**Verification:**
1. Complete a full daily practice session (all 3 blocks, VOICE/KEYBOARD answers)
2. Check `dailyPracticeAnsweredCounts` after completion: TRANSLATE should equal the block's task count
3. `cancelDailySession()` should return a non-null `sentenceCount`
4. `cursor.sentenceOffset` should advance by the practiced card count
5. Next daily session should start from the new offset

### Fix 2: Investigate and fix tablet lesson boundary bug
**UC:** UC-21 AC2, UC-24 AC2 | **Spec:** 09-daily-practice.md#9.6.4, #9.8.7

Investigate the full level resolution chain in `startDailyPractice()`:
1. Trace what `lessonLevel` is passed to `prebuildSession()` from `TrainingViewModel.init`
2. Trace how `resolveProgressLessonInfo()` determines level when cursor index is out of range
3. Check if regular training progress can "leak" into daily level resolution
4. Verify the `levelMismatch` guard in `startDailySession()` catches all discrepancies
5. Add defensive check: if `effectiveLevel` from cursor differs from level used to build blocks, rebuild from cursor

Files:
- `feature/daily/DailyPracticeCoordinator.kt` — `prebuildSession()`, `startDailyPractice()` level resolution
- `feature/daily/DailySessionComposer.kt` — `buildBlocks()` level parameter usage
- `ui/TrainingViewModel.kt` — what `lessonLevel` is passed to `prebuildSession()`

**Verification:**
1. Complete regular lesson training to advance progress past lesson 0
2. Enter daily practice — should still use `cursor.currentLessonIndex`, NOT regular training level
3. On tablet: verify Block 1 cards come from `cursor.currentLessonIndex` lesson
4. On tablet: verify Block 3 tenses match `effectiveLevel = cursor.currentLessonIndex + 1`

---

## Verification Checklist
1. [ ] Full daily session (3 blocks, VOICE/KEYBOARD) → cursor advances by sentence count
2. [ ] Partial session (exit after block 2) → cursor does NOT advance (existing behavior preserved)
3. [ ] Repeat session → uses first-session card IDs, cursor still advances on completion
4. [ ] `dailyPracticeAnsweredCounts[TRANSLATE]` equals number of VOICE/KEYBOARD cards after block 1
5. [ ] `dailyPracticeAnsweredCounts[VERBS]` equals number of VOICE/KEYBOARD cards after block 3
6. [ ] WORD_BANK answers do NOT increment answered counts
7. [ ] On tablet: Block 1 always uses `cursor.currentLessonIndex` regardless of regular training progress
8. [ ] `sentenceOffset >= lesson.cards.size` triggers lesson transition correctly
9. [ ] Pack wrap (last lesson → lesson 0) works correctly

## Scope Boundaries
**Do NOT touch:**
- VOCAB block rendering or SRS logic (working correctly)
- `DailyPracticeSessionProvider` dead code (separate cleanup task)
- Regular training session flow (unrelated to daily cursor)
- Streak calculation or recording logic
- Prebuilt session cache mechanism (only investigate, don't restructure)

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work:
   - Regular lesson training (start, answer, complete)
   - Vocab drill standalone
   - Verb drill standalone
   - Boss battle
   - Home screen flower states
   - Lesson roadmap navigation
5. **UC/AC spot-check:** UC-21, UC-24, UC-61 — confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-18 | Fix 1: Wire recordDailyCardPracticed | Done | Implemented in `TrainingViewModel.submitAnswer()` (lines ~628-643) with `isDailySession()` helper (line ~748). Counts VOICE/KEYBOARD answers in DAILY_TRANSLATE/DAILY_VERBS sessions. Spec updated. |
| | Fix 2: Tablet lesson boundary | | **Likely consequence of Bug 1.** Since `dailyPracticeAnsweredCounts` was always 0, `cancelDailySession()` never advanced the cursor, meaning the cursor always stayed at `sentenceOffset=0, currentLessonIndex=0`. Tablet users seeing cards from "next lesson" may have been seeing regular training progress leaking into daily level resolution (discrepancy #7 in scenario-06), which was already spec-resolved. Verify after Fix 1 is tested on device. |
