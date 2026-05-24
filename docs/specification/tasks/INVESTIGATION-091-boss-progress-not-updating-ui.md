# INVESTIGATION-091: Boss Progress Not Updating in UI

**Date:** 2026-05-24
**Status:** ROOT CAUSE IDENTIFIED
**Severity:** Critical - UI shows frozen progress during boss battles
**Type:** State synchronization bug

---

## Problem Description

During boss battle mode, the progress indicator stays stuck at "1/30" even though cards are changing. The user sees different sentences being presented, but the progress counter remains frozen, creating the impression that the review will never be completed.

**User Report (translated):**
"Why does the progress not update during review mode? We get stuck on card 1/TOTAL_CARDS. The proposals change from the set, but the progress stays in place, so it seems that the review will never be passed?"

---

## Root Cause Analysis

### Phase 1: Evidence Gathering

**1. UI reads progress from `_coreState.boss.bossProgress`**

File: `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt:303`
```kotlin
val current = if (state.boss.bossActive) state.boss.bossProgress else state.cardSession.currentIndex
```

The `state` parameter is `_coreState` from `TrainingViewModel`.

**2. BossOrchestrator updates only its own `_state`, not `_coreState.boss`**

File: `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt:291-297`
```kotlin
_state.update {  // <-- Only updates BossOrchestrator._state
    it.copy(
        bossProgress = nextProgress,  // <-- This change NEVER reaches UI!
        bossReward = nextReward ?: it.bossReward,
        bossRewardMessage = rewardMessage
    )
}
```

**3. Initial sync only happens on boss start**

File: `app/src/main/java/com/alexpo/grammermate/feature/boss/BossOrchestrator.kt:92-120`
```kotlin
stateAccess.updateState { s ->
    s.copy(
        boss = s.boss.copy(  // <-- Only updated when boss battle starts!
            bossActive = true,
            bossType = type
            // <-- bossProgress is NOT copied here!
        )
    )
}
```

**4. Event handler doesn't sync bossProgress**

File: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:1354-1360`
```kotlin
is SessionEvent.AdvanceBossProgress -> {
    val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(event.nextIndex, event.totalCards)
    handleBossCommands(bossCommands)
    // Apply boss pause if reward threshold was crossed
    if (advanceResult.rewardMessageChanged) {
        _coreState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
    }
    // <-- MISSING: No sync of bossProgress to _coreState.boss!
}
```

---

## The Bug: State Synchronization Gap

### Architecture Issue

```
┌─────────────────────────────────────────────────────────────┐
│                    TrainingViewModel                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              _coreState: TrainingUiState            │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │  boss: BossState                            │   │   │
│  │  │    - bossActive: true                       │   │   │
│  │  │    - bossProgress: 0  ← UI READS THIS       │   │   │
│  │  │    - bossTotal: 30                          │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↑                                   │
│                          │                                   │
│                          │ SHOULD SYNC HERE                  │
│                          │                                   │
│                          ↓                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         BossOrchestrator._state: BossState          │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │  bossProgress: 3  ← UPDATED BY ADVANCE     │   │   │
│  │  │  bossTotal: 30                              │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### The Gap

1. **Cards advance:** `SessionRunner.nextCardInternal()` sends `AdvanceBossProgress` event
2. **Orchestrator updates:** `BossOrchestrator.advanceBossProgressOnNextCard()` increments `bossProgress` in `_state` (0 → 1 → 2 → 3)
3. **UI doesn't see it:** `_coreState.boss.bossProgress` stays at 0 forever
4. **User sees:** "1/30" frozen, even though cards are changing

---

## Why TASK-090 Didn't Fix This

TASK-090 (DONE-TASK-090-boss-battle-progress-not-updating.md) claimed to fix this issue, but the fix was incomplete:

- **What TASK-090 did:** Added `bossProgress` update logic to `BossOrchestrator`
- **What TASK-090 missed:** Synchronization from `BossOrchestrator._state` to `TrainingViewModel._coreState.boss`

The manual tests in TASK-090 passed because the test device likely had a different code path or the tester misread the progress indicator. The automated tests failed because they use mock callbacks.

---

## Solution

### Fix: Sync bossProgress in TrainingViewModel event handler

**File:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:1354-1360`

**Current code:**
```kotlin
is SessionEvent.AdvanceBossProgress -> {
    val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(event.nextIndex, event.totalCards)
    handleBossCommands(bossCommands)
    // Apply boss pause if reward threshold was crossed
    if (advanceResult.rewardMessageChanged) {
        _coreState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
    }
}
```

**Fixed code:**
```kotlin
is SessionEvent.AdvanceBossProgress -> {
    val (advanceResult, bossCommands) = bossOrchestrator.advanceBossProgressOnNextCard(event.nextIndex, event.totalCards)
    handleBossCommands(bossCommands)

    // FIX: Sync bossProgress from BossOrchestrator to _coreState.boss
    val bossState = bossOrchestrator.stateFlow.value
    _coreState.update {
        it.copy(boss = bossState)
    }

    // Apply boss pause if reward threshold was crossed
    if (advanceResult.rewardMessageChanged) {
        _coreState.update { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED)) }
    }
}
```

### Why This Fix Works

1. `bossOrchestrator.advanceBossProgressOnNextCard()` updates `BossOrchestrator._state.bossProgress`
2. After the method returns, we read the updated state: `bossOrchestrator.stateFlow.value`
3. We sync the entire `BossState` to `_coreState.boss`, which includes:
   - `bossProgress` (the missing piece!)
   - `bossReward`
   - `bossRewardMessage`
   - All other boss fields
4. UI now sees the updated progress and displays it correctly

---

## Test Plan

### Unit Test Updates

1. **Update BossBattleClickUiTest** to read from `_coreState.boss.bossProgress` instead of mocked variables
2. **Add assertion:** Verify `bossProgress` increments after each card advancement
3. **Add assertion:** Verify UI displays correct progress fraction (1/30, 2/30, etc.)

### Manual Test Steps

1. Start a boss battle
2. Verify progress shows "0% (0/30)"
3. Submit first correct answer → verify progress shows "3% (1/30)"
4. Submit second correct answer → verify progress shows "6% (2/30)"
5. Complete 50% of cards → verify progress shows "50%"
6. Complete all cards → verify progress shows "100%" + GOLD reward

### Regression Tests

- Normal training progress should still work
- Sub-lesson completion should be unaffected
- Daily practice progress tracking should be unaffected

---

## Related Issues

- **TASK-090:** Incomplete fix - updated `BossOrchestrator` state but didn't sync to `_coreState`
- **BossBattleClickUiTest:** Tests fail because they mock callbacks instead of testing real state flow

---

## Files to Modify

1. `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt:1354-1360` - Add boss sync
2. `app/src/test/java/com/alexpo/grammermate/ui/BossBattleClickUiTest.kt` - Update tests to read real state

---

## Impact Assessment

**Severity:** Critical
**User Impact:** High - Users cannot see their progress during boss battles
**Risk:** Low - Simple state sync, no logic changes
**Regression Risk:** Minimal - Only affects boss battle mode

---

## Next Steps

1. ✅ Root cause identified
2. ⏳ Implement fix in TrainingViewModel
3. ⏳ Update BossBattleClickUiTest
4. ⏳ Run automated tests
5. ⏳ Manual device verification
6. ⏳ Create pull request
