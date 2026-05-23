# TASK-090: Fix Boss Battle Progress Not Updating During Battle

**Status:** OPEN
**Created:** 2026-05-24
**Branch:** feature/fix-boss-progress-update (from main)
**Spec:** 08-training-viewmodel.md §7, scenario-09 §4-5
**UC:** UC-17 (AC3), UC-18 (AC1, AC2, AC3)
**Scenario:** scenario-09-boss-battle.md, click-test-boss-battle.md Phase 5
**Test:** BossBattleClickUiTest.kt

---

## Problem

Boss battle mode has a critical bug where `bossProgress` counter increments internally but the UI doesn't reflect the progress updates during the battle.

**Symptoms:**
- Progress bar shows "0% (0/30)" even after submitting correct answers
- Progress text doesn't update from 0% → 3% → 6% as cards are answered correctly
- Test `BossBattleClickUiTest.bossBattle_correctAnswer_progressShouldUpdate` fails
- Test `BossBattleClickUiTest.bossBattle_showsCorrectTitleAndStats` fails

**Root Cause:**
`SessionRunner.nextCardInternal()` (lines 619-699) updates `cardSession.currentIndex` when advancing cards but does NOT update `boss.bossProgress` when `bossActive == true`.

**Impact:**
- Users can't see their progress during boss battles
- Progress bar stays at 0% throughout the entire battle
- Reward dialog may show incorrect progress percentage
- User experience is broken - no visual feedback for correct answers

**Code Location:**
`app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt:619-699`
```kotlin
// MISSING: bossProgress update
if (state.boss.bossActive) {
    // Need to add: boss.bossProgress = nextIndex
}
```

---

## Changes

### Fix: Update bossProgress in nextCardInternal()

**When:** `bossActive == true` and we're advancing to the next card
**What:** Update `boss.bossProgress` to track the highest card index reached

**Implementation:**

In `SessionRunner.nextCardInternal()` around line 619-699:

```kotlin
val nextIndex = it.index + 1
stateAccess.updateState { currentState ->
    currentState.copy(
        cardSession = currentState.cardSession.copy(
            currentIndex = nextIndex,
            currentCard = it,
            // ... other updates
        ),
        // ADD THIS: Update bossProgress during boss battles
        boss = if (currentState.boss.bossActive) {
            currentState.boss.copy(
                bossProgress = maxOf(currentState.boss.bossProgress, nextIndex)
            )
        } else {
            currentState.boss
        }
    )
}
```

**Files:**
- `app/src/main/java/com/alexpo/grammermate/feature/training/SessionRunner.kt` — `nextCardInternal()` method

**Verification:**
- Run `BossBattleClickUiTest` — both tests should PASS
- Manual test: Start boss battle, submit 3 correct answers, verify progress shows "10% (3/30)"
- Manual test: Complete boss battle with 100%, verify GOLD reward

---

## Verification Checklist

1. ✅ **Test passes:** `BossBattleClickUiTest.bossBattle_correctAnswer_progressShouldUpdate` PASS
2. ✅ **Test passes:** `BossBattleClickUiTest.bossBattle_showsCorrectTitleAndStats` PASS
3. ✅ **Manual test:** Progress updates from 0% → 3% after first correct answer
4. ✅ **Manual test:** Progress updates from 3% → 6% after second correct answer
5. ✅ **Manual test:** Progress shows 100% when all cards completed
6. ✅ **Reward calculation:** BRONZE (>50%), SILVER (>75%), GOLD (>=100%) work correctly
7. ✅ **No regression:** Normal training progress (`currentIndex`) still works
8. ✅ **No regression:** Sub-lesson progress tracking unaffected

---

## Scope Boundaries

**Do NOT touch:**
- `TrainingViewModel.submitAnswer()` — already calls `updateBossProgress()` correctly
- `TrainingViewModel.finishBoss()` — reward calculation is fine
- `ProgressTracker.kt` — mastery tracking is separate
- Boss card generation logic
- Boss unlock conditions

**Focus ONLY on:**
- `SessionRunner.nextCardInternal()` — add `bossProgress` update

---

## Regression Plan

After fix is implemented, run:

1. **Build:** `assembleDebug` — must pass with no errors
2. **Unit tests:** `testDebugUnitTest` — must pass, specifically:
   - `BossBattleClickUiTest` (both tests)
   - `RegularLessonClickUiTest` (ensure normal progress still works)
   - `PauseCascadeClickUiTest` (ensure navigation still works)
3. **Manual verification:**
   - Start boss battle → verify progress at 0%
   - Submit 1 correct answer → verify progress at 10% (1/10)
   - Submit 2nd correct answer → verify progress at 20% (2/10)
   - Complete all cards → verify GOLD reward
4. **Cross-feature regression:**
   - Normal training: `currentIndex` should still update correctly
   - Sub-lesson completion: progress bar should work
   - Daily practice: progress tracking should be unaffected

---

## Git

**One commit** with this structure:

```
fix(boss-battle): Update bossProgress during card advancement

Fixed boss battle progress not updating during battle by adding
bossProgress update to nextCardInternal() when bossActive=true.

Before: Progress bar stayed at 0% throughout entire boss battle
After: Progress updates correctly (0% → 3% → 6% → ... → 100%)

Test Results:
- BossBattleClickUiTest: PASS ✓
- Manual verification: PASS ✓

Root cause: SessionRunner.nextCardInternal() updated currentIndex
but not boss.bossProgress when advancing cards in boss mode.

Fix: Add bossProgress update in nextCardInternal() state update,
using maxOf() to track highest card index reached.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| 2026-05-24 | Bug identified | ✅ | Test created, failed as expected |
| | Fix implemented in nextCardInternal() | | |
| | BossBattleClickUiTest passes | | |
| | Manual verification complete | | |
