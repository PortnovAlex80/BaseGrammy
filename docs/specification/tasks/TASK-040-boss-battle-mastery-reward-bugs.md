# TASK-040: Fix Boss Battle Mastery Recording and Reward Logic

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fix-boss-mastery-reward (from main)
**Spec:** 08-training-viewmodel.md §7, scenario-09 §4-5, 18-learning-methodology.md §8.4
**UC:** UC-15 (AC1, AC5), UC-16 (AC3)
**Scenario:** scenario-09-boss-battle.md
**User Journey:** user-journey-models.md Steps 5.1-5.4, Discrepancies BUG-NAV-001, BUG-NAV-006, BUG-NAV-007

---

## Problem

Boss battle mode has three bugs affecting mastery tracking and reward logic:

1. **Boss records mastery (BUG-NAV-001):** `recordCardShowForMastery()` is called at 3 code sites during boss battles WITHOUT checking `bossActive` flag. Spec 18.8.4 explicitly states boss battles should be separate from the mastery/flower system. These calls inflate `uniqueCardShows` and advance SRS interval steps during boss practice, giving users unearned mastery progress.

   Code sites:
   - `TrainingViewModel.submitAnswer()` ~line 632
   - `TrainingViewModel.nextCard()` ~line 886
   - `SessionRunner.startSession()` ~line 2422 (via card show recording)

2. **Stale state in reward resume (BUG-NAV-006):** `BossOrchestrator.clearBossRewardMessage()` reads `bossActive`, `sessionState`, `currentCard`, `inputMode` at different points in a complex conditional for `shouldResumeTimer`. If state changes between reads, the resume decision may be based on stale values. This can cause the timer to not resume after dismissing a reward, or to resume when it shouldn't.

3. **Reward overwrite on replay (BUG-NAV-007):** When user replays a boss battle, the latest reward overwrites the previous one (not best-of). A user who earned GOLD can replay and get BRONZE, losing their GOLD record permanently. Spec says nothing explicit about this but the behavior is counter-intuitive.

Root cause: Boss battle code was added to the existing training flow without proper guards separating boss-specific behavior from normal mastery tracking.

## Changes

### Fix 1: Guard mastery recording with bossActive check
**Discrepancy:** BUG-NAV-001 | **UC:** UC-15 AC1 | **Spec:** 18-learning-methodology.md §8.4

Add `bossActive` guard to all `recordCardShowForMastery()` calls. The method should return early (skip recording) when `bossActive == true`.

Two approaches (choose one):
- **A:** Add early return inside `recordCardShowForMastery()` itself: `if (bossActive) return`
- **B:** Add `if (!bossActive)` guard at each call site in TrainingViewModel

Preferred: Approach A — single guard point, impossible to miss in future call sites.

**Files:**
- `feature/progress/ProgressTracker.kt` — `recordCardShowForMastery()` method
- `TrainingViewModel.kt` — verify all 3 call sites are covered

**Verification:**
- Start boss battle, answer cards, check MasteryStore — uniqueCardShows should NOT increase
- Start normal training, answer cards, check MasteryStore — uniqueCardShows SHOULD increase

### Fix 2: Snapshot state atomically for reward resume decision
**Discrepancy:** BUG-NAV-006 | **UC:** UC-16 AC3 | **Spec:** scenario-09 §4

In `BossOrchestrator.clearBossRewardMessage()`, read all relevant state fields once at the start of the method into local variables, then use those locals for the `shouldResumeTimer` decision. This prevents TOCTOU (time-of-check-to-time-of-use) bugs.

**Files:** `feature/boss/BossOrchestrator.kt` — `clearBossRewardMessage()` method (~line 230-260)

**Verification:** Dismiss boss reward during active session — timer should reliably resume. Test with rapid reward dismissals.

### Fix 3: Keep best reward on replay
**Discrepancy:** BUG-NAV-007 | **UC:** N/A | **Spec:** scenario-09 §5

When recording boss reward, compare with existing reward and keep the higher tier. Use ordinal comparison: GOLD > SILVER > BRONZE > null.

**Files:**
- `feature/boss/BossOrchestrator.kt` — `finishBoss()` method
- Or `feature/progress/ProgressTracker.kt` — wherever rewards are persisted

**Verification:**
- Play boss, earn BRONZE
- Replay boss, earn GOLD → reward should be GOLD
- Replay boss, earn BRONZE → reward should stay GOLD

---

## Verification Checklist
1. Boss battle: uniqueCardShows does NOT increase during boss practice
2. Normal training: uniqueCardShows DOES increase during normal practice
3. Boss reward dismissal: timer resumes reliably in all states
4. Boss replay: reward is best-of (never downgrades)
5. Boss battle completion still navigates to LESSON with reward dialog
6. Normal sub-lesson completion still works correctly
7. Flower states not affected by boss battle activity

## Scope Boundaries
**Do NOT touch:**
- SessionRunner (not involved in boss mastery recording)
- DailyPracticeCoordinator (completely separate)
- GrammarMateApp.kt navigation
- Boss card generation logic

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from Verification Checklist
4. **Cross-task regression:** normal training mastery tracking, flower state calculation, SRS intervals
5. **UC/AC spot-check:** read UC-15 AC1, AC5 from 22-use-case-registry.md
6. **Spec sync:** update scenario-09 discrepancy status to SPEC RESOLVED, CODE PENDING

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Guard mastery with bossActive | | |
| | Fix 2: Atomic state snapshot for reward resume | | |
| | Fix 3: Keep best reward on replay | | |
