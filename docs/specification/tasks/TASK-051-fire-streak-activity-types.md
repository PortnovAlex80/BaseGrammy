# TASK-051: Fire Streak by Activity Types

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/fire-streak-activity-types (from main)
**Spec:** 01-models-and-state.md §glossary, 02-data-stores.md §2.8, 03-algorithms-and-calculators.md §fire-streak, 08-training-viewmodel.md §6, 09-daily-practice.md §fire-streak, 10-verb-drill.md §fire-streak, 11-vocab-drill.md §fire-streak
**UC:** UC-62, UC-63
**Scenario:** scenario-03-mastery-flower.md, scenario-06-daily-practice.md, scenario-07-verb-drill.md, scenario-08-vocab-drill.md
**User Journey:** user-journey-models.md (all journeys)
**Depends on:** TASK-050 (unified session size parameter)
**Priority:** High — core motivation feature

---

## Problem

The current streak system is binary: a day counts if the user completed any sub-lesson with at least 1 correct answer. This has two problems:

1. **Too easy to game:** 1 correct answer out of 10 cards counts as a "practice day," inflating the streak metric.
2. **No diversity incentive:** The system rewards doing the same type of practice repeatedly, not cross-training across lesson, vocab, and verb drills.

The user requested a fire streak system where:
- A session only counts when FULLY completed (all cards answered via VOICE/KEYBOARD, minus bad sentences)
- "Fires" (🔥) are earned per unique practice TYPE per day, not per session
- Maximum fires per day: 3 (Italian) or 4 (English with SUB_DRILL)
- Streak = consecutive days with at least 1 fire

## Changes

### Fix 1: Define PracticeType enum and extend StreakData
**Discrepancy:** N/A (new feature) | **UC:** UC-62 | **Spec:** 01-models-and-state.md §glossary

Add `PracticeType` enum to `data/Models.kt`:
```kotlin
enum class PracticeType {
    TRANSLATION,  // training sub-lesson, boss battle, daily block 1, drill sub-mode (non-English)
    VOCAB,        // vocab drill standalone, daily block 2
    VERB,         // verb drill standalone, daily block 3
    SUB_DRILL     // drill sub-mode (isDrillMode) in English packs only
}
```

Extend `StreakData` in `data/Models.kt`:
```kotlin
data class StreakData(
    val languageId: LanguageId,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletionDateMs: Long? = null,
    val totalSubLessonsCompleted: Int = 0,
    val completedTypesToday: Set<PracticeType> = emptySet(),
    val todayFireCount: Int = 0,
    val lastFireDateMs: Long? = null
)
```

**Files:** `data/Models.kt` — PracticeType enum, StreakData data class

**Verification:** Build succeeds with new enum and extended data class.

### Fix 2: Update StreakStore with fire streak logic
**Discrepancy:** N/A | **UC:** UC-62 | **Spec:** 02-data-stores.md §2.8

Replace `recordSubLessonCompletion()` with `recordPracticeTypeCompletion(languageId, type)`. New logic:

1. Load current StreakData
2. Check if type is already in completedTypesToday → if yes, return unchanged (no duplicate fire)
3. Add type to completedTypesToday
4. Increment todayFireCount
5. Check day boundary:
   - If lastFireDateMs is null or different calendar day → reset completedTypesToday to just this type, todayFireCount = 1
   - If lastFireDateMs is yesterday → increment currentStreak
   - If lastFireDateMs is >1 day ago → reset currentStreak to 1
   - If lastFireDateMs is today → streak unchanged
6. Update lastFireDateMs to now
7. Save and return

Also update YAML serialization/deserialization to handle new fields. Handle migration: if old YAML file without new fields → defaults (emptySet, 0, null).

**Files:** `data/StreakStore.kt` — StreakStoreImpl, serialization, new method

**Verification:** Save StreakData with fire fields → reload → fields preserved.

### Fix 3: Implement "засчитанная УЕ" completion check
**Discrepancy:** N/A | **UC:** UC-62 AC6 | **Spec:** 08-training-viewmodel.md §6

Replace the `correctCount > 0` guard in `updateStreak()` with a proper completion check:

```kotlin
fun isSessionCompleted(cardSession: CardSessionState): Boolean {
    val badCount = // count bad sentences in current session
    val totalRequired = sessionSize - badCount
    return cardSession.correctCount >= totalRequired
}
```

This ensures ALL non-bad cards were answered correctly, not just 1.

Apply this check in:
- `TrainingViewModel.updateStreak()` — for training sub-lessons
- `VerbDrillViewModel` — for verb drill sessions
- `VocabDrillViewModel` — for vocab drill sessions (adapt: at least 1 GOOD/EASY rating)
- `DailyPracticeCoordinator` — for each daily practice block

**Files:**
- `ui/TrainingViewModel.kt` — `updateStreak()` method
- `ui/VerbDrillViewModel.kt` — session completion callback
- `ui/VocabDrillViewModel.kt` — session completion callback
- `feature/daily/DailyPracticeCoordinator.kt` — `onBlockComplete()` for each block

**Verification:** Complete session with only 1 correct answer → no fire earned. Complete full session → fire earned.

### Fix 4: Map session modes to PracticeType
**Discrepancy:** N/A | **UC:** UC-62 AC1-AC5 | **Spec:** 08-training-viewmodel.md §6

Add mapping logic:
```kotlin
fun getPracticeType(sessionMode: SessionMode): PracticeType = when (sessionMode) {
    SessionMode.LESSON, SessionMode.BOSS -> PracticeType.TRANSLATION
    SessionMode.DRILL -> if (isEnglishPack && isDrillMode) PracticeType.SUB_DRILL else PracticeType.TRANSLATION
    // Verb drill standalone → PracticeType.VERB
    // Vocab drill standalone → PracticeType.VOCAB
    // Daily block 1 → PracticeType.TRANSLATION
    // Daily block 2 → PracticeType.VOCAB
    // Daily block 3 → PracticeType.VERB
}
```

Thread this through the completion callbacks for each mode.

**Files:**
- `ui/TrainingViewModel.kt` — map training modes
- `ui/VerbDrillViewModel.kt` — return VERB
- `ui/VocabDrillViewModel.kt` — return VOCAB
- `feature/daily/DailyPracticeCoordinator.kt` — map per block type

**Verification:** Boss battle → TRANSLATION. Verb drill → VERB. Daily block 2 → VOCAB.

### Fix 5: Update StreakManager celebration messages
**Discrepancy:** N/A | **UC:** UC-62 | **Spec:** 23-screen-elements.md DG-03

Update `StreakManager.getCelebrationMessage()` to include fire count:
```kotlin
fun getCelebrationMessage(streakCount: Int, fireCount: Int): String? {
    val fireEmoji = "🔥".repeat(fireCount.coerceIn(1, 4))
    return when {
        fireCount >= 3 -> "$fireEmoji Perfect day! All practice types completed! $streakCount day streak!"
        fireCount == 2 -> "$fireEmoji Great variety! $streakCount day streak!"
        streakCount == 1 -> "$fireEmoji Great start! Day 1!"
        streakCount == 7 -> "$fireEmoji One week streak! Amazing!"
        streakCount == 30 -> "$fireEmoji One month streak! Outstanding!"
        streakCount % 10 == 0 -> "$fireEmoji $streakCount day streak! Keep it up!"
        else -> "$fireEmoji $streakCount day streak!"
    }
}
```

**Files:** `feature/progress/StreakManager.kt`

**Verification:** 3 fires on day 7 → "🔥🔥🔥 Perfect day! All practice types completed! 7 day streak!"

### Fix 6: Add HomeScreen fire streak indicator
**Discrepancy:** N/A | **UC:** UC-63 | **Spec:** 23-screen-elements.md HS-23

Add fire streak indicator to HomeScreen top area. Shows:
- 🔥 icons (todayFireCount, max 4) + streak count number
- 0 fires → hidden or dimmed
- Streak > 7 → gold highlight

**Files:**
- `ui/screens/HomeScreen.kt` — add fire indicator composable
- Read `streakData` from TrainingViewModel state

**Verification:** Complete a lesson → HomeScreen shows 1 fire 🔥. Complete vocab drill → shows 2 fires 🔥🔥.

---

## Verification Checklist
1. Training sub-lesson completed fully → 1 fire (TRANSLATION type recorded)
2. Training sub-lesson with only 1 correct answer → 0 fires (not "засчитанная УЕ")
3. Verb drill completed → 1 fire (VERB type)
4. Vocab drill completed → 1 fire (VOCAB type)
5. Daily practice all 3 blocks completed → 3 fires (TRANSLATION + VOCAB + VERB)
6. Daily practice then standalone lesson → still 3 fires (TRANSLATION not double-counted)
7. Same type twice in one day → no additional fire
8. Consecutive day with fire → streak increments
9. Day with 0 fires → streak resets to 0
10. English pack drill sub-mode → SUB_DRILL type recorded (4th fire possible)
11. Italian pack → max 3 fires per day
12. HomeScreen fire indicator shows correct count after each session
13. StreakDialog shows fire count + streak count
14. Migration: existing streak YAML (old format) loads with defaults for new fields
15. `assembleDebug` passes
16. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- Bad sentences mechanism (already exists in all modes)
- Navigation/session state management
- Mastery/flower calculation
- Session size parameter (TASK-050)
- SRS interval ladder
- Data persistence format (keep YAML)
- Backup/restore (existing backup includes streak YAML, just new fields)
- GrammarMateApp.kt screen routing

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after all fixes
3. **Per-task verification:** each item from Verification Checklist
4. **Cross-task regression:** training flow, daily practice 3 blocks, verb drill batch, vocab drill batch, streak celebration dialog, HomeScreen display, backup/restore
5. **UC/AC spot-check:** UC-62 AC1-AC10, UC-63 AC1-AC7
6. **Spec sync:** verify 01-models, 02-data-stores, 03-algorithms match implementation
7. **Migration test:** load old streak YAML → verify defaults applied, no crash

## Git
One commit per fix. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Define PracticeType + extend StreakData | | |
| | Fix 2: Update StreakStore with fire streak logic | | |
| | Fix 3: Implement "засчитанная УЕ" completion check | | |
| | Fix 4: Map session modes to PracticeType | | |
| | Fix 5: Update StreakManager celebration messages | | |
| | Fix 6: Add HomeScreen fire streak indicator | | |
