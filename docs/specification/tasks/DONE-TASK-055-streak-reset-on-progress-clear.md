# TASK-055: Streak Reset on Progress Clear

**Status:** DONE
**Created:** 2026-05-16
**Branch:** batch/008-streak-reset
**Spec:** 02-data-stores.md §2.8, 08-training-viewmodel.md
**UC:** UC-71 (fire streak)
**Priority:** HIGH — data integrity

---

## Problem

When a user resets progress (Settings → Reset All Progress or Reset Language Progress), the streak data (`streak_{languageId}.yaml`) is NOT cleared. The user starts with zero mastery, zero lessons completed, but retains their streak count and fire history. This is inconsistent — a fresh start should include streak reset.

## Root Cause

`SettingsActionHandler.resetAllProgress()` returns a list of `SettingsResult` actions:
- `ResetStores` → clears ProgressStore + MasteryStore
- `ResetDrillFiles` → deletes verb_drill_progress.yaml + word_mastery.yaml
- `ClearWordMastery` → clears word mastery
- `ResetDailyState` → resets daily cursor

None of these touch `StreakStore` or the `streak_{languageId}.yaml` files.

## Changes

### Fix 1: Add streak reset to resetAllProgress
**Discrepancy:** N/A (bug fix) | **UC:** UC-71 | **Spec:** 02-data-stores.md §2.8

In `SettingsActionHandler.resetAllProgress()`, add a new `SettingsResult` that resets streak data.

Add `SettingsResult.ResetStreak` to `shared/SettingsResult.kt`.

In `SettingsActionHandler.resetAllProgress()`, add `SettingsResult.ResetStreak` to the returned list.

In `TrainingViewModel.handleSettingsResults()`, add handler:
```kotlin
is SettingsResult.ResetStreak -> resetStreak()
```

Add `resetStreak()` method to TrainingViewModel:
```kotlin
private fun resetStreak() {
    streakStore.resetAll()
    _coreState.update {
        it.copy(cardSession = it.cardSession.copy(
            currentStreak = 0,
            longestStreak = 0,
            streakMessage = null,
            streakCelebrationToken = 0,
            todayFireCount = 0
        ))
    }
}
```

Add `resetAll()` to StreakStore interface and implementation — delete all `streak_*.yaml` files.

**Files:** `shared/SettingsActionHandler.kt`, `shared/SettingsResult.kt`, `ui/TrainingViewModel.kt`, `data/StreakStore.kt`

**Verification:** Reset All Progress → all streak files deleted, UI shows 0 streak.

### Fix 2: Add streak reset to resetLanguageProgress
**Discrepancy:** N/A | **UC:** UC-71 | **Spec:** 02-data-stores.md §2.8

Same pattern but scoped to a single language. Add `SettingsResult.ResetStreakForLanguage(languageId)`.

In `StreakStore`, add `resetForLanguage(languageId: String)` — deletes `streak_{languageId}.yaml`.

**Files:** `shared/SettingsActionHandler.kt`, `shared/SettingsResult.kt`, `ui/TrainingViewModel.kt`, `data/StreakStore.kt`

**Verification:** Reset Language Progress (Italian) → streak_it.yaml deleted, streak_en.yaml preserved.

---

## Verification Checklist
1. Reset All Progress → all streak_*.yaml files deleted, HomeScreen shows 0 fires, 0 streak
2. Reset Language Progress (Italian) → streak_it.yaml deleted, streak_en.yaml preserved
3. After reset, complete a session → new streak starts at 1
4. After reset, daily practice → fires start fresh
5. Backup taken before reset includes streak files (existing behavior preserved)
6. `assembleDebug` passes

## Scope Boundaries
**Do NOT touch:**
- Streak recording logic (recordPracticeTypeCompletion)
- Streak celebration messages
- HomeScreen fire indicator
- Any other progress reset logic beyond adding streak reset

## Regression Plan
1. Build: `assembleDebug`
2. Manual: Reset All Progress → verify streak files gone
3. Manual: Reset Language Progress → verify only that language's streak gone
4. Manual: Complete session after reset → streak starts at 1

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-16 | Fix 1: Reset streak on resetAllProgress | DONE | StreakStore.resetAll() + UI state zeroed |
| 2026-05-16 | Fix 2: Reset streak on resetLanguageProgress | DONE | StreakStore.resetForLanguage() + scoped UI state zeroed |
